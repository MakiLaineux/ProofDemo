package com.technoprimates.proofdemo.struct;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import static com.technoprimates.proofdemo.util.Constants.*;
import com.technoprimates.proofdemo.util.ProofError;
import com.technoprimates.proofdemo.util.ProofException;
import com.technoprimates.proofdemo.util.ProofUtils;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * This abstract class represents a file processed by the app
 *
 * @author Jean Charbonnel
 * @version 1.0
 */
public abstract class StampFile {
    private static final int BUFFER = 2048;
    Context mContext;
    Uri mUri;
    String mDraftName = null; //name of the draft file or null
    String mFileName = null; // name of the original file without path


    /**
     * Public constructor called by subclasses
     * @param context
     * @param uri
     */
    public StampFile(Context context, Uri uri) {
        this.mUri = uri;
        this.mContext = context;

        // set the file's name
        String name = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (name == null) {
            name = uri.getPath();
            int cut = name.lastIndexOf('/');
            if (cut != -1) {
                name = name.substring(cut + 1);
            }
        }
        this.mFileName = name;

    }

    /**
     * Public constructor called by subclasses
     * @param context
     * @param dbId The id of a request record
     * @param fileName The original file's name as displayed to the user
     * @param fileType The proof file variant, use VARIANT_PDF or VARIANT_ZIP
     */
    public StampFile(Context context, int dbId, String fileName, int fileType) {
        this.mUri = null;
        this.mFileName = fileName;
        this.mContext = context;
        this.mDraftName = String.format("%04d", dbId);
    }

    // Static methods

    /**
     * Sets a new StampFile object associated with an actual file.
     * @param uriSource The uri of an actual file that must be openable using the Content Resolver
     * @return An object of a subclass of StampFile
     * @throws ProofException if uri cannot be opened or IO error happened
     */
    public static StampFile set(Context context, Uri uriSource) throws ProofException {
        byte[] magic = new byte[4];
        final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46};
        InputStream in;

        // PDF : file type is pdf and file is not encrypted
        // ZIP : default type
        try {
            in = context.getContentResolver().openInputStream(uriSource);
            in.read(magic, 0, 4);
            in.close();
        } catch (IOException e) {
            throw new ProofException(ProofError.ERROR_CANNOT_OPEN_URI);
        }
        if (!Arrays.equals(magic, PDF_MAGIC)){ // not a pdf
            return new ZipStampFile(context, uriSource);
        }
        // check if encrypted
        PDFBoxResourceLoader.init(context);
        try {
            PDDocument document = PDDocument.load(context.getContentResolver().openInputStream(uriSource));
            if (document.isEncrypted()) {
                document.close();
                return new ZipStampFile(context, uriSource);
            } else {
                document.close();
                return new PdfStampFile(context, uriSource);
            }
        } catch (IOException e) {
            throw new ProofException(ProofError.ERROR_CANNOT_LOAD_PDF);
        }
    }


    /**
     * Initializes a new StampFile object from a Request record.
     * @param dbId The id of the request in the local database. This will be used to build the name of the stamped file
     * @param fileName The name without path of the original file. This will be used to build the name of the stamped file
     * @param fileType The proof file variant, use VARIANT_PDF or VARIANT_ZIP
     * @return An object of a subclass of StampFile
     * @throws ProofException if uri cannot be opened or IO error happened
     */
    public static StampFile init(Context context, int dbId, String fileName, int fileType) throws ProofException {
        switch (fileType){
            case VARIANT_PDF:
                return new PdfStampFile(context, dbId, fileName, fileType);
            case VARIANT_ZIP:
                return new ZipStampFile(context, dbId, fileName, fileType);
            default:
                throw new ProofException(ProofError.ERROR_UNKNOWN_FILETYPE);
        }
    }




    /**
     * Writes a Stamped File with proof metadata in the app's output directory on External Storage.
     * The content of the stamped file is taken from a draft file previously saved in app's private data. This
     * draft file is deleted if the writing succeeds.
     * A proof statement is added to the metadata of the stamped file.
     * @param statement Statement string to add in the stamped file
     * @throws ProofException if write failed
     */
    public abstract void write(String statement) throws ProofException;

    /**
     * Gets the statement stored in a stamped file.
     * @return A JSON string containing the statement stored in the file,
     * @throws ProofException
     */
    public abstract String getStatementString()throws ProofException;


    /**
     * get the Proof file type
      * @return VARIANT_PDF or VARIANT_ZIP
     */
    public abstract int typeOf();

    /**
     * Saves the file's content to a draft file in the app's private storage. This draft
     * file will later be used to write the stamped file when the proof data will be received from the server
     * @throws ProofException
     */
    public abstract void writeDraft(String draftName, boolean originalFile)throws ProofException;


    /**
     * Computes the SHA-256 hash of the file with empty proof metadata. This uses a draft file
     * copied form the original file, but with proof metadata emptied or empty proof metadata added.
     * @return The hash of the file with empty proof
     * @throws ProofException if draft file is not found, or if a hash exception occurred
     */
    public String getHash() throws ProofException{
        String hash;
        if (mDraftName == null){
            throw new ProofException(ProofError.ERROR_NO_FILE_TO_HASH);
        }
        try {
            File sourceFile = new File(mContext.getFilesDir(), mDraftName);
            InputStream in = new FileInputStream(sourceFile);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] buf = new byte[1024];
            int bytesRead;

            while ((bytesRead = in.read(buf)) != -1) {
                digest.update(buf, 0, bytesRead);
            }

            byte[] hashedBytes = digest.digest();

            hash = ProofUtils.convertByteArrayToHexString(hashedBytes);
            in.close();
            return hash;

        } catch (NoSuchAlgorithmException e) {
            throw new ProofException(ProofError.ERROR_UNKNOWN_HASH_ALGORITHM);

        } catch (IOException e) {
            throw new ProofException(ProofError.ERROR_COMPUTE_HASH);
        }
    }

    public String getFileName() {
        return mFileName;
    }

    /**
     * Deletes the draft file
      * @throws ProofException if deletion failed
     */
    public void eraseDraft() throws ProofException {
        File draftFile = new File(mContext.getFilesDir(), mDraftName);
        Boolean del = draftFile.delete();
        if (!del)
            throw new ProofException(ProofError.ERROR_DELETE_TEMP_FILE_FAILED);
    }

    /**
     * Gets the name of the stamped file given request id and original file name
     *
     * @param requestId
     * @param displayName
     * @param fileType The proof file variant, use VARIANT_PDF or VARIANT_ZIP
     * @return A string representing the name of the stamped file in the app's storage directory
     * @throws ProofException
     */
    public static String getName(int requestId, String displayName, int fileType) throws ProofException {
        switch (fileType){
            case VARIANT_PDF:
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

            case VARIANT_ZIP:
                return (displayName
                        + "."
                        + String.format("%04d", requestId)
                        + ".zip");
            default:
                throw new ProofException(ProofError.ERROR_UNKNOWN_FILETYPE);
        }
    }


}