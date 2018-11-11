package com.technoprimates.proofdemo.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

// Utility file methods
public class FileUtils {


    // Check SDCard Directory, create if does not exist
    public static boolean checkSDDirectory() {
        File dir = new File(Environment.getExternalStorageDirectory() + Constants.DIRECTORY_LOCAL);
        if (!dir.isDirectory()) {
            if (dir.mkdirs())
                Log.d(Constants.TAG, "Création répertoire : " + dir);
            else {
                Log.e(Constants.TAG, "Echec création du répertoire : " + dir);
                return false;
            }
        }
        return true;
    }

    // checks if pdf variant is to be applied, given an full uri location
    // returns true if file magic is pdf's, and if this pdf is not encrypted
    static protected boolean checkFileVariantFromUri(Context context, Uri uriSource) throws IOException {
        byte[] magic = new byte[4];
        final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46};

        // Check if pdf file
        InputStream in = context.getContentResolver().openInputStream(uriSource);
        in.read(magic, 0, 4);
        in.close();
        if (!Arrays.equals(magic, PDF_MAGIC)){ // not a pdf
            return false;
        }
        // check if encrypted
        PDFBoxResourceLoader.init(context);
        PDDocument document = PDDocument.load(context.getContentResolver().openInputStream(uriSource));
        if (document.isEncrypted()) {
            document.close();
            return false;
        } else {
            document.close();
            return true;
        }
    }

    // Get the user-visible name of the file given it's uri
    public static String getFilename(Context context, Uri uri) {

        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
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