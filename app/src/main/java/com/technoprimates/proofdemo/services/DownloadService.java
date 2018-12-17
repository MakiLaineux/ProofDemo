package com.technoprimates.proofdemo.services;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ParseException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.technoprimates.proofdemo.db.DatabaseHandler;
import com.technoprimates.proofdemo.struct.Statement;
import static com.technoprimates.proofdemo.util.Constants.*;
import com.technoprimates.proofdemo.util.ProofException;
import com.technoprimates.proofdemo.struct.StampFile;
import com.technoprimates.proofdemo.util.ProofUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

/* This Service tries to download the proofs that were prepared on the distant server
1. Build the url to invoke
2. Build a Volley request with this url, send it to the server and receive some proof records
For each of the proof records received:
3. Build the "proof file" on SD Card (zip file or file accepting metadata)
4. Store the proof elements and update the request's status in the local database
5. Send an acknowledgment to the server
*/

public class DownloadService extends JobIntentService {

    // Local Database
    private DatabaseHandler mDatabase;
    // Volley request queue
    private RequestQueue mRequestQueue;
    // ReultReceiver to retuen back info to the caling activity
    private ResultReceiver mResultReceiver;
    // InstanceId to identify the device when communicating with the server
    private String mInstanceId;

    @Override
    public void onCreate() {
        Log.d(TAG, "--- DownloadService        --- onCreate");
        super.onCreate();

        mRequestQueue = Volley.newRequestQueue(this);

        // get singleton instance of database
        mDatabase = DatabaseHandler.getInstance(this);

        // get instance id, the server uses this data to identify the client
        // if the instance id does not already exist, create it, based on the installation id of the app
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mInstanceId = sharedPref.getString("ID", "");
        if (mInstanceId.equals("")) {
            Log.d(TAG, "New UserId");
            mInstanceId = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("ID", mInstanceId);
            editor.apply();
        }

        Log.d(TAG, "service Download OnCreate");
    }

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, DownloadService.class, JOB_SERVICE_DOWNLOAD, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.d(TAG, "--- DownloadService        --- onHandleIntent");
        String sUrl;

        // Check output directory, create it if necessary
        if (!ProofUtils.checkSDDirectory()) return;

        // get the receiver
        mResultReceiver = intent.getParcelableExtra(EXTRA_RECEIVER);

        // Step 1 : build complete URL with installation id
        sUrl = String.format(Locale.US, URL_DOWNLOAD_PROOF, mInstanceId);
        Log.d(TAG, "                 URL String : " + sUrl);

        // Step 2 : Create Volley request and its callbacks :
        JsonArrayRequest mRequestDownload = new JsonArrayRequest(sUrl, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                Log.d(TAG, "   --- Download : Callback on server response" );
                Log.d(TAG, "   ---             JSON response : " + response.toString());
                processDownloadResponse(response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Download : Error Volley or empty return : " + error.getMessage());
                error.printStackTrace();
            }
        });
        mRequestQueue.add(mRequestDownload);

    }


    void processDownloadResponse(JSONArray response){
        Log.d(TAG, "--- DownloadService        --- processDownloadResponse");
        JSONObject json_data;
        String dateSynchro;

        Log.d(TAG, "                    Réponse Volley (JSONArray) : " + response);

        try {
            // First object gives date from the server (not used yet)
            json_data = response.getJSONObject(0);
            dateSynchro = json_data.getString("DateSynchro");
            Log.d(TAG, "                    Date synchro : " + dateSynchro);

            // Update sync date in SharedPreferences
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("last_synchro_", dateSynchro);
            editor.apply();

            // Loop on remaining response JSON objects, each is one requests's proof
            Log.d(TAG, "                    -- Loop on response items");

        } catch (JSONException e) {
            Log.e(TAG, "Download Error Volley (JSONException):" + e.toString());
        } catch (Exception e) {
            Log.e(TAG, "Download Error Volley (Erreur indéterminée) :" + e.toString());
        }

        for (int i = 1; i < response.length(); i++) {   // loop on responses
            try {
                // This bundle will be used to send the result to the calling activity
                Bundle bundle = new Bundle();

                // Check the response and the matching local db request
                // Decode next line from the response (json_data)
                json_data = response.getJSONObject(i);

                int request = Integer.valueOf(json_data.getString(PROOF_COL_REQUEST));
                int status = Integer.valueOf(json_data.getString(PROOF_COL_STATUS));
                String chain = json_data.getString(PROOF_COL_CHAIN);
                String tree = json_data.getString(PROOF_COL_TREE);
                String txid = json_data.getString(PROOF_COL_TXID);
                String txinfo = json_data.getString(PROOF_COL_INFO);


                // check that server response is in correct  status (READY)
                if (status != STATUS_READY) {
                    Log.d(TAG, "                    SKIP request "+request +", status "+status);
                    continue;
                }

                // Check that the request exists in local db
                Cursor c = mDatabase.getOneCursorProofRequest(request);
                if (c.getCount() != 1) {
                    //TODO : manage this case that may happen if user clears the local data
                    Log.e(TAG, "******** Erreur update : demande non trouvee en base SQLite : " + request);
                    continue;
                }
                c.moveToFirst();
                int idRequest = c.getInt(REQUEST_NUM_COL_ID);
                int currentStatut = c.getInt(REQUEST_NUM_COL_STATUS);
                int fileType = c.getInt(REQUEST_NUM_COL_FILETYPE);
                String fileName = c.getString(REQUEST_NUM_COL_FILENAME);
                String docHash = c.getString(REQUEST_NUM_COL_DOC_HASH);
                String message = c.getString(REQUEST_NUM_COL_MESSAGE);
                Statement statement = new Statement(docHash,
                        message,
                        tree,
                        chain,
                        txid,
                        txinfo);

                c.close();

                // Check that proof for that request was not already received
                if (currentStatut != STATUS_SUBMITTED) {
                    ackServerProofReceived(request);
                    Log.w(TAG, "******** Warning request is already proved : " + request);
                    continue;
                }

                // Step 3 : Write proof params into proof file, then delte draft file
                StampFile stampFile = StampFile.init(this, idRequest, fileName, fileType);
                stampFile.write(statement.getString());
                stampFile.eraseDraft();



                // Step 4 ; update request
                // Request's proof is received, update local db request's status
                Log.d(TAG, "                    MAJ  request "+request +", new status "+STATUS_FINISHED);
                int result = mDatabase.updateProofRequestFromReponseServeur(request);

                bundle.putInt(EXTRA_REQUEST_ID, request);

                switch (result){
                    case RETURN_DBUPDATE_OK:
                        // Send back ACK to the server
                        ackServerProofReceived(request);
                        bundle.putInt(EXTRA_RESULT_VALUE, RETURN_DOWNLOAD_OK);
                        break;
                    default:
                        Log.e(TAG, "Update Proof: Error updating local db request");
                        bundle.putInt(EXTRA_RESULT_VALUE, RETURN_DOWNLOAD_KO);
                }
                mResultReceiver.send(RETURN_DOWNLOAD_OK, bundle);
            } catch (JSONException e) {
                Log.e(TAG, "Download Error Volley (JSONException):" + e.toString());
            } catch (ParseException e) {
                Log.e(TAG, "Download Error Volley (ParseException) :" + e.toString());
            } catch (SecurityException e) {
                Log.e(TAG, "Download Error Volley (SecurityException :" + e.toString());
            } catch (ProofException e) {
                Log.e(TAG, "Download Error Volley (ProofException :" + e.toString());
            } catch (Exception e) {
                Log.e(TAG, "Download Error Volley (Erreur indéterminée) :" + e.toString());
            }
        }
        Log.d(TAG, "                    -- End Loop on response items");
    }

    public void ackServerProofReceived(int request){
        Log.d(TAG, "--- DownloadService        --- ackServerProofReceived");
        String sUrl;
        // build complete URL with instance id and request number
        sUrl = String.format(Locale.US, URL_SIGNOFF_PROOF, mInstanceId, request);
        Log.d(TAG, "               signoff, sURL = " + sUrl);

        // Create volley requests and its callbacks :
        JsonArrayRequest mArrayRequest = new JsonArrayRequest(sUrl, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                Log.d(TAG, "            Reponse signoff JSON : " + response.toString());
                // nothing special to do
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error Volley signoff : " + error.getMessage());
            }
        });
        mRequestQueue.add(mArrayRequest);
        return;
    }

}
