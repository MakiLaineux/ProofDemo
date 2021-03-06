package com.technoprimates.proofdemo.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.technoprimates.proofdemo.struct.ProofRequest;
import com.technoprimates.proofdemo.util.Constants;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

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
        super(context, Constants.DB_NAME, null, Constants.DB_VERSION);
    }

    // Creating Tables
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_REQUESTS_TABLE = "CREATE TABLE " + Constants.TABLE_REQUEST + "("
                + Constants.REQUEST_COL_ID + " INTEGER PRIMARY KEY,"
                + Constants.REQUEST_COL_FILENAME + " TEXT,"
                + Constants.REQUEST_COL_HASH + " TEXT,"
                + Constants.REQUEST_COL_TREE + " TEXT,"
                + Constants.REQUEST_COL_TXID + " TEXT,"
                + Constants.REQUEST_COL_INFO + " TEXT,"
                + Constants.REQUEST_COL_STATUS + " INTEGER,"
                + Constants.REQUEST_COL_REQUEST_DATE + " TEXT"+ ")";
        db.execSQL(CREATE_REQUESTS_TABLE);
    }

    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + Constants.TABLE_REQUEST);
        // Create tables again
        onCreate(db);
    }


    public long insertProofRequest(String fileName) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.REQUEST_COL_FILENAME, fileName);
        values.put(Constants.REQUEST_COL_HASH, "N/A");
        values.put(Constants.REQUEST_COL_TREE, "N/A");
        values.put(Constants.REQUEST_COL_TXID, "N/A");
        values.put(Constants.REQUEST_COL_INFO, "N/A");
        values.put(Constants.REQUEST_COL_STATUS, Constants.STATUS_INITIALIZED);

        // get request date
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());
        String date = df.format(c.getTime());
        values.put(Constants.REQUEST_COL_REQUEST_DATE, date);

        // Inserting Row
        return db.insert(Constants.TABLE_REQUEST, null, values);
    }

    public ProofRequest getOneProofRequest(int id) {
        Cursor c= getOneCursorProofRequest(id);
        if ((c==null) || c.getCount() ==0) return null;
        c.moveToFirst();
        ProofRequest p = new ProofRequest(
                c.getInt(Constants.REQUEST_NUM_COL_ID),
                c.getString(Constants.REQUEST_NUM_COL_FILENAME),
                c.getString(Constants.REQUEST_NUM_COL_HASH),
                c.getInt(Constants.REQUEST_NUM_COL_STATUS),
                c.getString(Constants.REQUEST_NUM_COL_TREE),
                c.getString(Constants.REQUEST_NUM_COL_TXID),
                c.getString(Constants.REQUEST_NUM_COL_INFO),
                c.getString(Constants.REQUEST_NUM_COL_REQUEST_DATE));
        c.close();
        return p;
    }

    public Cursor getOneCursorProofRequest(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        String where = Constants.REQUEST_COL_ID + " = \"" + id + "\"";
        return db.query(Constants.TABLE_REQUEST, new String[]{
                        Constants.REQUEST_COL_ID,
                        Constants.REQUEST_COL_FILENAME,
                        Constants.REQUEST_COL_HASH,
                        Constants.REQUEST_COL_TREE,
                        Constants.REQUEST_COL_TXID,
                        Constants.REQUEST_COL_INFO,
                        Constants.REQUEST_COL_STATUS,
                        Constants.REQUEST_COL_REQUEST_DATE,
                },
                where, null, null, null, null);
    }

    public Cursor getAllProofRequests(int status) {
        String where = null;
        SQLiteDatabase db = this.getReadableDatabase();
        switch (status){
            case Constants.STATUS_ALL :  // no where clause
                break;
            case Constants.STATUS_FINISHED_ALL:  // where clause with any finished status
                where = Constants.REQUEST_COL_STATUS + " = " + Constants.STATUS_FINISHED_PDF +
                        " OR "+
                        Constants.REQUEST_COL_STATUS + " = " + Constants.STATUS_FINISHED_ZIP ;
                break;
            default: // xhere clause with specified status
                where = Constants.REQUEST_COL_STATUS + " = " + status;
                break;
        }
        return db.query(Constants.TABLE_REQUEST, new String[]{
                        Constants.REQUEST_COL_ID,
                        Constants.REQUEST_COL_FILENAME,
                        Constants.REQUEST_COL_HASH,
                        Constants.REQUEST_COL_TREE,
                        Constants.REQUEST_COL_TXID,
                        Constants.REQUEST_COL_INFO,
                        Constants.REQUEST_COL_STATUS,
                        Constants.REQUEST_COL_REQUEST_DATE,
                },
                where, null, null, null, Constants.REQUEST_COL_ID + " DESC");
    }

    public int updateStatutProofRequest(int id, int statut) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = getOneCursorProofRequest(id);
        c.moveToFirst();
        ContentValues values = new ContentValues();
        values.put(Constants.REQUEST_COL_STATUS, statut);
        int nbRowsAffected = db.update(Constants.TABLE_REQUEST, values, Constants.REQUEST_COL_ID + " = " + id, null);
        c.close();
        if (nbRowsAffected == 1)
            return Constants.RETURN_DBUPDATE_OK;
        else
            return Constants.RETURN_DBUPDATE_KO;
    }

    public int updateProofRequestFromReponseServeur(int id, int statut, String tree, String txid, String info) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = getOneCursorProofRequest(id);
        c.moveToFirst();
        ContentValues values = new ContentValues();
        values.put(Constants.REQUEST_COL_STATUS, statut);
        values.put(Constants.REQUEST_COL_TREE, tree);
        values.put(Constants.REQUEST_COL_TXID, txid);
        values.put(Constants.REQUEST_COL_INFO, info);
        int nbRowsAffected = db.update(Constants.TABLE_REQUEST, values, Constants.REQUEST_COL_ID + " = " + id, null);
        c.close();
        if (nbRowsAffected == 1)
            return Constants.RETURN_DBUPDATE_OK;
        else
            return Constants.RETURN_DBUPDATE_KO;
    }
    public int updateHashProofRequest(int id, String hash) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = getOneCursorProofRequest(id);
        c.moveToFirst();
        ContentValues values = new ContentValues();
        values.put(Constants.REQUEST_COL_STATUS, Constants.STATUS_HASH_OK);
        values.put(Constants.REQUEST_COL_HASH, hash);
        int nbRowsAffected = db.update(Constants.TABLE_REQUEST, values, Constants.REQUEST_COL_ID + " = " + id, null);
        c.close();
        if (nbRowsAffected == 1)
            return Constants.RETURN_DBUPDATE_OK;
        else
            return Constants.RETURN_DBUPDATE_KO;
    }
}
