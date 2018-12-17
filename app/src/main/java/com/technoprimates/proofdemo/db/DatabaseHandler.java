package com.technoprimates.proofdemo.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import static com.technoprimates.proofdemo.util.Constants.*;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by JC on 01/12/2018.
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
        super(context, DB_NAME, null, DB_VERSION);
    }

    // Creating Tables
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_REQUESTS_TABLE = "CREATE TABLE " + TABLE_REQUEST + "("
                + REQUEST_COL_ID + " INTEGER PRIMARY KEY,"
                + REQUEST_COL_FILENAME + " TEXT,"
                + REQUEST_COL_FILETYPE + " TEXT,"
                + REQUEST_COL_MESSAGE + " TEXT,"
                + REQUEST_COL_DOC_HASH + " TEXT,"
                + REQUEST_COL_OVER_HASH + " TEXT,"
                + REQUEST_COL_STATUS + " INTEGER,"
                + REQUEST_COL_REQUEST_DATE + " TEXT"+ ");";
        db.execSQL(CREATE_REQUESTS_TABLE);
    }

    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REQUEST);
        onCreate(db);
    }


    public long insertProofRequest(String fileName, String proofMessage, int fileType) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(REQUEST_COL_FILENAME, fileName);
        values.put(REQUEST_COL_FILETYPE, fileType);
        values.put(REQUEST_COL_DOC_HASH, "N/A");
        values.put(REQUEST_COL_OVER_HASH, "N/A");
        values.put(REQUEST_COL_STATUS, STATUS_INITIALIZED);
        values.put(REQUEST_COL_MESSAGE, proofMessage);

        // get request date
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());
        String date = df.format(c.getTime());
        values.put(REQUEST_COL_REQUEST_DATE, date);


        // Inserting Row
        return db.insert(TABLE_REQUEST, null, values);
    }

    public String getDisplayName(int id) {
        Cursor c= getOneCursorProofRequest(id);
        if ((c==null) || c.getCount() ==0) return null;
        c.moveToFirst();
        String displayName = c.getString(REQUEST_NUM_COL_FILENAME);
        c.close();
        return displayName;
    }

    public Cursor getOneCursorProofRequest(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        String where = REQUEST_COL_ID + " = \"" + id + "\"";
        return db.query(TABLE_REQUEST, new String[]{
                        REQUEST_COL_ID,
                        REQUEST_COL_FILENAME,
                        REQUEST_COL_FILETYPE,
                        REQUEST_COL_MESSAGE,
                        REQUEST_COL_DOC_HASH,
                        REQUEST_COL_OVER_HASH,
                        REQUEST_COL_STATUS,
                        REQUEST_COL_REQUEST_DATE,
                },
                where, null, null, null, null);
    }

    public Cursor getAllProofRequests(int status) {
        String where = null;
        SQLiteDatabase db = this.getReadableDatabase();
        switch (status){
            case STATUS_ALL :  // no where clause
                break;
            default: // where clause with specified status
                where = REQUEST_COL_STATUS + " = " + status;
                break;
        }
        return db.query(TABLE_REQUEST, new String[]{
                        REQUEST_COL_ID,
                        REQUEST_COL_FILENAME,
                        REQUEST_COL_FILETYPE,
                        REQUEST_COL_MESSAGE,
                        REQUEST_COL_DOC_HASH,
                        REQUEST_COL_OVER_HASH,
                        REQUEST_COL_STATUS,
                        REQUEST_COL_REQUEST_DATE,
                },
                where, null, null, null, REQUEST_COL_ID + " DESC");
    }

    public int updateStatutProofRequest(int id, int statut) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = getOneCursorProofRequest(id);
        c.moveToFirst();
        ContentValues values = new ContentValues();
        values.put(REQUEST_COL_STATUS, statut);
        int nbRowsAffected = db.update(TABLE_REQUEST, values, REQUEST_COL_ID + " = " + id, null);
        c.close();
        if (nbRowsAffected == 1)
            return RETURN_DBUPDATE_OK;
        else
            return RETURN_DBUPDATE_KO;
    }

    public int updateProofRequestFromReponseServeur(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = getOneCursorProofRequest(id);
        c.moveToFirst();
        ContentValues values = new ContentValues();
        values.put(REQUEST_COL_STATUS, STATUS_FINISHED);
        int nbRowsAffected = db.update(TABLE_REQUEST, values, REQUEST_COL_ID + " = " + id, null);
        c.close();
        if (nbRowsAffected == 1)
            return RETURN_DBUPDATE_OK;
        else
            return RETURN_DBUPDATE_KO;
    }
    public int updateHashProofRequest(int id, String docHash, String overHash) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = getOneCursorProofRequest(id);
        c.moveToFirst();
        ContentValues values = new ContentValues();
        values.put(REQUEST_COL_STATUS, STATUS_HASH_OK);
        values.put(REQUEST_COL_DOC_HASH, docHash);
        values.put(REQUEST_COL_OVER_HASH, overHash);
        int nbRowsAffected = db.update(TABLE_REQUEST, values, REQUEST_COL_ID + " = " + id, null);
        c.close();
        if (nbRowsAffected == 1)
            return RETURN_DBUPDATE_OK;
        else
            return RETURN_DBUPDATE_KO;
    }
}
