package com.technoprimates.proofdemo.services;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;

import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import com.technoprimates.proofdemo.db.DatabaseHandler;
import com.technoprimates.proofdemo.util.Constants;
import com.technoprimates.proofdemo.util.FileUtils;
import com.technoprimates.proofdemo.util.ProofUtils;

/* This Service does the following
1. Create a "proof request" record in the local database, take note of the resulting request id
2. Copies the file selected by the user into app data space (name in app data space = request id)
3. Performs a hash of the original file(SHA-256 algorithm)
4. Stores the resulting hash in the proof request (local db)
*/
public class PrepareService extends JobIntentService {

    private static final int BUFFER = 2048;

    // ResultReceiver to send back results to the calling activity
    private ResultReceiver mResultReceiver;

    @Override
    public void onCreate() {
        Log.d(Constants.TAG, "--- PrepareService        --- onCreate");
        super.onCreate();
    }

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, PrepareService.class, Constants.JOB_SERVICE_PREPARE, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {

        Log.d(Constants.TAG, "--- PrepareService        --- onHandleIntent");

        // get Receiver to send progress to the calling activity
        mResultReceiver = intent.getParcelableExtra(Constants.EXTRA_RECEIVER);

        final String fullSourceUri = intent.getStringExtra(Constants.EXTRA_FILENAME);

        // Display name, used to display to the user
        String displayName = FileUtils.getFilename(this, Uri.parse(fullSourceUri));

        Log.d(Constants.TAG, String.format("full Uri : %1$s, displayName : %2$s", fullSourceUri, displayName));
        if (fullSourceUri == null){
            Log.e(Constants.TAG, "Erreur : pas de nom de fichier");
        }

        // Step 1 : Create a proof request with status INITIALIZED

        // DB insertion
        DatabaseHandler baseLocale = DatabaseHandler.getInstance(this);
        final int idBdd = (int) baseLocale.insertProofRequest(displayName);

        // If insertion succeeded, one new record was created, and calling activity might have to update UI
        // If insertion failed, inform the calling activity and stop the service
        if (idBdd!=-1){
            mResultReceiver.send(Constants.RETURN_DBUPDATE_OK, null);
        } else {
            mResultReceiver.send(Constants.RETURN_DBUPDATE_KO, null);
            Log.e(Constants.TAG, "ERROR DB Insertion failed");
            return;
        }

        // The name of the file to create in the app data storage is the local database request id
        String nameCopy = String.format("%04d", idBdd);


        // Step 2 : copy the file selected by the user in the app data space and perform its hash

        if (!ProofUtils.saveFileContentToAppData(this, fullSourceUri, nameCopy)){
            mResultReceiver.send(Constants.RETURN_COPYFILE_KO, null);
            Log.e(Constants.TAG, "ERROR while copying file into App data");
            return;
        }


        // Step 3 : Compute the hash, working in App space
        String hash = ProofUtils.computeHashFromFile(this, nameCopy);
        if (hash == null){
            mResultReceiver.send(Constants.RETURN_COPYFILE_KO, null);
            Log.e(Constants.TAG, "ERROR while computing hash from App data file");
            return;
        }

        final int updateDb = baseLocale.updateHashProofRequest(idBdd, hash);
        if (updateDb != Constants.RETURN_DBUPDATE_OK) {
            Log.e(Constants.TAG, "Error updating request with hash");
            return;
        }

        // Done, send back the db request id, to allow for Uploading
        Bundle bundle = new Bundle();
        bundle.putInt(Constants.EXTRA_REQUEST_ID, idBdd);
        mResultReceiver.send(Constants.RETURN_COPYANDHASH_OK, bundle);

    }
}
