package com.technoprimates.proofdemo.struct;

import android.util.Log;

import com.technoprimates.proofdemo.util.Constants;

import org.json.JSONException;
import org.json.JSONObject;
import org.parceler.Parcel;

/*
 * Server response structure relative to a proof request (status, proof elements)
 * Created by JC, october 2018.
 */
@Parcel

public class Proof {
    public int mRequest;   // Request number
    public int mStatus;    // Request's status
    public String mChain;  // Part of the proof : id of the blockchain containing the root
    public String mTree;  // Part of the proof : Merkle tree (JSON string)
    public String mTxid;  // Part of the proof : transaction id
    public String mInfo;  // Part of the proof : date/time at which the transaction was mined

    // Default constructors (needed for parcel)
    public Proof() {
    }

    // Constructor from JSON object
    public Proof(JSONObject j) {
        try {
            this.mRequest = Integer.valueOf(j.getString(Constants.PROOF_COL_REQUEST));
            this.mStatus = Integer.valueOf(j.getString(Constants.PROOF_COL_STATUS));
            this.mChain = j.getString(Constants.PROOF_COL_CHAIN);
            this.mTree = j.getString(Constants.PROOF_COL_TREE);
            this.mTxid = j.getString(Constants.PROOF_COL_TXID);
            this.mInfo = j.getString(Constants.PROOF_COL_INFO);

        } catch (JSONException e) {
            Log.e(Constants.TAG, "Error decodage JSON 8549 :" + e.toString());
        } catch (Exception e) {
            Log.e(Constants.TAG, "Erreur indéterminée 8550 :" + e.toString());
        }
    }

    // convert to JSON
    public JSONObject toJSON(){
        try {
            JSONObject j =new JSONObject();
            j.put("chain", mChain);
            j.put("txid", mTxid);
            j.put("txinfo", mInfo);
            j.put("tree", mTree);
            return j;
        } catch (JSONException e) {
            Log.e(Constants.TAG, "Error encodage JSON :" + e.toString());
        } catch (Exception e) {
            Log.e(Constants.TAG, "Erreur indéterminée  :" + e.toString());
        }
        return null;
    }
}