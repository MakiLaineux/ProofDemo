package com.technoprimates.proofdemo.services;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.technoprimates.proofdemo.db.DatabaseHandler;
import static com.technoprimates.proofdemo.util.Constants.*;
import com.technoprimates.proofdemo.util.ProofException;
import com.technoprimates.proofdemo.util.ProofUtils;
import com.technoprimates.proofdemo.struct.StampFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

/* This JobIntentService performs the long-running operations needed to submit a proof request to the server
Different tasks may be required, so the service must be launched with a paramater specifying the tasks to perform

- value "TASK_PREPARE", those tasks are :
1. Create a "proof request" record in the local database, take note of the resulting request id
2. Copies the file selected by the user into app data space (name in app data space = request id)
3. Performs a hash of the original file(SHA-256 algorithm)
4. Stores the resulting hash in the proof request (local db)
5. Post an "upload" task

- value "TASK_UPLOAD", the tasks are :
1. Upload a request already prepared onto the server
*/
public class SubmitService extends JobIntentService {

    private static final int BUFFER = 2048;

    // ResultReceiver to send back results to the calling activity
    private ResultReceiver mResultReceiver;

    private int mWorkType; // PREPARE or UPLOAD

    private DatabaseHandler mDatabase;    // Local db

    // Volley requests queue :
    private RequestQueue mRequestQueue;

    @Override
    public void onCreate() {
        Log.d(TAG, "--- SubmitService        --- onCreate");
        super.onCreate();
        mRequestQueue = Volley.newRequestQueue(this);

    }

    public static void enqueueWork(Context context, int workType, Intent work) {
        work.putExtra(EXTRA_WORK_TYPE, workType);  // add the work type to the intent
        enqueueWork(context, SubmitService.class, JOB_SERVICE_SUBMIT, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {

        Log.d(TAG, "--- Submit service        --- onHandleWork");

        // get Receiver to send progress to the calling activity
        mResultReceiver = intent.getParcelableExtra(EXTRA_RECEIVER);

        // get the type of work to execute
        mWorkType = intent.getIntExtra(EXTRA_WORK_TYPE, TASK_NOTASK);

        //Get database instance
        mDatabase = DatabaseHandler.getInstance(this);

        // Launch work depending on the incoming intent extras
        switch (mWorkType){
            case TASK_PREPARE:
                prepareRequest(intent);
                break;
            case TASK_UPLOAD:
                uploadRequest(intent);
                break;
            default:
                break;
        }
    }


    // Prepare a request
    private void prepareRequest(Intent intent){

        // Create an StampFile object from the uri
        final String fullSourceUri = intent.getStringExtra(EXTRA_FILENAME);
        if (fullSourceUri == null){
            sendBackInfo(RETURN_PREPARE_KO, null);
            return;
        }
        StampFile stampFile = null;
        try {
            stampFile = StampFile.set(this, Uri.parse(fullSourceUri));
        } catch (ProofException e) {
            sendBackInfo(RETURN_PREPARE_KO, null);
            return;
        }

        // get the current proof author's message
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String proofMessage = sharedPreferences.getString("ProofAuthorMessage", "");

        // Step 1 : Create a proof request with status INITIALIZED
        // DB insertion
        final int dbId = (int) mDatabase.insertProofRequest(stampFile.getFileName(), proofMessage, stampFile.typeOf());

        // If insertion failed, inform the calling activity and stop this job
        if (dbId==-1){
            sendBackInfo(RETURN_PREPARE_KO, null);
            return;
        }
        try {
            // Step 2 : build a ready-to-hash temp file (draft) in the app data space
            // The internal name of the temp file is the local database request id
            stampFile.writeDraft(String.format("%04d", dbId), true);

            // Step 3 : Compute the hash, working in App space
            String docHash = stampFile.getHash();

            // re-hash with proof message and store both hashs in local db
            String overHash = ProofUtils.overHash(docHash, proofMessage);
            final int updateDb = mDatabase.updateHashProofRequest(dbId, docHash, overHash);
            if (updateDb != RETURN_DBUPDATE_OK) {
                sendBackInfo(RETURN_PREPARE_KO, null);
                return;
            }

        } catch (ProofException e) {
            sendBackInfo(RETURN_PREPARE_KO, null);
            return;
        }

        // Done, post an upload request
        Intent i = new Intent();
        // post the receiver in extra
        i.putExtra(EXTRA_RECEIVER, mResultReceiver);
        // post the db id in extra
        i.putExtra(EXTRA_REQUEST_ID, dbId);
        // post the type of work : upload
        i.putExtra(EXTRA_WORK_TYPE, TASK_UPLOAD);
        //enqueue the job
        enqueueWork(this, SubmitService.class, JOB_SERVICE_SUBMIT, i);
    }

    // Upload an already prepared request onto the server
    private void uploadRequest(Intent intent){
        int nb = 0, i=0, param=0, idBdd =0;
        Cursor c;
        String url, overHash, instanceId;

        // get the instance id, the server uses this data to identify the client
        // if the instance id does not already exist, create it, based on the installation id of the app
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        instanceId = sharedPref.getString("ID", "");
        if (instanceId.equals("")) {
            Log.d(TAG, "New UserId");
            instanceId = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("ID", instanceId);
            editor.apply();
        }

        // Get the param identifying the record to handle
        // This is en extra containing the bdd id to handle or the value IDBDD_ALL
        param = intent.getIntExtra(EXTRA_REQUEST_ID, IDBDD_ALL);

        if (param == IDBDD_ALL) { // All matching records to handle
            c = mDatabase.getAllProofRequests(STATUS_HASH_OK);
            if (c != null) {
                nb = c.getCount();
            }
        } else { // one record only
            c = mDatabase.getOneCursorProofRequest(param);
            nb = 1;
        }
        if (c==null || nb==0) {
            sendBackInfo(RETURN_UPLOAD_KO, null);
            return;
        }

        // Send to the server, one by one, and update status
        for (i=0; i<nb; i++) {
            // get data to upload
            c.moveToPosition(i);
            idBdd = c.getInt(REQUEST_NUM_COL_ID);
            overHash = c.getString(REQUEST_NUM_COL_OVER_HASH);
            // build the URL to launch
            url = String.format(Locale.US, URL_UPLOAD_DEMANDE, instanceId, idBdd, overHash);

            // Create Volley request with callbacks :
            JsonArrayRequest mRequeteUpload = new JsonArrayRequest(url, new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    processUploadResponse(response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error Volley on upload: " + error.getMessage());
                    sendBackInfo(RETURN_UPLOAD_KO, null);
                }
            });
            mRequestQueue.add(mRequeteUpload);
        }
        c.close();
    }

    private void processUploadResponse(JSONArray response){
        JSONObject json_data;
        int dbId;

        try {
            // Server response is a JSONArray with just one element
            json_data = response.getJSONObject(0);
            dbId = Integer.valueOf(json_data.getString("request"));

            // Update request's status in local database
            int dbResult = mDatabase.updateStatutProofRequest(dbId, STATUS_SUBMITTED);

            // Notify calling activity
            if (dbResult == RETURN_DBUPDATE_OK) {
                sendBackInfo(RETURN_UPLOAD_OK, null);
            }
            else {
                sendBackInfo(RETURN_UPLOAD_KO, null);
            }

        } catch (Exception e) {
            Log.e(TAG, "Erreur indéterminée (03) Volley On Response :" + e.toString());
            sendBackInfo(RETURN_UPLOAD_KO, null);
        }
    }

    //    Send feedback to the caller
    private void sendBackInfo(int resultCode, Bundle resultData) {
        mResultReceiver.send(resultCode, resultData);
    }
}
