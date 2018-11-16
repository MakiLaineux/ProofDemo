package com.technoprimates.proofdemo.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public abstract class ProofFile {
    private static final int BUFFER = 2048;

    Context mContext;
    Uri mUri;

    // Static methods

    // Build a ProofFile object
    public static ProofFile set(Context context, Uri uriSource) throws ProofException {
        byte[] magic = new byte[4];
        final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46};
        InputStream in;

        // Check if pdf file
        try {
            in = context.getContentResolver().openInputStream(uriSource);
            in.read(magic, 0, 4);
            in.close();
        } catch (IOException e) {
            throw new ProofException(ProofError.ERROR_CANNOT_OPEN_URI);
        }
        if (!Arrays.equals(magic, PDF_MAGIC)){ // not a pdf
            return new ZipProofFile(context, uriSource);
        }
        // check if encrypted
        PDFBoxResourceLoader.init(context);
        try {
            PDDocument document = PDDocument.load(context.getContentResolver().openInputStream(uriSource));
            if (document.isEncrypted()) {
                document.close();
                return new ZipProofFile(context, uriSource);
            } else {
                document.close();
                return new PdfProofFile(context, uriSource);
            }
        } catch (IOException e) {
            throw new ProofException(ProofError.ERROR_CANNOT_LOAD_PDF);
        }
    }

    // Get the name of the proof file given request id and original file name
    public static String getProofFileName(int requestId, String displayName, int status) throws ProofException {
        switch (status){
            case Constants.STATUS_FINISHED_PDF:
                String fileNameWithoutExtsension;
                // Strip Filename's extension if exists
                if (displayName.lastIndexOf(".pdf") == displayName.length()-4){ // filename ends with ".pdf"
                    fileNameWithoutExtsension = displayName.substring(0, displayName.length()-4);
                } else {
                    fileNameWithoutExtsension = displayName;
                }
                return (fileNameWithoutExtsension
                        + "."
                        + String.format("%04d", requestId)
                        + ".pdf");

            case Constants.STATUS_FINISHED_ZIP:
                return (displayName
                        + "."
                        + String.format("%04d", requestId)
                        + ".zip");
            default:
                throw new ProofException(ProofError.ERROR_UNKNOWN_FINISHED_STATUS);
        }
    }


    public abstract void writeOnSDCard(String displayName, String proofString, int extensionPrefix) throws ProofException;
    protected abstract String readProof()throws ProofException;
    protected abstract void saveFileToHash()throws ProofException;
    protected abstract int typeOf();
    protected abstract void addProofNeutralMetadata (String fileName)throws ProofException;

    /*
     * This method makes a "copy" of the original file in app storage space. The proof file will later be built by joining
     * this saved file and the proof data received from the server
     * If the original file accepts metadata (at this point : if the file is a non-encrypted pdf), we embed in the saved file neutral proof metadata. Therefore,
     * it is not in this case an exact copy of the original file.
     * There is no certainty that the original file will still be available when the proof is received by the server.
     * Moreover, if the original file was to be modified between the request's creation and the reception of the proof,
     * the file's hash would also be modified and the proof wouldn't be valid. Therefore, it is necessary to dispose
     * of a "copy" of the original file.
     * The saved "copy" is named by the request number. It will later be deleted when the proof file is built
     *
     */
    public boolean saveFileContentToAppData(String internalName) throws ProofException {
        byte[] buf = new byte[BUFFER];
        Log.d(Constants.TAG, "          internalName    : " + internalName + ", namesource : " + mUri.toString());

        try {
            // Build a proofFile object
            ProofFile proofFile = set(mContext, mUri);

            // makes the copy, this copy will later be modified if files accepts metadata
            InputStream in = mContext.getContentResolver().openInputStream(mUri);
            File destFile = new File(mContext.getFilesDir(), internalName);
            OutputStream out = new FileOutputStream(destFile);

            // Transfer bytes from in to out
            int len;

            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();

            // TODO : short metadata
            // if proof metadata already exists, overwrite it with neutral proof metadata
            proofFile.addProofNeutralMetadata(internalName);
        } catch (IOException e) {
            throw new ProofException(ProofError.ERROR_APPDATA_SAVE_FAILED);
        }
        return true;
    }


    public String computeDocumentHash() throws ProofException {
        String hash;
        // First step : prepare a tmp file to hash
        saveFileToHash();
        // Second step, compute the hash
        hash = ProofOperations.computeHashFromFile(mContext, "tmpfile");
        if (hash == null){
            throw new ProofException(ProofError.ERROR_COMPUTE_HASH);
        }
        // Third part, delete the temp file that was created
        File tmpFile = new File(mContext.getFilesDir(), "tmpfile");
        Boolean del = tmpFile.delete();
        if (!del)
            throw new ProofException(ProofError.ERROR_DELETE_TEMP_FILE_FAILED);
        return hash;
    }
}