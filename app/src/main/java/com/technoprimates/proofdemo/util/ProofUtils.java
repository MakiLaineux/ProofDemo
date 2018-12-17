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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import static com.technoprimates.proofdemo.util.Constants.*;

public class ProofUtils {


    // Utility method to work with hexadecimal strings
    public static String convertByteArrayToHexString(byte[] arrayBytes) {
        StringBuilder stringBuilder = new StringBuilder();
        for (byte arrayByte : arrayBytes) {
            stringBuilder.append(Integer.toString((arrayByte & 0xff) + 0x100, 16)
                    .substring(1));
        }
        return stringBuilder.toString();
    }

    // Compute the hash of the concatenation of a hash and a message
    public static String overHash(String hash, String message) throws ProofException {
        String newHash;
        if (message == null || message.equals("")) {
            return hash;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = (hash + message).getBytes();

            digest.update(bytes);

            byte[] hashedBytes = digest.digest();

            newHash = convertByteArrayToHexString(hashedBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new ProofException(ProofError.ERROR_COMPUTE_HASH);
        }
        return newHash;
    }

    public static Bundle decodeBlockExplorerResponse(JSONObject j) throws ProofException {
        Bundle bundle = new Bundle();
        try {
            bundle.putString("txid", j.getString(BLOCK_EXPLORER_COL_TXID));
            bundle.putString("date", j.getString(BLOCK_EXPLORER_COL_DATE_CONFIRM));
            bundle.putInt("nbconfirm", Integer.valueOf(j.getString(BLOCK_EXPLORER_COL_NB_CONFIRM)));
            // First ouput must contain the OP_RETURN script
            JSONArray jArrayOutputs = j.getJSONArray(BLOCK_EXPLORER_COL_OUTPUTS);
            if (jArrayOutputs.length()!=2) {
                throw new ProofException(ProofError.ERROR_INVALID_BLOCKEXPLORER_RESPONSE);
            } else {
                JSONObject firstOutput = jArrayOutputs.getJSONObject(0);
                bundle.putString("opreturn_data", firstOutput.getString(BLOCK_EXPLORER_COL_DATA_HEX));
            }

        } catch (JSONException e) {
            throw new ProofException(ProofError.ERROR_JSON_EXCEPTION);
        }
        return bundle;
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

    // Check SDCard Directory, create if does not exist
    public static boolean checkSDDirectory() {
        File dir = new File(Environment.getExternalStorageDirectory() + DIRECTORY_LOCAL);
        if (!dir.isDirectory() && !dir.mkdirs())
            return false;
        else
            return true;
    }
}