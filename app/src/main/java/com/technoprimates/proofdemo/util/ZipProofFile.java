package com.technoprimates.proofdemo.util;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

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

public class ZipProofFile extends ProofFile {


    ZipProofFile(Context context, Uri uri) {
        this.mUri = uri;
        this.mContext = context;
    }

    @Override
    protected int typeOf() {
        return Constants.VARIANT_ZIP;
    }

    @Override
    protected void addProofNeutralMetadata(String fileName) {
        return;
    }


    // Create proof file (zip form)
    @Override
    public void writeOnSDCard(String displayName, String proofString, int extensionPrefix) throws ProofException {
        final int BUFFER = 2048;
        byte data[] = new byte[BUFFER];

        try {
            File sourceFile = new File(mContext.getFilesDir(), String.format(Locale.US, "%04d", extensionPrefix));
            FileInputStream in = new FileInputStream(sourceFile);
            BufferedInputStream origin = new BufferedInputStream(in, BUFFER);
            String zipName = displayName + String.format(Locale.US, ".%04d", extensionPrefix) + ".zip";
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
            return ;

        } catch (IOException e){
            throw new ProofException(ProofError.ERROR_CREATE_ZIPFILE_FAILED);
        }
    }

    // Extract proof from zip proof file
    @Override
    protected String readProof() throws ProofException {
        InputStream is;
        ZipInputStream zis;
        String entryname;

        try {
            // open zip file
            is = mContext.getContentResolver().openInputStream(mUri);
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
                    throw new ProofException(ProofError.ERROR_READING_PROOF_TEXT);
                }
                zis.closeEntry();
            }
            is.close();
            zis.close();
            return new String(buffer, "UTF-8");
        } catch (FileNotFoundException e) {
            throw new ProofException(ProofError.ERROR_ZIPFILE_NOT_FOUND);
        } catch (IOException e) {
            throw new ProofException(ProofError.ERROR_ZIPFILE_IO_ERROR);
        }
    }


    //Extract ready-to-hash file from Proof file
    @Override
    protected void saveFileToHash() throws ProofException {
        InputStream is;
        ZipInputStream zis;
        String entryname;
        ZipEntry ze;
        byte[] buffer = new byte[4096];
        int count;

        try {
        // open zip file
            File tmpFile = new File(mContext.getFilesDir(), "tmpfile");
            FileOutputStream fout = new FileOutputStream(tmpFile);

            InputStream in = mContext.getContentResolver().openInputStream(mUri);
            zis = new ZipInputStream(new BufferedInputStream(in));

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
            return;

        } catch (IOException e) {
            throw new ProofException(ProofError.ERROR_SAVEFILETOHASH_FAILED);
        }
    }

}
