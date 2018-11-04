package com.technoprimates.proofdemo.util;

import android.os.Environment;
import android.util.Log;

import java.io.File;

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
}