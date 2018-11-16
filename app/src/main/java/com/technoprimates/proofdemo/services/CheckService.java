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
import com.technoprimates.proofdemo.util.Constants;
import com.technoprimates.proofdemo.util.ProofError;
import com.technoprimates.proofdemo.util.ProofException;
import com.technoprimates.proofdemo.util.ProofFile;
import com.technoprimates.proofdemo.util.ProofOperations;

import org.json.JSONObject;

import java.io.IOException;

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
        JSONObject j;
        Uri fullUri;
        String tree, tiers, storedDocumentHash="", root="", chain, txid, url;

        // Get the receiver, this will be used to send progress info to the UI
        mResultReceiver = intent.getParcelableExtra(Constants.EXTRA_RECEIVER);

        // Get the param identifying the file to handle
        fullUri = Uri.parse(intent.getStringExtra(Constants.EXTRA_PROOFFULLURI));
        if (fullUri == null) {
            Bundle b = new Bundle();
            b.putString("error", ProofError.ERROR_NO_URI);
            mResultReceiver.send(Constants.RETURN_PROOFREAD_KO, b);
            return;
        }
        Log.d(Constants.TAG, "              full proof uri : " + fullUri.toString());

        // Step 1 : Load the proof. Read and decode json data of the proof, which is stored in the proof file
        String txtProof = null;
        try {
            txtProof = ProofOperations.readProofFromFullUri(this, fullUri);
        } catch (ProofException e) {
            Bundle b = new Bundle();
            b.putString("error", e.getProofError());
            mResultReceiver.send(Constants.RETURN_PROOFREAD_KO, b);
            return;
        }
        if (txtProof == null){ // Something went wrong
            Bundle b = new Bundle();
            b.putString("error", ProofError.ERROR_READING_PROOF_TEXT);
            mResultReceiver.send(Constants.RETURN_PROOFREAD_KO, b);
            return;
        }

        Log.d(Constants.TAG, txtProof);
        final Bundle bundle;
        try {
            bundle = ProofOperations.decodeProof(txtProof);
        } catch (ProofException e) {
            Bundle b = new Bundle();
            b.putString("error", e.getProofError());
            mResultReceiver.send(Constants.RETURN_PROOFREAD_KO, b);
            return;
        }

        // Send the results to the calling activity using the receiver
        mResultReceiver.send(Constants.RETURN_PROOFREAD_OK, bundle);
        // keep data needed for further checks
        chain = bundle.getString("chain", null);
        txid = bundle.getString("txid", null);
        storedDocumentHash = bundle.getString("hashdoc", null);
        tiers = bundle.getString("tiers", null);
        tree = bundle.getString("tree", null);
        root = bundle.getString("root", null);

        // Step 2 : Check the document hash.
        // Extract the original file from the proof file, and compute its hash

        String computedDocumentHash = null;
        try {
            computedDocumentHash = ProofFile.set(this, fullUri).computeDocumentHash();
        } catch (ProofException e) {
            Bundle b = new Bundle();
            b.putString("error", e.getProofError());
            mResultReceiver.send(Constants.RETURN_HASHCHECK_KO, b);
            return;
        }

        // Compare to the hash stored in the proof and send the result to the calling activity
        if (!storedDocumentHash.equals(computedDocumentHash)) {
            Bundle b = new Bundle();
            b.putString("error", ProofError.ERROR_HASH_DOES_NOT_MATCH);
            mResultReceiver.send(Constants.RETURN_HASHCHECK_KO, b);
            return;
        } else {
            mResultReceiver.send(Constants.RETURN_HASHCHECK_OK, null);
        }

        // Step 3 : Check the Merkle tree.
        try {
            if (ProofOperations.checkTree(tree)){
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
        switch (chain) {
            case "btc-testnet":
                url = Constants.URL_BASE_BTC_TESTNET + txid;
                break;
            case "btc-mainnet":
                url = Constants.URL_BASE_BTC_MAINNET + txid;
                break;
            case "ltc-testnet":
                url = Constants.URL_BASE_BTC_LITECOIN + txid;
                break;
            default:
                Bundle b = new Bundle();
                b.putString("error", ProofError.ERROR_UNKNOWN_BLOCKCHAIN);
                mResultReceiver.send(Constants.RETURN_TXLOAD_KO, b);
                return;
        }

        // Copy the root stored in the proof in a final String, to be used in Volley callback for comparaison
        final String storedRoot = root;

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
                            bundle = ProofOperations.decodeBlockExplorerResponse(response);
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
