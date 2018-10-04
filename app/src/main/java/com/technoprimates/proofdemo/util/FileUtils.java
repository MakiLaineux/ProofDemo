package com.technoprimates.proofdemo.util;

/**
 * Created by MAKI LAINEUX on 21/08/2016.
 */
import android.os.Environment;
import android.util.Log;

import com.technoprimates.proofdemo.struct.RetourServeur;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * File management utility class.
 */
public class FileUtils {

    private static final int BUFFER = 2048;

    private FileUtils() {
    }

    // Create zip file on SDCard and remove tmp file in internal directory
    public static void createZip(File sourceFile, String nameShort, String nameDest, RetourServeur r) throws IOException {
        FileInputStream in = new FileInputStream(sourceFile);
        BufferedInputStream origin = new BufferedInputStream(in, BUFFER);

        // Transfer bytes from in to out
        byte data[] = new byte[BUFFER];

        // Check SDCard Directory
        File dir = new File(Environment.getExternalStorageDirectory() + Globals.DIRECTORY_LOCAL);
        if(!dir.isDirectory()) {
            if (dir.mkdirs())
                Log.d(Globals.TAG, "Création répertoire : "+dir);
            else
                Log.e(Globals.TAG, "Echec création du répertoire : "+dir);
        }
        // Prepare createZip file
        Log.d(Globals.TAG, "Zip ------------------------------ ");
        Log.d(Globals.TAG, "Zip name: " + nameDest);
        FileOutputStream dest = new FileOutputStream(Environment.getExternalStorageDirectory() + Globals.DIRECTORY_LOCAL + nameDest);
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));

        // Add source file to createZip
        Log.d(Globals.TAG, "Adding file: " + nameShort);
        ZipEntry entrySource = new ZipEntry(nameShort);
        out.putNextEntry(entrySource);
        int count;
        while ((count = origin.read(data, 0, BUFFER)) != -1) {
            out.write(data, 0, count);
        }
        origin.close();

        // Add proof file to createZip
        String label = "proof_"+String.format("%04d", r.mDemande)+".txt";
        Log.d(Globals.TAG, "Adding proof: "+label);
        ZipEntry entryProof = new ZipEntry(label);
        out.putNextEntry(entryProof);

        // Write proof text
        byte[] tmpBytes =(r.mTree+"\n\n").getBytes();
        out.write(tmpBytes, 0, tmpBytes.length);
        tmpBytes =("Root hash stored on bitcoin's blockchain, transaction id : "+r.mTxid+"\n\n").getBytes();
        out.write(tmpBytes, 0, tmpBytes.length);
        tmpBytes =("Date of validation (mining) : "+r.mInfo+"\n").getBytes();
        out.write(tmpBytes, 0, tmpBytes.length);
        out.close();

        Boolean del = sourceFile.delete();
        if (del)
            Log.d(Globals.TAG, "internal file successfully deleted");
        else
            Log.e(Globals.TAG, "Error deleting internal file ");

        return;
    }

    // retrieve entry file in zipfile and unzip it into a tmp file
    public static void unpackZipEntry(String zipFileName, String zipEntryName)
    {
        InputStream is;
        ZipInputStream zis;

        try
        {
            // output to tmp file
            File tmpFile = new File(Environment.getExternalStorageDirectory() + Globals.DIRECTORY_LOCAL, "fichier preuve");
            FileOutputStream fout = new FileOutputStream(tmpFile);

            // open zip file
            String filename;
            is = new FileInputStream(zipFileName);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[1024];
            int count;

            while ((ze = zis.getNextEntry()) != null)
            {
                // loop until matching the zip entry name
                filename = ze.getName();
                if (!filename.equals(zipEntryName)){
                    zis.closeEntry();
                    continue;
                }

                // cteni zipu a zapis
                while ((count = zis.read(buffer)) != -1)
                {
                    fout.write(buffer, 0, count);
                }
                fout.close();
                zis.closeEntry();
            }
            zis.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }

    }

    public static String convertByteArrayToHexString(byte[] arrayBytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < arrayBytes.length; i++) {
            stringBuffer.append(Integer.toString((arrayBytes[i] & 0xff) + 0x100, 16)
                    .substring(1));
        }
        return stringBuffer.toString();
    }



}
