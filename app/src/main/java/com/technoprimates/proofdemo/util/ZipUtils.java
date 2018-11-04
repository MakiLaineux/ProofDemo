package com.technoprimates.proofdemo.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentCatalog;
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    private static final int BUFFER = 2048;

    // Create proof file (zip form)
    protected static boolean createZipProofFile(Context context, String displayName, String proofString, int requestNumber){
        byte data[] = new byte[BUFFER];

        try {
            File sourceFile = new File(context.getFilesDir(), String.format(Locale.US, "%04d", requestNumber));
            FileInputStream in = new FileInputStream(sourceFile);
            BufferedInputStream origin = new BufferedInputStream(in, BUFFER);
            String zipName = displayName + String.format(Locale.US, ".%04d", requestNumber) + ".zip";
            // Prepare Zip file
            FileOutputStream dest = new FileOutputStream(Environment.getExternalStorageDirectory() + Constants.DIRECTORY_LOCAL + zipName);
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));

            // Add source file to createZip
            ZipEntry entrySource = new ZipEntry(displayName);
            out.putNextEntry(entrySource);
            int count;
            while ((count = origin.read(data, 0, BUFFER)) != -1) {
                out.write(data, 0, count);
            }
            origin.close();

            // Add proof file to Zip,  entry name is always proof.txt
            String label = "proof.txt";
            Log.d(Constants.TAG, "Adding proof: " + label);
            ZipEntry entryProof = new ZipEntry(label);
            out.putNextEntry(entryProof);

            // Write proof text in JSON format
            byte[] tmpBytes = proofString.getBytes();
            out.write(tmpBytes, 0, tmpBytes.length);
            out.close();
            return true;

        } catch (IOException e){
            Log.e(Constants.TAG, "IO Exception while creating proof file (zip form) : " + e);
            e.printStackTrace();
        }
        return false;
    }

    // Extract proof from zip proof file
    protected static String readProofFromProofFile(String zipName){
        InputStream is;
        ZipInputStream zis;
        String entryname;

        try {
            // open zip file
            is = new FileInputStream(Environment.getExternalStorageDirectory()+Constants.DIRECTORY_LOCAL+zipName);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[4096];
            int count;

            while ((ze = zis.getNextEntry()) != null) {
                // loop until matching the zip entry name "proof.txt"
                entryname = ze.getName();
                if (!entryname.equals("proof.txt")) {
                    zis.closeEntry();
                    continue;
                }
                count = zis.read(buffer);
                if (count == -1) {
                    Log.e(Constants.TAG, "zip entry : no bytes read");
                    return null;
                }
                zis.closeEntry();
            }
            is.close();
            zis.close();
            return new String(buffer, "UTF-8");
        } catch (FileNotFoundException e) {
            Log.e(Constants.TAG, "read proof from filename : File not found");
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            Log.e(Constants.TAG, "read proof from filename : IO Exception");
            e.printStackTrace();
            return null;
        }
    }


    //Extract ready-to-hash file from Proof file
    protected static boolean saveFileToHash(Context context, String zipName, String tmpString){
        InputStream is;
        ZipInputStream zis;
        String entryname;
        ZipEntry ze;
        byte[] buffer = new byte[4096];
        int count;

        try {
        // open zip file
            File tmpFile = new File(context.getFilesDir(), tmpString);
            FileOutputStream fout = new FileOutputStream(tmpFile);
            is = new FileInputStream(Environment.getExternalStorageDirectory()+Constants.DIRECTORY_LOCAL+zipName);
            zis = new ZipInputStream(new BufferedInputStream(is));

            // loop to search "proof.txt" entry
            while ((ze = zis.getNextEntry()) != null) {
                entryname = ze.getName();
                if (entryname.equals("proof.txt")) {
                    zis.closeEntry();
                    continue;
                }
                while ((count = zis.read(buffer, 0, 4096)) != -1) {
                    fout.write(buffer, 0, count);
                }
                fout.close();
                zis.closeEntry();
            }
            zis.close();
            return true;

        } catch (IOException e) {
            Log.e(Constants.TAG, "zip entry : IO Exception");
            e.printStackTrace();
            return false;
        }
    }
}
