package com.technoprimates.proofdemo.services;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.technoprimates.proofdemo.struct.Statement;
import com.technoprimates.proofdemo.util.Constants;
import com.technoprimates.proofdemo.util.ProofError;
import com.technoprimates.proofdemo.util.ProofException;
import com.technoprimates.proofdemo.util.ProofUtils;
import com.technoprimates.proofdemo.struct.StampFile;

import org.json.JSONObject;

/* This Service displays a proof and checks it
1 : Load the proof from the proof file
2 : Check the document hash.
3 : Check the Merkle tree.
4 : Load blockchain data with a web request sent to a blockchain explorer
5 : compare the data embedded in the blockchain with the root of the merkel tree stored in the proof
*/
public class CheckService extends IntentService {

    // Volley Request queue
    private RequestQueue mRequestQueue;

    // Receiver used to send back results to the calling activity
    private ResultReceiver mResultReceiver;

    public CheckService() {
        super("CheckService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(Constants.TAG, "--- CheckService          --- onCreate");

        // initialize Volley request queue
        mRequestQueue = Volley.newRequestQueue(this);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.d(Constants.TAG, "--- CheckService          --- onHandleIntent");
        Statement statement = null;
        String url;

        // Get the receiver, this will be used to send progress info to the UI
        mResultReceiver = intent.getParcelableExtra(Constants.EXTRA_RECEIVER);

        // Create an StampFile object from the uri
        final String fullSourceUri = intent.getStringExtra(Constants.EXTRA_PROOFFULLURI);
        if (fullSourceUri == null) {
            Bundle b = new Bundle();
            b.putString("error", ProofError.ERROR_NO_URI);
            mResultReceiver.send(Constants.RETURN_PROOFREAD_KO, b);
            return;
        }
        StampFile stampFile ;
        try {
            stampFile = StampFile.set(this, Uri.parse(fullSourceUri));
        } catch (ProofException e) {
            Bundle b = new Bundle();
            b.putString("error", ProofError.ERROR_NO_URI);
            mResultReceiver.send(Constants.RETURN_PROOFREAD_KO, b);
            return;
        }

        // Step 1 : Load the proof. Read and decode json data of the proof, which is stored in the proof file
        try {
            statement = new Statement(stampFile.getStatementString());
        } catch (ProofException e) {
            Bundle b = new Bundle();
            b.putString("error", e.getProofError());
            mResultReceiver.send(Constants.RETURN_PROOFREAD_KO, b);
            return;
        }

        // Send the results to the calling activity using the receiver
        Log.d(Constants.TAG, statement.getString());
        Bundle bundle = new Bundle();
        bundle.putString("chain", statement.getChain());
        bundle.putString("txid", statement.getTxid());
        bundle.putString("txinfo", statement.getTxinfo());
        bundle.putString("root", statement.getRoot());
        bundle.putString("tiers", statement.getTiers());
        bundle.putString("dochash", statement.getDocHash());
        bundle.putString("message", statement.getMessage());
        bundle.putString("overhash", statement.getOverHash());
        mResultReceiver.send(Constants.RETURN_PROOFREAD_OK, bundle);

        // Step 2 : Check the hash.
        // Extract the original file from the proof file, compute its hash and
        // mix it with the proof author's message
        String docHash, mixedHash;
        try {
            stampFile.writeDraft("tmpfile"); // create a ready-to-hash temp file
            docHash = stampFile.getHash();
            stampFile.eraseDraft(); // deletes the temp file
            mixedHash = ProofUtils.overHash(docHash, statement.getMessage());

        } catch (ProofException e) {
            Bundle b = new Bundle();
            b.putString("error", e.getProofError());
            mResultReceiver.send(Constants.RETURN_HASHCHECK_KO, b);
            return;
        }

        // Compare both hashs to those stored in the statement
        if ((statement.getDocHash().equals(docHash)) &&
                (statement.getOverHash().equals(mixedHash))) {
            mResultReceiver.send(Constants.RETURN_HASHCHECK_OK, null);
        } else {
            Bundle b = new Bundle();
            b.putString("error", ProofError.ERROR_HASH_DOES_NOT_MATCH);
            mResultReceiver.send(Constants.RETURN_HASHCHECK_KO, b);
            return;
        }

        // Step 3 : Check the Merkle tree.
        try {
            if (statement.checkTree()){
                mResultReceiver.send(Constants.RETURN_TREECHECK_OK, null);
            } else {
                Bundle b = new Bundle();
                b.putString("error", ProofError.ERROR_INVALID_MERKLE_TREE);
                mResultReceiver.send(Constants.RETURN_TREECHECK_KO, b);
                return;
            }
        } catch (ProofException e) {
            Bundle b = new Bundle();
            b.putString("error", e.getProofError());
            mResultReceiver.send(Constants.RETURN_TREECHECK_KO, b);
            return;
        }

        // Step 4 : Load blockchain data with a web request sent to a blockchain explorer
        // Build the api url depending on the chain name
        switch (statement.getChain()) {
            case "btc-testnet":
                url = Constants.URL_BASE_BTC_TESTNET + statement.getTxid();
                break;
            case "btc-mainnet":
                url = Constants.URL_BASE_BTC_MAINNET + statement.getTxid();
                break;
            case "ltc-testnet":
                url = Constants.URL_BASE_BTC_LITECOIN + statement.getTxid();
                break;
            default:
                Bundle b = new Bundle();
                b.putString("error", ProofError.ERROR_UNKNOWN_BLOCKCHAIN);
                mResultReceiver.send(Constants.RETURN_TXLOAD_KO, b);
                return;
        }

        // Copy the root stored in the proof in a final String, to be used in Volley callback for comparaison
        final String storedRoot = statement.getRoot();

        // Create Volley request and its callbacks :
        mRequestQueue = Volley.newRequestQueue(this);

        JsonObjectRequest mRequestDownload = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(Constants.TAG, "   --- Download :  JSON response : " + response.toString());

                        // Send the results to the calling activity using the receiver
                        Bundle bundle = null;
                        try {
                            bundle = ProofUtils.decodeBlockExplorerResponse(response);
                        } catch (ProofException e) {
                            Bundle b = new Bundle();
                            b.putString("error", e.getProofError());
                            mResultReceiver.send(Constants.RETURN_TXLOAD_KO, b);
                            return;
                        }
                        mResultReceiver.send(Constants.RETURN_TXLOAD_OK, bundle);

                        // Step 5 : compare the data embedded in the blockchain with the root of the merkel tree stored in the proof
                        if (storedRoot.equals(bundle.getString("opreturn_data"))) {
                            // Data stored in the blockchain transaction matches the root of proof's merkle tree
                            mResultReceiver.send(Constants.RETURN_TXCHECK_OK, null);
                        } else {
                            // does not match
                            Bundle b = new Bundle();
                            b.putString("error", ProofError.ERROR_BLOCKCHAIN_DOES_NOT_MATCH);
                            mResultReceiver.send(Constants.RETURN_TXCHECK_KO, b);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Bundle b = new Bundle();
                        b.putString("error", ProofError.ERROR_VOLLEY_BLOCKEXPLORER);
                        mResultReceiver.send(Constants.RETURN_TXCHECK_KO, b);
                    }
                });
        mRequestQueue.add(mRequestDownload);
    }
}
