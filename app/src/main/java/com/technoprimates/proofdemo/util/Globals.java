package com.technoprimates.proofdemo.util;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;

import com.technoprimates.proofdemo.R;

/**
 * Created by MAKI LAINEUX on 18/03/2016.
 */
public class Globals extends Application {

    public static Context context;

    public static final String TAG = "PROOF";  //pour debug
    public static final String MAJ_BDD = "com.technoprimates.proofdemo.MAJ_BDD"; // pour broadcast interne à l'appli

    public static final String URL_DOWNLOAD_PROOF =
            "http://192.168.1.25/get_proof.php";

    public static final String URL_UPLOAD_DEMANDE =
            "http://192.168.1.25/request_proof.php";

    public static final String URL_SIGNOFF_PROOF =
            "http://192.168.1.25/signoff_proof.php";

    public static final String DIRECTORY_LOCAL =
            "/DigitProof/";

    public static final String SERVICE_IDBDD = "idbdd";
    public static final String SERVICE_FILENAME = "filename";
    public static final String SERVICE_RECEIVER = "receiver";
    public static final String SERVICE_RESULT_VALUE = "resultValue";
    public static final String PARCEL_PROOF_REQUEST = "proofRequest";

    // Valeur de SERVICE_IDBDD pour traitement de tous les idbdd
    public static final int IDBDD_ALL = -1;

    // Codes retours pour les services
    public static final int HASH_SUCCESS = 1;
    public static final int HASH_FAILED = 2;
    public static final int UPLOAD_SUCCESS = 3;
    public static final int UPLOAD_FAILED = 4;
    public static final int DOWNLOAD_SUCCESS = 5;
    public static final int DOWNLOAD_FAILED = 6;
    public static final int DOWNLOAD_ALREADY_OK = 7;

    // Codes retours pour la MAJ de preuve en SQLite
    public static final int UPDATE_OK = 1;
    public static final int WARNING_PROOF_ALREADY_OK = 2;
    public static final int ERREUR_REQUEST_NOT_FOUND = 3;
    public static final int ERREUR_UPDATE_FAILED = 4;


    public static String sUserId;         // UserId different pour chaque device

    // Base de données
    public static final int VERSION_BDD = 1;
    public static final String NOM_BDD = "jcnews.db";
    public static final String TABLE_REQUEST = "table_proof";

    // Colonnes de tables de données
    public static final int OBJET_NUM_COL_ID = 0;
    public static final int OBJET_NUM_COL_FILENAME = 1;
    public static final int OBJET_NUM_COL_HASH = 2;
    public static final int OBJET_NUM_COL_TREE = 3;
    public static final int OBJET_NUM_COL_TXID = 4;
    public static final int OBJET_NUM_COL_INFO = 5;
    public static final int OBJET_NUM_COL_STATUT = 6;
    public static final int OBJET_NUM_COL_DATE_DEMANDE = 7;
    public static final String OBJET_COL_ID = "_id";
    public static final String OBJET_COL_FILENAME = "filename";
    public static final String OBJET_COL_HASH = "hash";
    public static final String OBJET_COL_DATE_DEMANDE = "datedem";
    public static final String OBJET_COL_TREE = "tree";
    public static final String OBJET_COL_TXID = "txid";
    public static final String OBJET_COL_INFO = "info";
    public static final String OBJET_COL_STATUT = "statut";

    public static final String RETOUR_SERVEUR_COL_DEMANDE = "request";
    public static final String RETOUR_SERVEUR_COL_STATUT = "status";
    public static final String RETOUR_SERVEUR_COL_TREE = "tree";
    public static final String RETOUR_SERVEUR_COL_TXID = "txid";
    public static final String RETOUR_SERVEUR_COL_INFO = "info";

    // ID transitoire d'un nouvel OBJET avant l'insertion Autoincrement
    public static final int OBJET_NOID = -1;

    // Conditions de statut pour les recherches
    public static final int STATUS_ERROR = -1;
    public static final int STATUS_DELETED = -2;
    public static final int STATUS_INITIALIZED = 0;
    public static final int STATUS_HASH_OK = 1;
    public static final int STATUS_SUBMITTED = 2;
    public static final int STATUS_READY = 4;
    public static final int STATUS_FINISHED_OK = 5;
    public static final int STATUS_ALL = 6;


    public static String getLibelleStatut(int statut){
        if (statut == STATUS_INITIALIZED) return Globals.context.getResources().getString(R.string.statut_init);
        if (statut == STATUS_HASH_OK) return Globals.context.getResources().getString(R.string.statut_hash);
        if (statut == STATUS_SUBMITTED) return Globals.context.getResources().getString(R.string.statut_submit);
        if (statut == STATUS_FINISHED_OK) return Globals.context.getResources().getString(R.string.statut_ok);
        if (statut == STATUS_DELETED) return Globals.context.getResources().getString(R.string.statut_erreur);
        if (statut == STATUS_ERROR) return Globals.context.getResources().getString(R.string.statut_suppr);
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this.getApplicationContext();
        sUserId = Build.SERIAL;
        if (sUserId.length()!=16) sUserId = "0123456789abcdef"; // emulateur
        Log.d(Globals.TAG, "UserId: " + sUserId);
    }
}