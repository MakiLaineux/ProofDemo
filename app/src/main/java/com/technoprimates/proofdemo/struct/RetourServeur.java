package com.technoprimates.proofdemo.struct;

import android.util.Log;

import com.technoprimates.proofdemo.util.Globals;

import org.json.JSONException;
import org.json.JSONObject;
import org.parceler.Parcel;

/**
 * Created by MAKI LAINEUX on 9/8/2016.
 * Objet retourne par le serveur lors des requetes de synchro
 */
@Parcel

public class RetourServeur {
    public int mDemande;   // numero de la demande
    public int mStatut;    // statut de la demande
    public String mTree;  // partie de la preuve
    public String mTxid;  // partie de la preuve
    public String mInfo;  // partie de la preuve

    // Constructeur défaut (nécessaire pour parcellisation)
    public RetourServeur() {
    }

    // Constructeur à partir d'un objet JSON
    public RetourServeur(JSONObject j) {
        try {
            this.mDemande = Integer.valueOf(j.getString(Globals.RETOUR_SERVEUR_COL_DEMANDE));
            this.mStatut = Integer.valueOf(j.getString(Globals.RETOUR_SERVEUR_COL_STATUT));
            this.mTree = j.getString(Globals.RETOUR_SERVEUR_COL_TREE);
            this.mTxid = j.getString(Globals.RETOUR_SERVEUR_COL_TXID);
            this.mInfo = j.getString(Globals.RETOUR_SERVEUR_COL_INFO);
        } catch (JSONException e) {
            Log.e(Globals.TAG, "Error decodage JSON 8549 :" + e.toString());
        } catch (Exception e) {
            Log.e(Globals.TAG, "Erreur indéterminée 8550 :" + e.toString());
        }
    }
}