package com.technoprimates.proofdemo.services;

import android.app.IntentService;
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
import com.technoprimates.proofdemo.struct.Proof;
import com.technoprimates.proofdemo.util.Constants;
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
3. Store the proof elements and update the request's status in the local database
4. Build the "proof file" on SD Card (zip file or file accepting metadata)
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
        Log.d(Constants.TAG, "--- DownloadService        --- onCreate");
        super.onCreate();

        mRequestQueue = Volley.newRequestQueue(this);

        // get singleton instance of database
        mDatabase = DatabaseHandler.getInstance(this);

        // get instance id, the server uses this data to identify the client
        // if the instance id does not already exist, create it, based on the installation id of the app
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mInstanceId = sharedPref.getString("ID", "");
        if (mInstanceId.equals("")) {
            Log.d(Constants.TAG, "New UserId");
            mInstanceId = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("ID", mInstanceId);
            editor.apply();
        }

        Log.d(Constants.TAG, "service Download OnCreate");
    }

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, DownloadService.class, Constants.JOB_SERVICE_DOWNLOAD, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.d(Constants.TAG, "--- DownloadService        --- onHandleIntent");
        String sUrl;

        // get the receiver
        mResultReceiver = intent.getParcelableExtra(Constants.EXTRA_RECEIVER);

        // Step 1 : build complete URL with installation id
        sUrl = String.format(Locale.US, Constants.URL_DOWNLOAD_PROOF, mInstanceId);
        Log.d(Constants.TAG, "                 URL String : " + sUrl);

        // Step 2 : Create Volley request and its callbacks :
        JsonArrayRequest mRequestDownload = new JsonArrayRequest(sUrl, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                Log.d(Constants.TAG, "   --- Download : Callback on server response" );
                Log.d(Constants.TAG, "   ---             JSON response : " + response.toString());
                processDownloadResponse(response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(Constants.TAG, "Download : Error Volley or empty return : " + error.getMessage());
                error.printStackTrace();
            }
        });
        mRequestQueue.add(mRequestDownload);

    }


    void processDownloadResponse(JSONArray response){
        Log.d(Constants.TAG, "--- DownloadService        --- processDownloadResponse");
        JSONObject json_data;
        String dateSynchro;

        Log.d(Constants.TAG, "                    Réponse Volley (JSONArray) : " + response);

        try {
            // First object gives date from the server (not used yet)
            json_data = response.getJSONObject(0);
            dateSynchro = json_data.getString("DateSynchro");
            Log.d(Constants.TAG, "                    Date synchro : " + dateSynchro);

            // Update sync date in SharedPreferences
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("last_synchro_", dateSynchro);
            editor.commit();

            // Loop on remaining response JSON objects, each is one requests's proof
            Log.d(Constants.TAG, "                    -- Loop on response items");
            for (int i = 1; i < response.length(); i++) {
                // This bundle will be used to send the result to the calling activity
                Bundle bundle = new Bundle();

                // Decode next line from the response (json_data)
                json_data = response.getJSONObject(i);
                Proof r = new Proof(json_data);
                if (r == null) {
                    Log.e(Constants.TAG, "--- Download Service : Proof Object is null");
                    return;
                }

                // Step 3 ; update request, only if proof is ok
                if (r.mStatus != Constants.STATUS_READY) {
                    Log.d(Constants.TAG, "                    SKIP request "+r.mRequest +", status "+r.mStatus);
                    continue;
                }
                // Check thar erquests exists in local db
                Cursor c = mDatabase.getOneCursorProofRequest(r.mRequest);
                if (c.getCount() != 1) {
                    Log.e(Constants.TAG, "******** Erreur update : demande non trouvee en base SQLite : " + r.mRequest);
                    continue;
                }

                // Check that proof for that request was not already received
                c.moveToFirst();
                int currentStatut = c.getInt(Constants.REQUEST_NUM_COL_STATUS);
                c.close();
                if (currentStatut != Constants.STATUS_SUBMITTED) {
                    ackServerProofReceived(r);
                    Log.w(Constants.TAG, "******** Warning update : demande deja prouvee : " + r.mRequest);
                    continue;
                }

                // Request's proof is received, update local db request's status
                Log.d(Constants.TAG, "                    MAJ  request "+r.mRequest +", new status "+Constants.STATUS_FINISHED_OK);
                int result = mDatabase.updateProofRequestFromReponseServeur(
                        r.mRequest,
                        Constants.STATUS_FINISHED_OK,
                        r.mTree,
                        r.mTxid,
                        r.mInfo);

                bundle.putInt(Constants.EXTRA_REQUEST_ID, r.mRequest);

                switch (result){
                    case Constants.RETURN_DBUPDATE_OK:
                        // Step 4 : Write proof paramas into proof file (zip)
                        String displayName = mDatabase.getOneProofRequest(r.mRequest).get_filename();

                        if (!ProofUtils.buildProofFile(this, displayName, r)){
                            return;
                        }

                        // Send back ACK to the server
                        ackServerProofReceived(r);
                        bundle.putInt(Constants.EXTRA_RESULT_VALUE, Constants.RETURN_DOWNLOAD_OK);
                        break;
                    default:
                        Log.e(Constants.TAG, "Update Proof: Error updating local db request");
                        bundle.putInt(Constants.EXTRA_RESULT_VALUE, Constants.RETURN_DOWNLOAD_KO);
                }
                mResultReceiver.send(Constants.RETURN_DOWNLOAD_OK, bundle);
                Log.d(Constants.TAG, "                    -- End Loop on response items");
            }
        } catch (JSONException e) {
            Log.e(Constants.TAG, "Download Error Volley (JSONException):" + e.toString());
        } catch (ParseException e) {
            Log.e(Constants.TAG, "Download Error Volley (ParseException) :" + e.toString());
        } catch (SecurityException e) {
            Log.e(Constants.TAG, "Download Error Volley (SecurityException :" + e.toString());
        } catch (Exception e) {
            Log.e(Constants.TAG, "Download Error Volley (Erreur indéterminée) :" + e.toString());
        }
    }

    public void ackServerProofReceived(Proof r){
        Log.d(Constants.TAG, "--- DownloadService        --- ackServerProofReceived");
        String sUrl;
        // build complete URL with instance id and request number
        sUrl = String.format(Locale.US, Constants.URL_SIGNOFF_PROOF, mInstanceId, r.mRequest);
        Log.d(Constants.TAG, "               signoff, sURL = " + sUrl);

        // Create volley requests and its callbacks :
        JsonArrayRequest mArrayRequest = new JsonArrayRequest(sUrl, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                Log.d(Constants.TAG, "            Reponse signoff JSON : " + response.toString());
                // nothing special to do
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(Constants.TAG, "Error Volley signoff : " + error.getMessage());
            }
        });
        mRequestQueue.add(mArrayRequest);
        return;
    }

}
