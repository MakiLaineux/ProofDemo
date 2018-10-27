package com.technoprimates.proofdemo.services;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ParseException;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.technoprimates.proofdemo.db.DatabaseHandler;
import com.technoprimates.proofdemo.util.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

/* This Service tries to upload a proof request on the distant server
1. Get the requests to upload and loop on them
For each of those requests:
2. Build the url to invoke
3. Build a Volley request with this url and launch it
4. On success, update the request's status in local db
*/
public class UploadService extends IntentService {

    // Local db
    private DatabaseHandler mBaseLocale;

    // Volley requests queue :
    private RequestQueue mRequestQueue;

    // Receiver used to send back results to the calling activity
    private ResultReceiver mResultReceiver;

    // InstanceId to identify the device when communicating with the server
    private String mInstanceId;

    public UploadService() {
        // Used to name the worker thread, important only for debugging.
        super("UploadService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(Constants.TAG, "--- UploadService          --- onCreate");

        mRequestQueue = Volley.newRequestQueue(this);

        // get singleton instance of database
        mBaseLocale = DatabaseHandler.getInstance(this);

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

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(Constants.TAG, "--- UploadService          --- onHandleIntent");
        int nb = 0, i=0, param=0, idBdd =0;
        Cursor c;
        String sUrl, sHash;
        if (intent != null) {

            // Get the receiver
            mResultReceiver = intent.getParcelableExtra(Constants.EXTRA_RECEIVER);

            // Get the param identifying the record to handle
            // This is en extra containing the bdd id to handle or the value IDBDD_ALL
            param = intent.getIntExtra(Constants.EXTRA_REQUEST_ID, Constants.IDBDD_ALL);
            Log.d(Constants.TAG, "              record to handle (-1 for all): "+param);

            if (param == Constants.IDBDD_ALL) { // All matching records to handle
                c = mBaseLocale.getAllProofRequests(Constants.STATUS_HASH_OK);
                if (c != null) {
                    nb = c.getCount();
                }
            } else { // one record only
                c = mBaseLocale.getOneCursorProofRequest(param);
                nb = 1;
            }
            if (c==null || nb==0) return;

            // Send to the server, one by one, and update status

            Log.d(Constants.TAG, "              loop on records");

            for (i=0; i<nb; i++) {
                // get data to upload
                c.moveToPosition(i);
                idBdd = c.getInt(Constants.REQUEST_NUM_COL_ID);
                sHash = c.getString(Constants.REQUEST_NUM_COL_HASH);
                // build the URL to launch
                sUrl = String.format(Locale.US, Constants.URL_UPLOAD_DEMANDE, mInstanceId, idBdd, sHash );
                Log.d(Constants.TAG, "               record : "+idBdd+", url: "+sUrl);

                // Create Volley request with callbacks :
                JsonArrayRequest mRequeteUpload = new JsonArrayRequest(sUrl, new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        processUploadResponse(response);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(Constants.TAG, "Error Volley on upload: " + error.getMessage());
                    }
                });
                mRequestQueue.add(mRequeteUpload);
            }
            c.close();
        }
    }

    void processUploadResponse(JSONArray response){
        JSONObject json_data;
        int dbId;

        Log.d(Constants.TAG, "--- UploadService          --- processUploadResponse");
        Log.d(Constants.TAG, "           Réponse Volley : " + response);

        try {
            // Server response is a JSONArray with just one element
            json_data = response.getJSONObject(0);
            dbId = Integer.valueOf(json_data.getString("request"));

            // Update request's status in local database
            int success = mBaseLocale.updateStatutProofRequest(dbId, Constants.STATUS_SUBMITTED);

            // Notify calling activity
            mResultReceiver.send(success, null);

        } catch (JSONException e) {
            Log.e(Constants.TAG, "Error JSON (01) Volley On Response :" + e.toString());
        } catch (ParseException e) {
            Log.e(Constants.TAG, "Erreur Parse (02) Volley On Response :" + e.toString());
        } catch (SecurityException e) {
            Log.e(Constants.TAG, "Erreur Permission (04) Volley On Response :" + e.toString());
        } catch (Exception e) {
            Log.e(Constants.TAG, "Erreur indéterminée (03) Volley On Response :" + e.toString());
        }
    }
}
