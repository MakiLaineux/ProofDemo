package com.technoprimates.proofdemo.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;

//Some file operations not specific to proof files
public class FileUtils {

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

    // Check SDCard Directory, create if does not exist
    static boolean checkSDDirectory() {
        File dir = new File(Environment.getExternalStorageDirectory() + Constants.DIRECTORY_LOCAL);
        if (!dir.isDirectory() && !dir.mkdirs())
            return false;
        else
            return true;
    }
}
