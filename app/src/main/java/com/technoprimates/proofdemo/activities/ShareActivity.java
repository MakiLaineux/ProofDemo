package com.technoprimates.proofdemo.activities;


import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.NavigationView;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.technoprimates.proofdemo.services.SubmitService;
import com.technoprimates.proofdemo.util.Constants;
import com.technoprimates.proofdemo.util.ServiceResultReceiver;
import com.technoprimates.proofdemo.util.VisuProofListener;

import java.util.ArrayList;

public class ShareActivity extends AppCompatActivity implements ServiceResultReceiver.Receiver {
    // Callback for services feedback
    public ServiceResultReceiver mReceiver;

    // Number of files remaining, don't finish activity while not zero
    private int mCount = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Receiver for services feedbacks
        mReceiver = new ServiceResultReceiver(new Handler());
        mReceiver.setReceiver(this);

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

    private void manageOneFile(Uri u){
        if (u != null) {

            // Increment count
            mCount ++;
            Log.d("RECV", "mCount : "+mCount+ ", uri : "+u.toString());

            // Calcul du hash, MAJ en BDD et recopie du fichier par un service
            Intent i = new Intent();
            // store the receiver in extra
            i.putExtra(Constants.EXTRA_RECEIVER, mReceiver);
            i.putExtra(Constants.EXTRA_FILENAME, u.toString());  // nom complt du fichier (pour recopie)
            SubmitService.enqueueWork(this, Constants.TASK_PREPARE, i);
        }

    }


    private void refreshUI(){
        Intent intent = new Intent(Constants.EVENT_REFRESH_UI);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        Log.i(Constants.TAG, "ShareActivity resultCode: "+resultCode);
        switch (resultCode){
            case(Constants.RETURN_PREPARE_OK) :
                Log.d("RECV", "ERROR, resultCode : PREPARE_KO");
                break;
            case(Constants.RETURN_PREPARE_KO) :
                mCount --;
                Log.d("RECV", "mCount : "+mCount+ ", resultCode : "+resultCode);
                refreshUI();
                break;
            case(Constants.RETURN_UPLOAD_OK) :
                mCount --;
                Log.d("RECV", "mCount : "+mCount+ ", resultCode : "+resultCode);
                refreshUI();
                break;
            case(Constants.RETURN_UPLOAD_KO) :
                mCount --;
                Log.d("RECV", "mCount : "+mCount+ ", resultCode : "+resultCode);
                refreshUI();
                break;
            case(Constants.RETURN_DBUPDATE_OK) :
                Log.d("RECV", "mCount : "+mCount+ ", resultCode : DBUPDATE_OK");
                refreshUI();
                break;
            case(Constants.RETURN_DBUPDATE_KO) :
            default:
                // Something is wrong, get cause and log it
                Log.d("RECV", "ERROR, resultCode : "+resultCode);
                Log.e(Constants.TAG, resultData.getString("error"));
                break;
        }
        // Finish the activity once all files are processed
        if (mCount == 0) finish();

    }

    @Override
    protected void onResume() {
        // register receivers
        mReceiver.setReceiver(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        // unregister receivers
        mReceiver.setReceiver(null);
        super.onPause();
    }

}


