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
import com.technoprimates.proofdemo.services.PrepareService;
import com.technoprimates.proofdemo.util.Constants;
import com.technoprimates.proofdemo.util.ServiceResultReceiver;
import com.technoprimates.proofdemo.services.UploadService;

import java.util.ArrayList;

public class ShareActivity extends Activity {
    // Local Db to update
    private DatabaseHandler mDatabase;
    public ServiceResultReceiver receiverForServices;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get singleton instance of database
        mDatabase = DatabaseHandler.getInstance(this);

        // setup receiver for services communications
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
        finish(); // finish immediately
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
//                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                manageOneFile(u);
            }
            Toast.makeText(getApplicationContext(), "Preuve demandée pour "+imageUris.size()+" fichier(s)", Toast.LENGTH_SHORT).show();
        }
    }

    void demandeUpload(int idBdd){

        Intent i = new Intent();
        // store the receiver in extra
        i.putExtra(Constants.EXTRA_RECEIVER, receiverForServices);
        // store the db id in extra
        i.putExtra(Constants.EXTRA_REQUEST_ID, idBdd);
        UploadService.enqueueWork(this, i);
    }

    // Setup the callback for when data is received from the service
    public void setupServiceReceiver() {
        receiverForServices = new ServiceResultReceiver(new Handler());
        // This is where we specify what happens when data is received from the service
        receiverForServices.setReceiver(new ServiceResultReceiver.Receiver() {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                switch (resultCode){
                    case(Constants.RETURN_COPYANDHASH_OK) :
                        int idBdd = resultData.getInt(Constants.EXTRA_REQUEST_ID);
                        demandeUpload(idBdd);
                        Log.w(Constants.TAG, "ShareActivity : Send UI Broadcast, COPYANDHASH OK");
                        refreshUI();
                        break;
                    case(Constants.RETURN_UPLOAD_OK) :
                        Log.w(Constants.TAG, "ShareActivity : Send UI Broadcast, UPLOAD OK");
                        refreshUI();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void refreshUI(){
        Intent intent = new Intent(Constants.EVENT_REFRESH_UI);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void manageOneFile(Uri u){
        if (u != null) {

            // Calcul du hash, MAJ en BDD et recopie du fichier par un service
            Intent i = new Intent();
            // store the receiver in extra
            i.putExtra(Constants.EXTRA_RECEIVER, receiverForServices);
            i.putExtra(Constants.EXTRA_FILENAME, u.toString());  // nom complt du fichier (pour recopie)
            PrepareService.enqueueWork(this, i);
        }

    }
}


