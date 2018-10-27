package com.technoprimates.proofdemo.util;

/*
 * This class contains various static methods managing files, hashes, proofs.
 * Created by JC, october 2018.
 */
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;

import com.technoprimates.proofdemo.struct.Proof;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ProofUtils {

    private static final int BUFFER = 2048;

    private ProofUtils() {
    }


    /*
     * This method makes a copy of the original file in app storage space. The proof file will later be built by joining
     * this saved file and the proof data received from the server
     * There is no certainty that the original file will still be available when the proof is received by the server.
     * Moreover, if the original file was to be modified between the request's creation and the reception of the proof,
     * the file's hash would also be modified and the proof wouldn't be valid. Therefore, it is necessary to dispose
     * of a copy of the original file.
     * The saved copy is named by the request number
     * This copy will later be deleted when the proof file is built
     */
    public static boolean saveFileContentToAppData(Context context, String stringUri, String fileName) {
        //TODO : PDF variant, copied file must contain space for [void] metadata
        final Uri uriSource = Uri.parse(stringUri);
        Log.d(Constants.TAG, "          fileName    : " + fileName + ", namesource : " + uriSource.toString());

        try {
            InputStream in = context.getContentResolver().openInputStream(uriSource);
            File destFile = new File(context.getFilesDir(), fileName);
            OutputStream out = new FileOutputStream(destFile);

            // Transfer bytes from in to out
            byte[] buf = new byte[BUFFER];
            int len;

            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();

            Log.d(Constants.TAG, "Copy to App data ok, copy ok");
        } catch (IOException e) {
            Log.e(Constants.TAG, "Copy to App data failed : " + e);
            e.printStackTrace();
            return false;
        }
        return true;
    }


    /*
     * Write on SDCard the proof file containing the original file and the proof.
     * This is done when the text proof is received from the server.
     *
     * In general, the proof file is a zip file with two entries, for the original doc and for the proof
     * It may also be the original file with proof metadata inserted, if the file type permits it, e.g. pdf
     *
     * The name of the original file was previously stored in the local database, the request id is contained in the server response
     * The content of the original file was previously saved on app data storage, named by the request id
     *
     * When the writing is completed, delete the original saved file on app data storage
     *
     */
    // TODO : add PDF variant
    public static boolean buildProofFile(Context context, String displayName, Proof proof) {

        /* Names :
        The original file was copied in app data directory, named by the request number on 4 digits
        The original name (display name) of this file was stored in the request record in local db
        zip : The proof file name will be [original_name].[request_number].zip, with one entry [original_file]
            and one entry [proof.txt]
         */

        try {
            File sourceFile = new File(context.getFilesDir(), String.format(Locale.US, "%04d", proof.mRequest));
            FileInputStream in = new FileInputStream(sourceFile);
            BufferedInputStream origin = new BufferedInputStream(in, BUFFER);
            String zipName = displayName + String.format(Locale.US, ".%04d", proof.mRequest) + ".zip";

            byte data[] = new byte[BUFFER];

            // Check SDCard Directory
            File dir = new File(Environment.getExternalStorageDirectory() + Constants.DIRECTORY_LOCAL);
            if (!dir.isDirectory()) {
                if (dir.mkdirs())
                    Log.d(Constants.TAG, "Création répertoire : " + dir);
                else {
                    Log.e(Constants.TAG, "Echec création du répertoire : " + dir);
                    return false;
                }
            }

            // Prepare Zip file
            Log.d(Constants.TAG, "Zip ------------------------------ ");
            Log.d(Constants.TAG, "Zip name: " + zipName);
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

            // Add proof file to createZip, name is always proof.txt
            String label = "proof.txt";
            Log.d(Constants.TAG, "Adding proof: " + label);
            ZipEntry entryProof = new ZipEntry(label);
            out.putNextEntry(entryProof);

            // Write proof text in JSON format
            JSONObject j = proof.toJSON();
            byte[] tmpBytes = j.toString().getBytes();
            out.write(tmpBytes, 0, tmpBytes.length);

            out.close();

            // Delete the original file
            Boolean del = sourceFile.delete();
            if (del)
                Log.d(Constants.TAG, "internal file successfully deleted");
            else
                Log.e(Constants.TAG, "Error deleting internal file ");

        } catch (IOException e) {
            Log.e(Constants.TAG, "IO Exception while building proof file : " + e);
            e.printStackTrace();
            return false;
        }

        return true;
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
    // TODO : PDF variant
    public static String readProofFromProofFilename(String proofFilename) {
        InputStream is;
        ZipInputStream zis;

        // open zip file
        String entryname;
        try {
            is = new FileInputStream(Environment.getExternalStorageDirectory()+Constants.DIRECTORY_LOCAL+proofFilename);
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
            zis.close();
            return new String(buffer, "UTF-8");
        } catch (FileNotFoundException e) {
            Log.e(Constants.TAG, "zip entry : file not found");
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            Log.e(Constants.TAG, "zip entry : IO Exception");
            e.printStackTrace();
            return null;
        }
    }


    // gets the document hash in a hex String, given the name of the proof file
    // if proof file is a zip, the raw document is stored in an entry of the zip
    // TODO : PDF variant
    static public String computeDocumentHashFromProofFilename(Context context, String proofFilename) {
        String hash = null;
        InputStream is;
        ZipInputStream zis;

        // open zip file
        String entryname;
        try {

            // First part, unzip the originaldocument in file "tmpfile"

            // output to tmp file
            File tmpFile = new File(context.getFilesDir(), "tmpfile");
            FileOutputStream fout = new FileOutputStream(tmpFile);

            // open zip file
            is = new FileInputStream(Environment.getExternalStorageDirectory()+Constants.DIRECTORY_LOCAL+proofFilename);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[4096];
            int count;

            while ((ze = zis.getNextEntry()) != null) {
                //TODO adjust with real entry name
                // loop until zip entry does not match "proof.txt"
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

        } catch (FileNotFoundException e) {
            Log.e(Constants.TAG, "zip entry : file not found");
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            Log.e(Constants.TAG, "zip entry : IO Exception");
            e.printStackTrace();
            return null;
        }

        // Second part, compute the hash
        try {
            // open the tmp file just created
            File tmpFile = new File(context.getFilesDir(), "tmpfile");
            FileInputStream in = new FileInputStream(tmpFile);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] buf = new byte[1024];
            int bytesRead = -1;

            while ((bytesRead = in.read(buf)) != -1) {
                digest.update(buf, 0, bytesRead);
            }

            byte[] hashedBytes = digest.digest();

            hash = convertByteArrayToHexString(hashedBytes);
            in.close();

        } catch (NoSuchAlgorithmException | IOException e) {
            Log.e(Constants.TAG, "Hash Exception : " + e);
            e.printStackTrace();
            return null;
        }

        // Third part, delete the temp file that was created
        // Delete the original file
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
    // TODO : PDF variant
    public static String getProofFileName(int requestId, String fileName) {
        return (fileName
                + "."
                + String.format("%04d", requestId)
                + ".zip");
    }


    // Get the name of the original given the name of the proof file
    // if proof file is a zip, the name is the name of the entry which is not "proof.txt"
    // TODO : PDF variant
    public static String getFilenameFromProofFilename(String proofName) {
        String fileName = null;
        InputStream is;
        ZipInputStream zis;
        String entryname;

        // open zip file
        try {
            is = new FileInputStream(Environment.getExternalStorageDirectory()+Constants.DIRECTORY_LOCAL+proofName);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[4096];
            int count;

            // loop until finding an entry with name different from "proof.txt"
            while ((ze = zis.getNextEntry()) != null) {
                entryname = ze.getName();
                zis.closeEntry();
                if (entryname.equals("proof.txt")) {
                    continue;
                } else {
                    return entryname;
                }
            }
            zis.close();
        } catch (FileNotFoundException e) {
            Log.e(Constants.TAG, "zip entry : file not found");
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            Log.e(Constants.TAG, "zip entry : IO Exception");
            e.printStackTrace();
            return null;
        }


        return fileName;
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