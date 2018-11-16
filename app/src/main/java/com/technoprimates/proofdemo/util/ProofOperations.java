package com.technoprimates.proofdemo.util;

/*
 * This class contains various static methods managing files, hashes, proofs.
 * Created by JC, october 2018.
 */
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.technoprimates.proofdemo.struct.Proof;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class ProofOperations {

    // build the proof file
    // returns depends on success and type of proof file created
    public static int buildProofFile(Context context, String displayName, Proof proof) throws ProofException {

        /* About file names
                Proof file name :
        Giving that the user may issue several proof requests for the same file name, it is necessary to distinguish
        the proof files matching each of those requests. Therefore, the proof file built will reflect in its name
        the corresponding request number. It is up to the user to rename the proof file at its convenience, in this case the proof
        would remain valid and checkable, but the software would no more be able to link this renamed proof file with
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
        int result;
        if (!FileUtils.checkSDDirectory()) return Constants.STATUS_ERROR;

        // check if the file accepts metadata
        File sourceFile = new File(context.getFilesDir(), String.format(Locale.US, "%04d", proof.mRequest));
        ProofFile proofFile = ProofFile.set(context, Uri.fromFile(sourceFile));

        // Create proof file
        proofFile.writeOnSDCard(displayName, proof.toJSON().toString(), proof.mRequest);

        switch (proofFile.typeOf()){
            case Constants.VARIANT_PDF:
                result = Constants.STATUS_FINISHED_PDF;
                break;
            case Constants.VARIANT_ZIP:
                result = Constants.STATUS_FINISHED_ZIP;
                break;
            default:
                throw new ProofException(ProofError.ERROR_UNKNOWN_FINISHED_STATUS);
        }

        // Delete the saved original file
        Boolean del = sourceFile.delete();
        if (!del) {
            throw new ProofException(ProofError.ERROR_DELETE_TEMP_FILE_FAILED);
        }
        return result;
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
    public static String readProofFromFullUri(Context context, Uri fullUri) throws ProofException  {
        ProofFile proofFile;
        // check if the file accepts metadata
        proofFile = ProofFile.set(context, fullUri);
        return proofFile.readProof();
    }

    // Compute a file's hash, returning hex string or null
    // File is identified by its name in app data storage
    public static String computeHashFromFile(Context context, String nameFile) throws ProofException {
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

            hash = ProofOperations.convertByteArrayToHexString(hashedBytes);
            in.close();
            return hash;

        } catch (NoSuchAlgorithmException e) {
            throw new ProofException(ProofError.ERROR_UNKNOWN_HASH_ALGORITHM);

        } catch (IOException e) {
            throw new ProofException(ProofError.ERROR_COMPUTE_HASH);
        }
    }

    // Compute the hash of the concatenation of two hashes
    // This is done repeatedly for each tier of the Merkle tree to obtain the root hash
    private static String computeHashFromTwoHashes(String left, String right) throws ProofException {
        String newHash = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = (left + right).getBytes();

            digest.update(bytes);

            byte[] hashedBytes = digest.digest();

            newHash = convertByteArrayToHexString(hashedBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new ProofException(ProofError.ERROR_COMPUTE_HASH);
        }
        return newHash;
    }

    // Check a merkle tree
    public static boolean checkTree(String tree) throws ProofException {
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
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
            } else {
                currentHash = json_data.getString("hashdoc");
            }

            // Next come (array size - 2) objects with either "toleftof" or "torightof"
            // Last object should have "treeroot" name
            for (int i = 1; i < arrayTree.length() - 1; i++) {
                json_data = arrayTree.getJSONObject(i);
                if (json_data.has("toleftof")) {
                    currentHash = ProofOperations.computeHashFromTwoHashes(currentHash, json_data.optString("toleftof"));
                } else {
                    if (json_data.has("torightof")) {
                        currentHash = ProofOperations.computeHashFromTwoHashes(json_data.optString("torightof"), currentHash);
                    } else {
                        throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
                    }
                }
            }

            // Last JSONobject should be "treeroot"
            json_data = arrayTree.getJSONObject(arrayTree.length() - 1);
            if (!json_data.has("treeroot")) {
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
            }

            // Compare computed root with the root stored in the proof
            if (json_data.optString("treeroot").equals(currentHash)) {
                return true;  // hashs do match
            } else {
                return false; // hashs do not match
            }

        } catch (JSONException e) {
            throw new ProofException(ProofError.ERROR_JSON_EXCEPTION);
        }
    }

    // Decode a JSON encoded proof, returning a bundle
    public static Bundle decodeProof(String txtProof) throws ProofException {
        Bundle bundle = new Bundle();
        String tree, tiers = "", root = "";
        JSONObject j;

        try {
            j = new JSONObject(txtProof);
            if (!j.has("chain")) {
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
            } else {
                bundle.putString("chain", j.getString("chain"));
            }
            if (!j.has("txid")) {
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
            } else {
                bundle.putString("txid", j.getString("txid"));
            }
            if (!j.has("txinfo")) {
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
            } else {
                bundle.putString("txinfo", j.getString("txinfo"));
            }
            if (!j.has("tree")) {
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
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
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
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
                            throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
                        }
                    }
                }

            }
            bundle.putString("tiers", tiers);
            bundle.putString("root", root);


            // Check if a root was there
            // TODO : add more syntax controls, eg root should be last and unique
            if (root.equals("")) {
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
            }
        } catch (JSONException e) {
            throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
        }
        return bundle;
    }

    public static Bundle decodeBlockExplorerResponse(JSONObject j) throws ProofException {
        Bundle bundle = new Bundle();
        try {
            bundle.putString("txid", j.getString(Constants.BLOCK_EXPLORER_COL_TXID));
            bundle.putString("date", j.getString(Constants.BLOCK_EXPLORER_COL_DATE_CONFIRM));
            bundle.putInt("nbconfirm", Integer.valueOf(j.getString(Constants.BLOCK_EXPLORER_COL_NB_CONFIRM)));
            // First ouput must contain the OP_RETURN script
            JSONArray jArrayOutputs = j.getJSONArray(Constants.BLOCK_EXPLORER_COL_OUTPUTS);
            if (jArrayOutputs.length()!=2) {
                throw new ProofException(ProofError.ERROR_INVALID_BLOCKEXPLORER_RESPONSE);
            } else {
                JSONObject firstOutput = jArrayOutputs.getJSONObject(0);
                bundle.putString("opreturn_data", firstOutput.getString(Constants.BLOCK_EXPLORER_COL_DATA_HEX));
            }

        } catch (JSONException e) {
            throw new ProofException(ProofError.ERROR_JSON_EXCEPTION);
        }
        return bundle;
    }
}