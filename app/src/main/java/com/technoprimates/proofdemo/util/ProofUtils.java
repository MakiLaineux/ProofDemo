package com.technoprimates.proofdemo.util;

/*
 * This class contains various static methods managing files, hashes, proofs.
 * Created by JC, october 2018.
 */
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;

import com.technoprimates.proofdemo.struct.Proof;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class ProofUtils {

    private static final int BUFFER = 2048;

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
    public static boolean saveFileContentToAppData(Context context, String stringUri, String fileName) {
        final Uri uriSource = Uri.parse(stringUri);
        boolean pdfVariant;
        byte[] buf = new byte[BUFFER];
        Log.d(Constants.TAG, "          fileName    : " + fileName + ", namesource : " + uriSource.toString());

        try {
            // check if the file accepts metadata
            pdfVariant = PdfUtils.checkOriginalFileVariant(context, uriSource);

            // makes the copy, this copy will later be modified if files accepts metadata
            InputStream in = context.getContentResolver().openInputStream(uriSource);
            File destFile = new File(context.getFilesDir(), fileName);
            OutputStream out = new FileOutputStream(destFile);

            // Transfer bytes from in to out
            int len;

            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();

            // Case of pdf variant : store neutral proof metadata
            // if proof metadata already exists, overwrite it with neutral proof metadata
            if (pdfVariant){
                Boolean success = PdfUtils.addProofNeutralMetadata(context, fileName);
                if (!success) {
                    Log.e(Constants.TAG, "Adding neutral metadata failed");
                    return false;
                }
            }

            Log.d(Constants.TAG, "Copy to App data ok, copy ok");
        } catch (IOException e) {
            Log.e(Constants.TAG, "Copy to App data failed : " + e);
            e.printStackTrace();
            return false;
        }
        return true;
    }



    // build the proof file
    // returns depends on success and type of proof file created
    public static int buildProofFile(Context context, String displayName, Proof proof) {

        /* About file names
                Proof file name :
        Giving that the user may issue several proof requests for the same file name, it is necessary to distinguish
        the proof files matching each of those requests. Therefore, the proof file built will reflect in its name
        the corresponding request number. It is up to the user to rename the proof file at its convenience, in this case the proof
        would remain valid and checkable, but the software would no more be able to link this rename proof file with
        the historical request.
        Case of zip : The proof file name will be "[original_filename]-[request_number].zip", with one entry "[original_filename]"
        and one entry "proof.txt"
        Case of pdf : The proof file name will be "[original_filename_without_pdf_extension]-[request number].pdf", regardless of the
        existence of a ".pdf" extension in the original file name.
                Original file name :
        The original file was "copied" in app data directory, named by the request number on 4 digits. In case of
        PDF Variant, neutral metadata was inserted. The original name (display name) of this file was stored
        in the request record in local db
         */
        boolean pdfVariant;
        byte data[] = new byte[BUFFER];
        String newXmpMetadata, oldXmpMetadata;
        int result;
        try {
            if (!FileUtils.checkSDDirectory()) return Constants.STATUS_ERROR;

            // check if the file accepts metadata
            File sourceFile = new File(context.getFilesDir(), String.format(Locale.US, "%04d", proof.mRequest));
            pdfVariant = PdfUtils.checkInternalFileVariant(context, sourceFile);

            if (!pdfVariant) { // Not a non-encrypted pdf file, build zip prooffile
                // write proof file
                if (!ZipUtils.createZipProofFile(context, displayName, proof.toJSON().toString(), proof.mRequest))
                    return Constants.STATUS_ERROR;
                else
                    result = Constants.STATUS_FINISHED_ZIP;

            } else { // pdf variant

                if (!PdfUtils.createPdfProofFile(context, displayName, proof.toJSON().toString(), proof.mRequest))
                    return Constants.STATUS_ERROR;
                else
                    result = Constants.STATUS_FINISHED_PDF;
            }

            // Delete the saved original file
            Boolean del = sourceFile.delete();
            if (del)
                Log.d(Constants.TAG, "internal file successfully deleted");
            else{
                Log.e(Constants.TAG, "Error deleting internal file ");
                return Constants.STATUS_ERROR;
            }
            return result;

        } catch (IOException e) {
            Log.e(Constants.TAG, "IO Exception while building proof file : " + e);
            e.printStackTrace();
            return Constants.STATUS_ERROR;
        }
    }


    // Utility method to work with hexadecimal strings
    private static String convertByteArrayToHexString(byte[] arrayBytes) {
        StringBuilder stringBuilder = new StringBuilder();
        for (byte arrayByte : arrayBytes) {
            stringBuilder.append(Integer.toString((arrayByte & 0xff) + 0x100, 16)
                    .substring(1));
        }
        return stringBuilder.toString();
    }

    // read the proof text and return it
    // case zip : proof text is stored in an entry with name "proof.txt"
    public static String readProofFromProofFilename(String proofFilename) {
        boolean pdfVariant;

        // check if the file accepts metadata
        pdfVariant = PdfUtils.checkProofFileVariant(proofFilename);

        if (!pdfVariant) {    // zip file : read proof entry
            return ZipUtils.readProofFromProofFile(proofFilename);
        } else {  // pdf file : read metadata
            return PdfUtils.readProofFromProofFile(proofFilename);
        }
    }

    // read the proof text and return it
    // case zip : proof text is stored in an entry with name "proof.txt"
    public static String readProofFromFullUri(Context context, Uri fullUri) {
        boolean pdfVariant;

        //TODO : adjust all calls with full uri
        String proofFilename = getFilename(context, fullUri);

        // check if the file accepts metadata
        pdfVariant = PdfUtils.checkProofFileVariant(proofFilename);

        if (!pdfVariant) {    // zip file : read proof entry
            return ZipUtils.readProofFromProofFile(proofFilename);
        } else {  // pdf file : read metadata
            return PdfUtils.readProofFromProofFile(proofFilename);
        }
    }


    // gets the document hash in a hex String, given the name of the proof file
    // if proof file is a zip, the raw document is stored in an entry of the zip
    // if proof file is a pdf, the raw document to hash is obtained by rewriting pdf with neutral proof
    static public String computeDocumentHashFromProofFilename(Context context, String proofFilename) {

        boolean pdfVariant;
        String hash = null;
        // check if the file accepts metadata
        pdfVariant = PdfUtils.checkProofFileVariant(proofFilename);

        // First step : prepare a tmp file to hash
        if (!pdfVariant){  // zip file : extract data entry in tmp file
            if (!ZipUtils.saveFileToHash(context, proofFilename, "tmpfile")){return null;}
        } else {  // pdf variant : copy proof pdf in tmp file and overwrite proof metadata with neutral proof
            if (!PdfUtils.saveFileToHash(context, proofFilename, "tmpfile")){return null;}
        }

        // Second step, compute the hash
        // this part does not depend of proof file type (zip or pdf)
        if ((hash = computeHashFromFile(context, "tmpfile")) == null){
            Log.e(Constants.TAG, "Hash Exception ");
            return null;
        }

        // Third part, delete the temp file that was created
        File tmpFile = new File(context.getFilesDir(), "tmpfile");
        Boolean del = tmpFile.delete();
        if (del)
            Log.d(Constants.TAG, "internal file successfully deleted");
        else
            Log.e(Constants.TAG, "Error deleting internal file ");

        return hash;
    }

    // gets the document hash in a hex String, given the full Uri of the proof file
    // if proof file is a zip, the raw document is stored in an entry of the zip
    // if proof file is a pdf, the raw document to hash is obtained by rewriting pdf with neutral proof
    static public String computeDocumentHashFromFullUri(Context context, Uri fullUri) {

        boolean pdfVariant;
        String hash = null;
        // check if the file accepts metadata

        // TODO : adjust all calls with full uri
        String proofFilename = getFilename(context, fullUri);

        pdfVariant = PdfUtils.checkProofFileVariant(proofFilename);

        // First step : prepare a tmp file to hash
        if (!pdfVariant){  // zip file : extract data entry in tmp file
            if (!ZipUtils.saveFileToHash(context, proofFilename, "tmpfile")){return null;}
        } else {  // pdf variant : copy proof pdf in tmp file and overwrite proof metadata with neutral proof
            if (!PdfUtils.saveFileToHash(context, proofFilename, "tmpfile")){return null;}
        }

        // Second step, compute the hash
        // this part does not depend of proof file type (zip or pdf)
        if ((hash = computeHashFromFile(context, "tmpfile")) == null){
            Log.e(Constants.TAG, "Hash Exception ");
            return null;
        }

        // Third part, delete the temp file that was created
        File tmpFile = new File(context.getFilesDir(), "tmpfile");
        Boolean del = tmpFile.delete();
        if (del)
            Log.d(Constants.TAG, "internal file successfully deleted");
        else
            Log.e(Constants.TAG, "Error deleting internal file ");

        return hash;
    }

    // Compute a file's hash, returning hex string or null
    public static String computeHashFromFile(Context context, String nameFile) {
        String hash;
        try {
            File sourceFile = new File(context.getFilesDir(), nameFile);
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

        } catch (NoSuchAlgorithmException | IOException e) {
            Log.e(Constants.TAG, "Hash Exception : " + e);
            e.printStackTrace();
            return null;
        }
        return hash;
    }

    // Get the name of the proof file given request id and original file name
    public static String getProofFileName(int requestId, String fileName, int status) {
        switch (status){
            case Constants.STATUS_FINISHED_PDF:
                String fileNameWithoutExtsension;
                // Strip Filename's extension if exists
                if (fileName.lastIndexOf(".pdf") == fileName.length()-4){ // filename ends with ".pdf"
                    fileNameWithoutExtsension = fileName.substring(0, fileName.length()-4);
                } else {
                    fileNameWithoutExtsension = fileName;
                }
                return (fileNameWithoutExtsension
                        + "."
                        + String.format("%04d", requestId)
                        + ".pdf");

            case Constants.STATUS_FINISHED_ZIP:
                return (fileName
                        + "."
                        + String.format("%04d", requestId)
                        + ".zip");
            default:
                return "ERROR failed getting proof file name";
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

    // Compute the hash of the concatenation of two hashes
    // This is done repeatedly for each tier of the Merkle tree to obtain the root hash
    public static String computeHashFromTwoHashes(String left, String right) {
        String newHash = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = (left + right).getBytes();

            digest.update(bytes);

            byte[] hashedBytes = digest.digest();

            newHash = convertByteArrayToHexString(hashedBytes);

        } catch (NoSuchAlgorithmException e) {
            Log.e(Constants.TAG, "Hash Exception : " + e);
            e.printStackTrace();
            return null;
        }
        return newHash;
    }

    // Check a merkle tree
    public static boolean checkTree(String tree) {
        Log.d(Constants.TAG, "tree:" + tree);

        JSONArray arrayTree;
        JSONObject json_data;
        String currentHash;

        try {
            arrayTree = new JSONArray(tree);

            // walk into the merkle tree and compute the root tree
            // all objects should have one name only
            // First object should have the name "hashdoc"
            json_data = arrayTree.getJSONObject(0);

            if (!json_data.has("hashdoc")) {
                Log.e(Constants.TAG, "ERROR Proof tree should begin with hashdoc");
                return false;
            } else {
                currentHash = json_data.getString("hashdoc");
            }

            // Next come (array size - 2) objects with either "toleftof" or "torightof"
            // Last object should have "treeroot" name
            for (int i = 1; i < arrayTree.length() - 1; i++) {
                json_data = arrayTree.getJSONObject(i);
                if (json_data.has("toleftof")) {
                    currentHash = ProofUtils.computeHashFromTwoHashes(currentHash, json_data.optString("toleftof"));
                } else {
                    if (json_data.has("torightof")) {
                        currentHash = ProofUtils.computeHashFromTwoHashes(json_data.optString("torightof"), currentHash);
                    } else {
                        Log.e(Constants.TAG, "ERROR invalid tier format");
                        return false;
                    }
                }
            }

            // Last JSONobject should be "treeroot"
            json_data = arrayTree.getJSONObject(arrayTree.length() - 1);
            if (!json_data.has("treeroot")) {
                Log.e(Constants.TAG, "ERROR tree should end with roottree");
                return false;
            }

            // Compare computed root with the root stored in the proof
            if (json_data.optString("treeroot").equals(currentHash)) {
                return true;
            } else {
                Log.e(Constants.TAG, "ERROR stored tree root differs from computed tree root");
                return false;
            }

        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Decode a JSON encoded proof, returning a bundle
    public static Bundle decodeProof(String txtProof) {
        Bundle bundle = new Bundle();
        String tree, tiers = "", root = "";
        JSONObject j;

        try {
            j = new JSONObject(txtProof);
            if (!j.has("chain")) {
                Log.e(Constants.TAG, "ERROR No chain name");
                return null;
            } else {
                bundle.putString("chain", j.getString("chain"));
            }
            if (!j.has("txid")) {
                Log.e(Constants.TAG, "ERROR No txid name");
                return null;
            } else {
                bundle.putString("txid", j.getString("txid"));
            }
            if (!j.has("txinfo")) {
                Log.e(Constants.TAG, "ERROR No txinfo name");
                return null;
            } else {
                bundle.putString("txinfo", j.getString("txinfo"));
            }
            if (!j.has("tree")) {
                Log.e(Constants.TAG, "ERROR No tree name");
                return null;
            } else {
                bundle.putString("tree", j.getString("tree"));
                tree = j.getString("tree");
            }

            // split the merkle tree into hashdoc, tiers, root
            JSONArray arrayTree;
            JSONObject json_data;
            arrayTree = new JSONArray(tree);

            // all objects should have one name only
            // First object should have the name "hashdoc"
            json_data = arrayTree.getJSONObject(0);

            if (!json_data.has("hashdoc")) {
                Log.e(Constants.TAG, "ERROR Proof tree should begin with hashdoc");
                return null;
            } else {
                bundle.putString("hashdoc", json_data.getString("hashdoc"));
            }

            // Next come Zero to n objects with either "toleftof" or "torightof"
            // Last object should have "treeroot" name
            for (int i = 1; i < arrayTree.length(); i++) {
                json_data = arrayTree.getJSONObject(i);
                if (json_data.has("toleftof")) {
                    tiers += "Hash to left of : " + json_data.optString("toleftof") + "\n";
                } else {
                    if (json_data.has("torightof")) {
                        tiers += "Hash to right of : " + json_data.optString("torightof") + "\n";
                    } else {
                        if (json_data.has("treeroot")) {
                            root = json_data.optString("treeroot");
                        } else {
                            Log.e(Constants.TAG, "ERROR Proof tree contains invalid objects");
                            return null;
                        }
                    }
                }

            }
            bundle.putString("tiers", tiers);
            bundle.putString("root", root);


            // Check if a root was there
            // TODO : add more syntax controls, eg root should be last and unique
            if (root.equals("")) {
                Log.e(Constants.TAG, "ERROR Proof tree contains no root object");
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        return bundle;
    }

    public static Bundle decodeBlockExplorerResponse(JSONObject j) {
        Bundle bundle = new Bundle();
        try {
            bundle.putString("txid", j.getString(Constants.BLOCK_EXPLORER_COL_TXID));
            bundle.putString("date", j.getString(Constants.BLOCK_EXPLORER_COL_DATE_CONFIRM));
            bundle.putInt("nbconfirm", Integer.valueOf(j.getString(Constants.BLOCK_EXPLORER_COL_NB_CONFIRM)));
            // First ouput must contain the OP_RETURN script
            JSONArray jArrayOutputs = j.getJSONArray(Constants.BLOCK_EXPLORER_COL_OUTPUTS);
            if (jArrayOutputs.length()!=2) {
                Log.e(Constants.TAG, "Error, transaction does not contain 2 outputs, but : "+jArrayOutputs.length());
                return null;
            } else {
                JSONObject firstOutput = jArrayOutputs.getJSONObject(0);
                bundle.putString("opreturn_data", firstOutput.getString(Constants.BLOCK_EXPLORER_COL_DATA_HEX));
                Log.d(Constants.TAG, "OPRETURN Data : "+bundle.getString("opreturn_data"));
            }

        } catch (JSONException e) {
            Log.e(Constants.TAG, "Error decoding block explorer response");
            e.printStackTrace();
            return null;
        }
        return bundle;
    }
}