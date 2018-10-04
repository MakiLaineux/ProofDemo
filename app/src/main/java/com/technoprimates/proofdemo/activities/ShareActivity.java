package com.technoprimates.proofdemo.activities;


import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.technoprimates.proofdemo.db.DatabaseHandler;
import com.technoprimates.proofdemo.db.ProofRequest;
import com.technoprimates.proofdemo.services.CopyAndHashService;
import com.technoprimates.proofdemo.util.Globals;
import com.technoprimates.proofdemo.util.MyResultReceiver;
import com.technoprimates.proofdemo.services.UploadService;

import java.util.ArrayList;

public class ShareActivity extends Activity {
    // Base SQLite à mettre à jour
    private DatabaseHandler mBaseLocale;
    public MyResultReceiver receiverForServices;

    // Broadcast interne à l'appli : émis pour permettre à l'UI de se rafraichir si la bdd est mise à jour
    public LocalBroadcastManager mBroadcaster = null; //Pour notification de nouveaute(s) interne a l'appli

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBroadcaster = LocalBroadcastManager.getInstance(this);

        // get singleton instance of database
        mBaseLocale = DatabaseHandler.getInstance(Globals.context);

        // Mise en place du receiver pour la communication avec les services
        setupServiceReceiver();

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            handleSendFile(intent); // Handle single file being sent

        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            handleSendMultipleFiles(intent); // Handle multiple files being sent

        } else {
            // Handle other intents, such as being started from the home screen
        }
        finish(); // Fin immédiate de cette activité
    }


    void handleSendFile(Intent intent) {
        Uri u = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        manageOneFile(u);
        Toast.makeText(getApplicationContext(), "Preuve demandée pour 1 fichier", Toast.LENGTH_SHORT).show();
    }

    void handleSendMultipleFiles(Intent intent) {
        Uri u;
        ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (imageUris != null) {
            for (int i=0; i<imageUris.size();i++) {
                u = imageUris.get(i);
                manageOneFile(u);
            }
            Toast.makeText(getApplicationContext(), "Preuve demandée pour "+imageUris.size()+" fichier(s)", Toast.LENGTH_SHORT).show();
        }
    }

    void demandeUpload(int idBdd){
        // Lancement du service d'envoi au serveur des demandes préparées
        Intent i = new Intent(this, UploadService.class);
        //passage au service d'un receiver pour informer en retour l'activité
        i.putExtra(Globals.SERVICE_RECEIVER, receiverForServices);
        // passage du numero d'enregistrement à traiter
        i.putExtra(Globals.SERVICE_IDBDD, idBdd);
        // Start the service
        startService(i);
    }

    // Setup the callback for when data is received from the service
    public void setupServiceReceiver() {
        receiverForServices = new MyResultReceiver(new Handler());
        // This is where we specify what happens when data is received from the service
        receiverForServices.setReceiver(new MyResultReceiver.Receiver() {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                if (resultCode == RESULT_OK) {
                    int resultValue = resultData.getInt(Globals.SERVICE_RESULT_VALUE);
                    int idBdd = resultData.getInt(Globals.SERVICE_IDBDD);
                    if (resultValue == Globals.HASH_SUCCESS) {  // Lancement upload pour un enregistrement
                        Log.w(Globals.TAG, "ShareActivity (RECEIVER) Lancement Upload, idBdd = "+idBdd);
                        demandeUpload(idBdd);
                    }

                    // Envoi d'un broadcast pour  MAJ de l'UI (si une UI est en cours d'exécution)
                    Intent intent = new Intent(Globals.MAJ_BDD);
                    Log.w(Globals.TAG, "ShareActivity (RECEIVER) Sending UI broadcast, idBdd = "+idBdd);
                    mBroadcaster.sendBroadcast(intent);
                }
            }
        });
    }


    public void manageOneFile(Uri u){
        if (u != null) {
            ProofRequest p = new ProofRequest(
                    Globals.OBJET_NOID,
                    u.getPath(),
                    null,
                    Globals.STATUS_INITIALIZED,
                    "Not available yet",
                    "Not available yet",
                    "Not available yet",
                    null);

            if (p != null) {
                // Insertion en base et MAJ liste
                int idBdd = (int) mBaseLocale.insertProofRequest(p);

                // Calcul du hash, MAJ en BDD et recopie du fichier par un service
                Intent i = new Intent(this, CopyAndHashService.class);
                i.putExtra(Globals.SERVICE_IDBDD, idBdd);          // numéro de requete (pour MAJ BDD)
                i.putExtra(Globals.SERVICE_FILENAME, p.get_filename());  // nom complt du fichier (pour recopie)

                //passage au service d'un receiver pour informer en retour l'activité
                i.putExtra(Globals.SERVICE_RECEIVER, receiverForServices);

                // Start the service
                startService(i);
            }
        }

    }
}


