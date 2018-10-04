package com.technoprimates.proofdemo.services;

import android.app.Activity;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.v4.content.LocalBroadcastManager;
import android.os.ResultReceiver;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import android.util.Log;

import com.technoprimates.proofdemo.R;
import com.technoprimates.proofdemo.db.DatabaseHandler;
import com.technoprimates.proofdemo.db.ProofRequest;
import com.technoprimates.proofdemo.util.Globals;
import com.technoprimates.proofdemo.util.FileUtils;
import com.technoprimates.proofdemo.util.HashGenerationException;
import com.technoprimates.proofdemo.util.HashGeneratorUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.transform.Source;

// This Service copies accesses a file selected by the user, copies it into app data space,
// performs a SHA-256 hash of the file and creates a "proof request" record in the local database

public class CopyAndHashService extends IntentService {

    private static final int BUFFER = 2048;

    //Contexte
    private Context mContext;
    // Base SQLite à mettre à jour
    private DatabaseHandler mBaseLocale;
    public LocalBroadcastManager mBroadcaster = null; //Pour notification de nouveaute(s) interne a l'appli

    public CopyAndHashService() {
        // Used to name the worker thread, important only for debugging.
        super("CopyAndHashService");
    }

    @Override
    public void onCreate() {
        Log.d(Globals.TAG, "--- CopyAndHashService        --- onCreate");
        super.onCreate();
        // init du contexte
        mContext = getApplicationContext();
        mBroadcaster = LocalBroadcastManager.getInstance(this);

        // get singleton instance of database
        mBaseLocale = DatabaseHandler.getInstance(Globals.context);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        // First copy the selected file in the app data storage
        // Then perform its hash
        // Finaly store the name and the hash in the local db in a "request" record

        Log.d(Globals.TAG, "--- CopyAndHashService        --- onHandleIntent");
        if (intent != null) {

            // Retrieve the receiver passed (to send the result when the service is completed)
            ResultReceiver rec = intent.getParcelableExtra(Globals.SERVICE_RECEIVER);

            // Retrieve the db id (involved in the copied file name)
            final int idBdd = intent.getIntExtra(Globals.SERVICE_IDBDD, 0);

            // Copy the file into app data

            // Source File :
            final String nameSource = intent.getStringExtra(Globals.SERVICE_FILENAME);
            if (nameSource == null){
                Log.e(Globals.TAG, "Erreur : pas de nom de fichier");
            }
            final Uri uriSource = Uri.parse(nameSource);
            Log.d(Globals.TAG, "          idBdd : "+idBdd+", namesource : "+uriSource.toString());

            // Dest file:
            // Concatenate the original name file with the record id to build the name
            String nameDest = getFileName(uriSource)+"."+String.format("%04d", idBdd);
            Log.d(Globals.TAG, "          nameDest    : "+nameDest);

            try {
                InputStream in = getContentResolver().openInputStream(uriSource);
                File destFile = new File(getFilesDir(), nameDest);
                OutputStream out = new FileOutputStream(destFile);

                // Transfer bytes from in to out
                byte[] buf = new byte[BUFFER];
                int len;

                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();

                Log.d(Globals.TAG, "service Hash (onHandleIntent), copy ok, idBdd : "+idBdd+", dest : "+destFile);
            } catch (IOException e) {
                Log.e(Globals.TAG, "Copy Exception : "+e);
                e.printStackTrace();
            }

            // Compute the hash
            Log.d(Globals.TAG, "      Hash : NameSource : "+nameSource);
            String hash = null;

            try {
                InputStream in = getContentResolver().openInputStream(uriSource);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");

                byte[] buf = new byte[1024];
                int bytesRead = -1;

                while ((bytesRead = in.read(buf)) != -1) {
                    digest.update(buf, 0, bytesRead);
                }

                byte[] hashedBytes = digest.digest();

                hash = FileUtils.convertByteArrayToHexString(hashedBytes);
                in.close();

            } catch (NoSuchAlgorithmException | IOException e) {
                Log.e(Globals.TAG, "Hash Exception : "+e);
                e.printStackTrace();
            }

            // Update db with filename, hash and status
            mBaseLocale.updateHashProofRequest(idBdd, hash, nameDest);

            //TODO : recuperer et gerer le cas erreur de hash : pas de set statut retour HASH_FAILED
            mBaseLocale.updateStatutProofRequest(idBdd, Globals.STATUS_HASH_OK);


            // Envoi de message à l'UI via le receiver avec l'id de l'enr concerné
            Bundle bundle = new Bundle();
            bundle.putInt(Globals.SERVICE_IDBDD, idBdd);
            bundle.putInt(Globals.SERVICE_RESULT_VALUE, Globals.HASH_SUCCESS);
            rec.send(Activity.RESULT_OK, bundle);
        }
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}
