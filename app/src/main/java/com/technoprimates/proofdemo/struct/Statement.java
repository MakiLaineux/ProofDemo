package com.technoprimates.proofdemo.struct;

import android.util.Log;

import com.technoprimates.proofdemo.util.Constants;
import com.technoprimates.proofdemo.util.ProofError;
import com.technoprimates.proofdemo.util.ProofException;
import com.technoprimates.proofdemo.util.ProofUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A proof statement associated with a specific file describes how some data related to this file was
 * stored on a blockchain at a specific point of time. Depending on the file's type, the statement
 * may be saved inside the file as a piece of metadata, otherwise it can be kept alongside.
 *
 * Given a file and a statement, one can check whether this statement actually furnishes a proof of
 * existence of this file's content at this point of time.
 */
public class Statement {
    private String docHash;
    private String message;
    private String overHash;
    private String tree;
    private String chain;
    private String txid;
    private String txinfo;
    private String tiers; // String formulation of the merkle tree tiers, suitable for screen display
    private String root; // Root of the merkle tree, suitable for screen display

    /**
     * Creates a statement object
     *
     * @param docHash A cryptographic hash depending on the file's content.
     *                It is basically the SHA-256 hash of the file, however, if the statement
     *                is stored inside the file, it must be removed before computing the hash.
     * @param message A string message to store in the proof statement.
     * @param tree  A JSON String giving a merkle tree of hashes
     * @param chain A string identifying a blockchain
     * @param txid A hexadecimal string containing a transaction hash
     * @param txinfo A string containing a timestamp
     * @throws ProofException App-specific exception
     */
    public Statement (String docHash, String message, String tree,
                      String chain, String txid, String txinfo) throws ProofException {
        this.docHash = docHash;
        if (message == null || message.equals("")){
            this.message = null;
            this.overHash = this.docHash;
        } else {
            // re-hash with proof message
            this.message = message;
            this.overHash = ProofUtils.overHash(docHash, message);
        }
        this.tree = tree;
        this.chain = chain;
        this.txid = txid;
        this.txinfo =txinfo;
        this.tiers = null;
        this.root = null;
    }

    /**
     * Creates a statement objects
     *
     * @param txtStatement A well formed JSON string describing a statement
     * @throws ProofException
     */
    public Statement(String txtStatement) throws ProofException {
        JSONObject j;
        try {
            j = new JSONObject(txtStatement);
            if (!j.has("message")) {  //optional proof author's string message in the proof
                this.message = "";
            } else {
                this.message = j.getString("message");
            }
            if (!j.has("chain")) {
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
            } else {
                this.chain = j.getString("chain");
            }
            if (!j.has("txid")) {
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
            } else {
                this.txid = j.getString("txid");
            }
            if (!j.has("txinfo")) {
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
            } else {
                this.txinfo = j.getString("txinfo");
            }
            if (!j.has("tree")) {
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
            } else {
                this.tree = j.getString("tree");
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
                this.docHash = json_data.getString("hashdoc");
            }

            // Next come Zero to n objects with either "toleftof" or "torightof"
            // Last object should have "treeroot" name
            for (int i = 1; i < arrayTree.length(); i++) {
                json_data = arrayTree.getJSONObject(i);
                if (json_data.has("toleftof")) {
                    this.tiers += "Hash to left of : " + json_data.optString("toleftof") + "\n";
                } else {
                    if (json_data.has("torightof")) {
                        this.tiers += "Hash to right of : " + json_data.optString("torightof") + "\n";
                    } else {
                        if (json_data.has("treeroot")) {
                            this.root = json_data.optString("treeroot");
                        } else {
                            throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
                        }
                    }
                }

            }
            // Check if a root was there
            // TODO : add more syntax controls, eg root should be last and unique
            if (this.root.equals("")) {
                throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
            }
        } catch (JSONException e) {
            throw new ProofException(ProofError.ERROR_INVALID_PROOF_SYNTAX);
        }
    }

    public String getString(){
        JSONObject j =new JSONObject();
        try {
            j.put("dochash", docHash);
            j.put("message", message);
            j.put("overhash", overHash);
            j.put("tree", tree);
            j.put("chain", chain);
            j.put("txid", txid);
            j.put("txinfo", txinfo);
        } catch (JSONException e) {
            Log.e(Constants.TAG, "Error encodage JSON :" + e.toString());
        } catch (Exception e) {
            Log.e(Constants.TAG, "Erreur indéterminée  :" + e.toString());
        }
        return j.toString();
    }


    public boolean checkTree() throws ProofException {
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
                    currentHash = ProofUtils.overHash(currentHash, json_data.optString("toleftof"));
                } else {
                    if (json_data.has("torightof")) {
                        currentHash = ProofUtils.overHash(json_data.optString("torightof"), currentHash);
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

    // getters
    public String getTiers(){return tiers;}
    public String getRoot(){return root;}
    public String getDocHash() {return docHash;}
    public String getMessage() {return message;}
    public String getOverHash() {return overHash;}
    public String getTree() {return tree;}
    public String getChain() {return chain;}
    public String getTxid() {return txid;}
    public String getTxinfo() {return txinfo;}
}
