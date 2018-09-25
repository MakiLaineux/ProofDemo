package com.technoprimates.proofdemo.services;

import android.app.Activity;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ParseException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.os.ResultReceiver;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.technoprimates.proofdemo.db.DatabaseHandler;
import com.technoprimates.proofdemo.util.Globals;
import com.technoprimates.proofdemo.struct.RetourServeur;
import com.technoprimates.proofdemo.util.FileUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class DownloadService extends IntentService {

    //Contexte
    private Context mContext;
    // Base SQLite à traiter
    private DatabaseHandler mBaseLocale;
    // File d'attente des requêtes Volley :
    private RequestQueue mRequestQueue;
    // ReultReceiver à activer à chaque réponse du serveur
    private ResultReceiver mResultReceiver;

    public DownloadService() {
        // Used to name the worker thread, important only for debugging.
        super("DownloadService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // init du contexte et de la file d'attente Volley
        mContext = getApplicationContext();
        mRequestQueue = Volley.newRequestQueue(this);

        // get singleton instance of database
        mBaseLocale = DatabaseHandler.getInstance(Globals.context);
        Log.d(Globals.TAG, "service Download OnCreate");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        int nb = 0, i=0, idBdd =0;
        String sUrl, sHash;
        if (intent != null) {

            // Recup du receiver passé au service
            mResultReceiver = intent.getParcelableExtra(Globals.SERVICE_RECEIVER);

            // constitution de l'URL complète avec le user id
            sUrl = Globals.URL_DOWNLOAD_PROOF + "?user=\'" + Globals.sUserId+"\'";
            Log.d(Globals.TAG, "onStartCommand, sURL = " + sUrl);

            // Création de la requête volley (avec ses callbacks) :
            JsonArrayRequest mRequestDownload = new JsonArrayRequest(sUrl, new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    Log.d(Globals.TAG, "JSON : " + response.toString());
                    traiteReponseDownload(response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(Globals.TAG, "Error Volley on download : " + error.getMessage());
                }
            });
            mRequestQueue.add(mRequestDownload);
        }
    }

    void traiteReponseDownload(JSONArray response){
        JSONObject json_data;
        String dateSynchro;

        Log.d(Globals.TAG, "Download, Réponse Volley : " + response);

        try {
            // Le premier objet JSON donne la date du serveur
            json_data = response.getJSONObject(0);
            dateSynchro = json_data.getString("DateSynchro");

            Log.d(Globals.TAG, "--- Download service, JSON : " + json_data);

            // mise à jour de la date de synchro dans les paramètres de l'appli
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(Globals.context);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("last_synchro_", dateSynchro);
            editor.commit();

            // Décodage de la réponse (json_data)
            json_data = response.getJSONObject(0);

            // Boucle sur le reste des objets JSON (qui sont des preuves recues du serveur)
            for (int i = 1; i < response.length(); i++) {
                // Décodage d'une ligne de réponse (json_data)
                json_data = response.getJSONObject(i);
                RetourServeur r = new RetourServeur(json_data);
                if (r == null) {
                    Log.e(Globals.TAG, " NULLLLLLLLLLLLLLLLLLLLLLL");
                    return;
                }

                // Bundle pour le receiver avec la demande concernée et l'operation effectuée
                Bundle bundle = new Bundle();
                bundle.putInt(Globals.SERVICE_IDBDD, r.mDemande);

                // Update seulement si la preuve est OK
                if (r.mStatut != Globals.STATUS_READY)
                    continue;

                // On verifie que la requete est en base
                Cursor c = mBaseLocale.getOneCursorProofRequest(r.mDemande);
                if (c.getCount() != 1) {
                    Log.e(Globals.TAG, "******** Erreur update : demande non trouvee en base SQLite : " + r.mDemande);
                    continue;
                }

                // On verifie que la preuve pour cette requete n'est pas encore enregistree
                c.moveToFirst();
                int currentStatut = c.getInt(Globals.OBJET_NUM_COL_STATUT);
                c.close();
                if (currentStatut != Globals.STATUS_SUBMITTED) {
                    envoiAccuseReceptionServeur(r);
                    Log.w(Globals.TAG, "******** Warning update : demande deja prouvee : " + r.mDemande);
                    continue;
                }

                // Sinon on met à jour la bdd, on cree le zip de preuve et on envoie un accuse de reception
                // au serveur pour qu'il puisse enregistrer que la demande est terminée

                int result = mBaseLocale.updateProofRequestFromReponseServeur(
                        r.mDemande,
                        Globals.STATUS_FINISHED_OK,
                        r.mTree,
                        r.mTxid,
                        r.mInfo);

                switch (result){
                    case Globals.UPDATE_OK:
                        // Création du fichier preuve (zip)
                        creeZipProof(r);
                        envoiAccuseReceptionServeur(r);
                        bundle.putInt(Globals.SERVICE_RESULT_VALUE, Globals.DOWNLOAD_SUCCESS);
                        break;
                    default:
                        Log.e(Globals.TAG, "Update Proof: Erreur impossible");
                        bundle.putInt(Globals.SERVICE_RESULT_VALUE, Globals.DOWNLOAD_FAILED);
                }
                mResultReceiver.send(Activity.RESULT_OK, bundle);
            }
        } catch (JSONException e) {
            Log.e(Globals.TAG, "Download Error Volley (JSONException):" + e.toString());
        } catch (ParseException e) {
            Log.e(Globals.TAG, "Download Error Volley (ParseException) :" + e.toString());
        } catch (SecurityException e) {
            Log.e(Globals.TAG, "Download Error Volley (SecurityException :" + e.toString());
        } catch (Exception e) {
            Log.e(Globals.TAG, "Download Error Volley (Erreur indéterminée) :" + e.toString());
        }
    }

    public void creeZipProof(RetourServeur r){
        String nameOrigin = mBaseLocale.getOneProofRequest(r.mDemande).get_chemin();
        String nameShort = nameOrigin.substring(nameOrigin.lastIndexOf("/") + 1);
        String nameSource = nameShort + "."+String.format("%04d", r.mDemande);
        String nameDest = nameSource+".zip";
        File sourceFile = new File(getFilesDir(), nameSource);

        try {
            FileUtils.createZip(sourceFile, nameShort, nameDest, r);
        } catch (IOException e) {
            Log.e(Globals.TAG, "Zip Exception : "+e);
            e.printStackTrace();
        }
    }

    public void envoiAccuseReceptionServeur(RetourServeur r){
        String sUrl;
        // constitution de l'URL complète avec le user id et le nuemero de demande
        sUrl = Globals.URL_SIGNOFF_PROOF + "?user=\'" + Globals.sUserId+"\'&demande=\'"+r.mDemande+"\'";
        Log.d(Globals.TAG, "signoff, sURL = " + sUrl);

        // Création de la requête volley (avec ses callbacks) :
        JsonArrayRequest mArrayRequest = new JsonArrayRequest(sUrl, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                Log.d(Globals.TAG, "Reponse signoff JSON : " + response.toString());
                // rien de special a faire
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(Globals.TAG, "Error Volley signoff : " + error.getMessage());
            }
        });
        mRequestQueue.add(mArrayRequest);
        return;
    }

}
