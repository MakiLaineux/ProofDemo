package com.technoprimates.proofdemo.struct;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.technoprimates.proofdemo.util.Constants;
import com.technoprimates.proofdemo.util.ProofError;
import com.technoprimates.proofdemo.util.ProofException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipStampFile extends StampFile {


    public ZipStampFile(Context context, Uri uri) {
        super(context, uri);
    }

    public ZipStampFile(Context context, int dbId, String fileName, int fileType) {
        super(context, dbId, fileName, fileType);
    }


    /**
     * Writes a Stamped File in the app's output directory on External Storage
     * @param statement Statement string to add in the stamped file
     * @throws ProofException if write failed
     */
    @Override
    public void write(String statement) throws ProofException {
        final int BUFFER = 2048;
        byte data[] = new byte[BUFFER];

        try {
            File sourceFile = new File(mContext.getFilesDir(), mDraftName);
            FileInputStream in = new FileInputStream(sourceFile);
            BufferedInputStream origin = new BufferedInputStream(in, BUFFER);
            String zipName = mFileName + mDraftName + ".zip";
            // Prepare Zip file
            FileOutputStream dest = new FileOutputStream(Environment.getExternalStorageDirectory() + Constants.DIRECTORY_LOCAL + zipName);
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));

            // Add source file to createZip
            ZipEntry entrySource = new ZipEntry(mFileName);
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
            byte[] tmpBytes = statement.getBytes();
            out.write(tmpBytes, 0, tmpBytes.length);
            out.close();
            return ;

        } catch (IOException e){
            throw new ProofException(ProofError.ERROR_CREATE_ZIPFILE_FAILED);
        }
    }

    /**
     * Gets the statement stored in a file.
     * @return The statement stored in the file or null if no statement was found
     * @throws ProofException if failed to find or open zip file
     */
    @Override
    public String getStatementString() throws ProofException {
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
    public void writeDraft(String draftName) throws ProofException {
        InputStream is;
        ZipInputStream zis;
        String entryname;
        ZipEntry ze;
        byte[] buffer = new byte[4096];
        int count;
        mDraftName = draftName;


        try {
        // open zip file
            File tmpFile = new File(mContext.getFilesDir(), mDraftName);
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
