package com.technoprimates.proofdemo.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.technoprimates.proofdemo.util.Globals;

/**
 * Created by MAKI LAINEUX on 05/09/2016.
 */

public class DatabaseHandler extends SQLiteOpenHelper {

    private static DatabaseHandler sInstance;

    public static synchronized DatabaseHandler getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DatabaseHandler(context.getApplicationContext());
        }
        return sInstance;
    }

    private DatabaseHandler(Context context) {
        super(context, Globals.NOM_BDD, null, Globals.VERSION_BDD);
    }

    // Creating Tables
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_REQUESTS_TABLE = "CREATE TABLE " + Globals.TABLE_REQUEST + "("
                + Globals.OBJET_COL_ID + " INTEGER PRIMARY KEY,"
                + Globals.OBJET_COL_CHEMIN + " TEXT,"
                + Globals.OBJET_COL_HASH + " TEXT,"
                + Globals.OBJET_COL_TREE + " TEXT,"
                + Globals.OBJET_COL_TXID + " TEXT,"
                + Globals.OBJET_COL_INFO + " TEXT,"
                + Globals.OBJET_COL_STATUT + " INTEGER,"
                + Globals.OBJET_COL_DATE_DEMANDE + " TEXT"+ ")";
        db.execSQL(CREATE_REQUESTS_TABLE);
    }

    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + Globals.TABLE_REQUEST);
        // Create tables again
        onCreate(db);
    }

    // Requête d'insertion en BDD locale,
    // On ne fournit pas OBJET_COL_ID pour forcer l'autoincrementation par sqlite
    public long insertProofRequest(ProofRequest proofRequest) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Globals.OBJET_COL_CHEMIN, proofRequest.get_chemin());
        values.put(Globals.OBJET_COL_HASH, proofRequest.get_hash());
        values.put(Globals.OBJET_COL_TREE, proofRequest.get_tree());
        values.put(Globals.OBJET_COL_TXID, proofRequest.get_txid());
        values.put(Globals.OBJET_COL_INFO, proofRequest.get_info());
        values.put(Globals.OBJET_COL_STATUT, proofRequest.get_statut());
        values.put(Globals.OBJET_COL_DATE_DEMANDE, proofRequest.get_date_request());

        // Inserting Row
        long id = db.insert(Globals.TABLE_REQUEST, null, values);
        return id;
    }

    public ProofRequest getOneProofRequest(int id) {
        Cursor c= getOneCursorProofRequest(id);
        if ((c==null) || c.getCount() ==0) return null;
        c.moveToFirst();
        ProofRequest p = new ProofRequest(
                c.getInt(Globals.OBJET_NUM_COL_ID),
                c.getString(Globals.OBJET_NUM_COL_CHEMIN),
                c.getString(Globals.OBJET_NUM_COL_HASH),
                c.getInt(Globals.OBJET_NUM_COL_STATUT),
                c.getString(Globals.OBJET_NUM_COL_TREE),
                c.getString(Globals.OBJET_NUM_COL_TXID),
                c.getString(Globals.OBJET_NUM_COL_INFO),
                c.getString(Globals.OBJET_NUM_COL_DATE_DEMANDE));
        c.close();
        return p;
    }

    public Cursor getOneCursorProofRequest(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        String where = Globals.OBJET_COL_ID+ " = \"" + id + "\"";
        Cursor c= db.query(Globals.TABLE_REQUEST, new String[]{
                        Globals.OBJET_COL_ID,
                        Globals.OBJET_COL_CHEMIN,
                        Globals.OBJET_COL_HASH,
                        Globals.OBJET_COL_TREE,
                        Globals.OBJET_COL_TXID,
                        Globals.OBJET_COL_INFO,
                        Globals.OBJET_COL_STATUT,
                        Globals.OBJET_COL_DATE_DEMANDE,
                },
                where, null, null, null, null);
        return c;
    }

    public Cursor getAllProofRequests(int statut) {
        String where = null;
        SQLiteDatabase db = this.getReadableDatabase();
        if (statut != Globals.STATUS_ALL)
            where = Globals.OBJET_COL_STATUT + " = " + statut;
        Cursor c = db.query(Globals.TABLE_REQUEST, new String[]{
                        Globals.OBJET_COL_ID,
                        Globals.OBJET_COL_CHEMIN,
                        Globals.OBJET_COL_HASH,
                        Globals.OBJET_COL_TREE,
                        Globals.OBJET_COL_TXID,
                        Globals.OBJET_COL_INFO,
                        Globals.OBJET_COL_STATUT,
                        Globals.OBJET_COL_DATE_DEMANDE,
                },
                where, null, null, null, Globals.OBJET_COL_ID + " DESC");
        return c;
    }

    public int updateStatutProofRequest(int id, int statut) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = getOneCursorProofRequest(id);
        c.moveToFirst();
        ContentValues values = new ContentValues();
        values.put(Globals.OBJET_COL_STATUT, statut);
        int result = db.update(Globals.TABLE_REQUEST, values, Globals.OBJET_COL_ID + " = " + id, null);
        c.close();
        return result;
    }
    public int updateProofRequestFromReponseServeur(int id, int statut, String tree, String txid, String info) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = getOneCursorProofRequest(id);
        c.moveToFirst();
        ContentValues values = new ContentValues();
        values.put(Globals.OBJET_COL_STATUT, statut);
        values.put(Globals.OBJET_COL_TREE, tree);
        values.put(Globals.OBJET_COL_TXID, txid);
        values.put(Globals.OBJET_COL_INFO, info);
        int result = db.update(Globals.TABLE_REQUEST, values, Globals.OBJET_COL_ID + " = " + id, null);
        c.close();
        return result;
    }
    public int updateHashProofRequest(int id, String hash) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = getOneCursorProofRequest(id);
        c.moveToFirst();
        ContentValues values = new ContentValues();
        values.put(Globals.OBJET_COL_HASH, hash);
        int result = db.update(Globals.TABLE_REQUEST, values, Globals.OBJET_COL_ID + " = " + id, null);
        c.close();
        return result;
    }
}
