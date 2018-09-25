package com.technoprimates.proofdemo.services;

import android.app.Activity;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.os.ResultReceiver;
import android.util.Log;

import com.technoprimates.proofdemo.db.DatabaseHandler;
import com.technoprimates.proofdemo.db.ProofRequest;
import com.technoprimates.proofdemo.util.Globals;
import com.technoprimates.proofdemo.util.FileUtils;

import java.io.File;
import java.io.IOException;

public class HashAndCopyService extends IntentService {

    //Contexte
    private Context mContext;
    // Base SQLite à mettre à jour
    private DatabaseHandler mBaseLocale;
    public LocalBroadcastManager mBroadcaster = null; //Pour notification de nouveaute(s) interne a l'appli

    public HashAndCopyService() {
        // Used to name the worker thread, important only for debugging.
        super("HashAndCopyService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(Globals.TAG, "service Hash (onCreate)");
        // init du contexte
        mContext = getApplicationContext();
        mBroadcaster = LocalBroadcastManager.getInstance(this);

        // get singleton instance of database
        mBaseLocale = DatabaseHandler.getInstance(Globals.context);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {

            // Recup du receiver passé au service
            ResultReceiver rec = intent.getParcelableExtra(Globals.SERVICE_RECEIVER);

            // recup du numéro d'enregistrement bdd à traiter
            final int idBdd = intent.getIntExtra(Globals.SERVICE_IDBDD, 0);
            Log.d(Globals.TAG, "service Hash (onHandleIntent), idBdd : "+idBdd);

            // Nom du fichier (pour la recopie)
            final String nameSource = intent.getStringExtra(Globals.SERVICE_FILENAME);
            if (nameSource == null){
                Log.e(Globals.TAG, "Erreur : pas de nom de fichier");
            }
            File sourceFile = new File(nameSource);

            // Calcul du hash et MAJ en BDD
            String h = handleActionHash(idBdd);

            // MAJ en bdd du hash et du statut
            mBaseLocale.updateHashProofRequest(idBdd, h);

            //TODO : recuperer et gerer le cas erreur de hash : pas de set statut retour HASH_FAILED
            mBaseLocale.updateStatutProofRequest(idBdd, Globals.STATUS_HASH_OK);

            //Copie du fichier, suffixé par son idBdd, dans le répertoire privé de l'appli
            String nameDest = nameSource.substring(nameSource.lastIndexOf("/") + 1)+"."+String.format("%04d", idBdd);
            File destFile = new File(getFilesDir(), nameDest);
            try {
                FileUtils.copy(sourceFile, destFile);
                Log.d(Globals.TAG, "service Hash (onHandleIntent), copy ok, idBdd : "+idBdd+", dest : "+destFile);
            } catch (IOException e) {
                Log.e(Globals.TAG, "Copy Exception : "+e);
                e.printStackTrace();
            }

            // Envoi de message à l'UI via le receiver avec l'id de l'enr concerné
            Bundle bundle = new Bundle();
            bundle.putInt(Globals.SERVICE_IDBDD, idBdd);
            bundle.putInt(Globals.SERVICE_RESULT_VALUE, Globals.HASH_SUCCESS);
            rec.send(Activity.RESULT_OK, bundle);
        }
    }

    protected String handleActionHash(int idBdd) {
        // Recup de l'objet concerné
        ProofRequest p = mBaseLocale.getOneProofRequest(idBdd);

        // Calcul du hash
        String hash = FileUtils.computeHash(p.get_chemin());
        Log.d(Globals.TAG, "service Hash (onHandleIntent), hash ok, idBdd : "+idBdd+", chemin : " + p.get_chemin()+" hash : " + hash);
        return hash;

    }

}
