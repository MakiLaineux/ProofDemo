package com.technoprimates.proofdemo.struct;

import org.parceler.Parcel;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by MAKI LAINEUX on 5/9/2016.
 * DigitProof: une demande de preuve pour un fichier
 */
@Parcel

public class ProofRequest {
    private int _id;         // id en base SQLite
    private String _filename; // nom complet du fichier
    private String _hash;   // hash du fichier
    private int _statut;    // statut de la demande
    private String _tree;  // partie de la preuve
    private String _txid;  // partie de la preuve
    private String _info;  // partie de la preuve
    private String _date_request;

    // Constructeur défaut (nécessaire pour parcellisation)
    public ProofRequest() {
    }

    // Constructeur à partir des données détaillées (sauf date  = date du moment)
    public ProofRequest(int id, String filename, String hash, int statut, String tree, String txid, String info, String datedem) {
        this._id = id;
        this._hash = hash;
        this._filename = filename;
        this._statut = statut;
        this._tree = tree;
        this._txid = txid;
        this._info = info;
        if (datedem == null) {
            Calendar c = Calendar.getInstance();
            SimpleDateFormat df = new SimpleDateFormat("dd-MMM-yyyy");
            this._date_request = df.format(c.getTime());
        } else {
            this._date_request = datedem;
        }
    }

    // getters and setters
    public void set_id(int _id) {this._id = _id;}
    public void set_filename(String _filename) {this._filename = _filename;}
    public void set_hash(String _hash) {this._hash = _hash;}
    public void set_statut(int _statut) {this._statut = _statut;}
    public void set_tree(String _tree) {this._tree = _tree;}
    public void set_txid(String _txid) {this._txid = _txid;}
    public void set_info(String _info) {this._info = _info;}
    public void set_date_request(String _date_request) {this._date_request = _date_request;}

    public int get_id() {return _id;}
    public String get_filename() {return _filename;}
    public String get_hash() {return _hash;}
    public int get_statut() {return _statut;}
    public String get_tree() {return _tree;}
    public String get_txid() {return _txid;}
    public String get_info() {return _info;}
    public String get_date_request() {return _date_request;}

}