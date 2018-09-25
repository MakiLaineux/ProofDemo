package com.technoprimates.proofdemo.services;

import android.app.Activity;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ParseException;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.os.ResultReceiver;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.technoprimates.proofdemo.db.DatabaseHandler;
import com.technoprimates.proofdemo.util.Globals;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class UploadService extends IntentService {

    //Contexte
    private Context mContext;
    // Base SQLite à traiter
    private DatabaseHandler mBaseLocale;
    // File d'attente des requêtes Volley :
    private RequestQueue mRequestQueue;
    // ReultReceiver à activer à chaque réponsr du serveur
    private ResultReceiver mResultReceiver;
    public LocalBroadcastManager mBroadcaster = null; //Pour notification de nouveaute(s) interne a l'appli

    public UploadService() {
        // Used to name the worker thread, important only for debugging.
        super("UploadService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(Globals.TAG, "service Upload (onCreate)");
        mBroadcaster = LocalBroadcastManager.getInstance(this);

        // init du contexte et de la file d'attente Volley
        mContext = getApplicationContext();
        mRequestQueue = Volley.newRequestQueue(this);

        // get singleton instance of database
        mBaseLocale = DatabaseHandler.getInstance(Globals.context);
        Log.d(Globals.TAG, "service Upload OnCreate");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        int nb = 0, i=0, param=0, idBdd =0;
        Cursor c;
        String sUrl, sHash;
        if (intent != null) {

            // Recup du receiver passé au service
            mResultReceiver = intent.getParcelableExtra(Globals.SERVICE_RECEIVER);

            // Recup du paramètre indiquant le ou les enregistrements à traiter
            // L'extra contient l'idbdd de l'enr à traiter ou la valeur IDBDD_ALL
            param = intent.getIntExtra(Globals.SERVICE_IDBDD, Globals.IDBDD_ALL);
            Log.d(Globals.TAG, "service Upload (onHandleIntent), paramètre passé : "+param);

            if (param == Globals.IDBDD_ALL) { //Tous les enr éligibles sont à traiter
                c = mBaseLocale.getAllProofRequests(Globals.STATUS_HASH_OK);
                if (c != null) {
                    nb = c.getCount();
                }
            } else { // un seul enr à traiter
                c = mBaseLocale.getOneCursorProofRequest(param);
                nb = 1;
            }
            if (c==null || nb==0) return;

            // Envoi un par un au serveur et MAJ du statut
            for (i=0; i<nb; i++) {
                c.moveToPosition(i);
                idBdd = c.getInt(Globals.OBJET_NUM_COL_ID);
                sHash = c.getString(Globals.OBJET_NUM_COL_HASH);
                sUrl = Globals.URL_UPLOAD_DEMANDE + "?user=\'"+Globals.sUserId+"\'&id=1&demande=" +idBdd +"&hash=\'" + sHash+"\'";
                Log.d(Globals.TAG, "service Upload (onHandleIntent), idBdd : "+idBdd+", url: "+sUrl);

                // Création de la requête volley (avec ses callbacks) :
                JsonArrayRequest mRequeteUpload = new JsonArrayRequest(sUrl, new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        traiteReponseUpload(response);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(Globals.TAG, "Error Volley on upload: " + error.getMessage());
                    }
                });
                mRequestQueue.add(mRequeteUpload);
            }
            c.close();
        }
    }

    void traiteReponseUpload(JSONArray response){
        JSONObject json_data;
        int idBdd;

        Log.d(Globals.TAG, "Upload, Réponse Volley : " + response);

        try {
            // Décodage de la réponse (json_data)
            json_data = response.getJSONObject(0);
            idBdd = Integer.valueOf(json_data.getString("demande"));

            // MAJ en bdd du statut
            mBaseLocale.updateStatutProofRequest(idBdd, Globals.STATUS_SUBMITTED);

            // Envoi de message à l'UI via le receiver avec l'id serveur reçu
            Bundle bundle = new Bundle();
            bundle.putInt(Globals.SERVICE_IDBDD, idBdd);
            bundle.putInt(Globals.SERVICE_RESULT_VALUE, Globals.UPLOAD_SUCCESS);
            mResultReceiver.send(Activity.RESULT_OK, bundle);

            Intent intent = new Intent(Globals.MAJ_BDD);
            //mBroadcaster.sendBroadcast(intent);


        } catch (JSONException e) {
            Log.e(Globals.TAG, "Error JSON (01) Volley On Response :" + e.toString());
        } catch (ParseException e) {
            Log.e(Globals.TAG, "Erreur Parse (02) Volley On Response :" + e.toString());
        } catch (SecurityException e) {
            Log.e(Globals.TAG, "Erreur Permission (04) Volley On Response :" + e.toString());
        } catch (Exception e) {
            Log.e(Globals.TAG, "Erreur indéterminée (03) Volley On Response :" + e.toString());
        }
    }
}
