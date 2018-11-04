package com.technoprimates.proofdemo.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.technoprimates.proofdemo.util.Constants;
import com.technoprimates.proofdemo.util.ProofUtils;

import org.json.JSONObject;

/* This Service displays a proof and checks it
1 : Load the proof from the proof file
2 : Check the document hash.
3 : Check the Merkle tree.
4 : Load blockchain data with a web request sent to a blockchain explorer
5 : compare the data embedded in the blockchain with the root of the merkel tree stored in the proof
*/
public class DisplayAndCheckService extends JobIntentService {

    // Volley Request queue
    private RequestQueue mRequestQueue;

    // Receiver used to send back results to the calling activity
    private ResultReceiver mResultReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(Constants.TAG, "--- DisplayAndCheckService          --- onCreate");

        // initialize Volley request queue
        mRequestQueue = Volley.newRequestQueue(this);
    }

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, DisplayAndCheckService.class, Constants.JOB_SERVICE_DISPLAY, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.d(Constants.TAG, "--- DisplayAndCheckService          --- onHandleIntent");
        JSONObject j;
        String proofFilename;
        String tree, tiers, storedDocumentHash="", root="", chain, txid, url;

        // Get the receiver, this will be used to send progress info to the UI
        mResultReceiver = intent.getParcelableExtra(Constants.EXTRA_RECEIVER);

        // Get the param identifying the file to handle
        proofFilename = intent.getStringExtra(Constants.EXTRA_PROOFFILENAME);
        if (proofFilename == null) {
            Log.e(Constants.TAG, "********* Filename is null");
            return;
        }
        Log.d(Constants.TAG, "              proofFilename: " + proofFilename);

        // Step 1 : Load the proof. Read and decode json data of the proof, which is stored in the proof file
        String txtProof = ProofUtils.readProofFromProofFilename(proofFilename);
        if (txtProof == null){ // Something went wrong
            mResultReceiver.send(Constants.RETURN_PROOFREAD_KO, null);
            return;
        }

        Log.d(Constants.TAG, txtProof);
        final Bundle bundle = ProofUtils.decodeProof(txtProof);
        if (bundle == null){// Something went wrong
            mResultReceiver.send(Constants.RETURN_PROOFREAD_KO, null);
            return;
        } else {
            // Send the results to the calling activity using the receiver
            mResultReceiver.send(Constants.RETURN_PROOFREAD_OK, bundle);
            // keep data needed for further checks
            chain = bundle.getString("chain", null);
            txid = bundle.getString("txid", null);
            storedDocumentHash = bundle.getString("hashdoc", null);
            tiers = bundle.getString("tiers", null);
            tree = bundle.getString("tree", null);
            root = bundle.getString("root", null);
        }

        // Step 2 : Check the document hash.
        // Extract the original file from the proof file, and compute its hash
        String computedDocumentHash = ProofUtils.computeDocumentHashFromProofFilename(this, proofFilename);

        // Compare to the hash stored in the proof and send the result to the calling activity
        Log.d(Constants.TAG, "Proof  : Document hash : "+storedDocumentHash);
        Log.d(Constants.TAG, "Compute: Document hash : "+computedDocumentHash);
        if (!storedDocumentHash.equals(computedDocumentHash)) {
            mResultReceiver.send(Constants.RETURN_HASHCHECK_KO, null);
            return;
        } else {
            mResultReceiver.send(Constants.RETURN_HASHCHECK_OK, null);
        }


        // Step 3 : Check the Merkle tree.
        if (ProofUtils.checkTree(tree)){
            mResultReceiver.send(Constants.RETURN_TREECHECK_OK, null);
        } else {
            mResultReceiver.send(Constants.RETURN_TREECHECK_KO, null);
            Log.e(Constants.TAG, "ERROR Stored Merkle tree is flawed");
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
                Log.e(Constants.TAG, "Unsupported blockchain");
                mResultReceiver.send(Constants.RETURN_TXLOAD_KO, null);
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
                        Log.d(Constants.TAG, "   --- Download : Callback on server response");
                        Log.d(Constants.TAG, "   ---             JSON response : " + response.toString());

                        // Send the results to the calling activity using the receiver
                        Bundle bundle = ProofUtils.decodeBlockExplorerResponse(response);
                        mResultReceiver.send(Constants.RETURN_TXLOAD_OK, bundle);

                        // Step 5 : compare the data embedded in the blockchain with the root of the merkel tree stored in the proof
                        if (storedRoot.equals(bundle.getString("opreturn_data"))) {
                            // Data stored in the blockchain transaction matches the root of proof's merkle tree
                            mResultReceiver.send(Constants.RETURN_TXCHECK_OK, null);
                        } else {
                            // does not match
                            mResultReceiver.send(Constants.RETURN_TXCHECK_KO, null);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        mResultReceiver.send(Constants.RETURN_TXLOAD_KO, null);
                        Log.e(Constants.TAG, "Download : Error Volley or empty return : " + error.getMessage());
                        error.printStackTrace();
                    }
                });
        mRequestQueue.add(mRequestDownload);
    }
}
