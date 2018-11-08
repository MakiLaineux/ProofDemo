package com.technoprimates.proofdemo.util;


/*
 * Created by JC on 21/10/2018.
 */
public class Constants {

    // Tag for Log messages
    public static final String TAG = "PROOF";

    // Name of directory on SD card
    public static final String DIRECTORY_LOCAL = "/DigitProof/";

    // Internal broadcast
    public static final String EVENT_REFRESH_UI = "com.technoprimates.proofdemo.EVENT_REFRESH_UI"; // pour broadcast interne à l'appli

    // Identifiers used in extras
    public static final String EXTRA_REQUEST_ID = "idbdd";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_PROOFFILENAME = "zipfilename";
    public static final String EXTRA_RECEIVER = "receiver";
    public static final String EXTRA_RESULT_VALUE = "resultValue";
    public static final String EXTRA_DISPLAY_PARAMS = "display_params";

    public static final int DISPLAY_PROOF_ONLY = 1;
    public static final int DISPLAY_AND_CHECK_PROOF = 2;


    // URLs
    // Server URLs
//    public static final String URL_DOWNLOAD_PROOF = "http://192.168.1.25/get_proof.php?instance=\'%1$s\'";
//    public static final String URL_UPLOAD_DEMANDE = "http://192.168.1.25/request_proof.php?instance=\'%1$s\'&idrequest=\'%2$d\'&hash=\'%3$s\'";
//    public static final String URL_SIGNOFF_PROOF = "http://192.168.1.25/signoff_proof.php?instance=\'%1$s\'&idrequest=\'%2$d\'";
    public static final String URL_DOWNLOAD_PROOF = "http://tp.troglophile.fr/get_proof.php?instance=\'%1$s\'";
    public static final String URL_UPLOAD_DEMANDE = "http://tp.troglophile.fr/request_proof.php?instance=\'%1$s\'&idrequest=\'%2$d\'&hash=\'%3$s\'";
    public static final String URL_SIGNOFF_PROOF = "http://tp.troglophile.fr/signoff_proof.php?instance=\'%1$s\'&idrequest=\'%2$d\'";

    // Block explorer API URLs
    public static final String URL_BASE_BTC_TESTNET = "https://api.blockcypher.com/v1/btc/test3/txs/";
    public static final String URL_BASE_BTC_MAINNET = "https://api.blockcypher.com/v1/btc/main/txs/";
    public static final String URL_BASE_BTC_LITECOIN = "https://api.blockcypher.com/v1/ltc/main/txs/";
    //    public static final String URL = "https://blockexplorer.com/api/tx/23805bb772db8b872c7cbf26eddfeda59dd2d33c407bd62811f9a9fb9ad3f274";
    //    public static final String URL = "https://api.blockcypher.com/v1/btc/test3/txs/3b6f8ae6724acf9662d6558776955c3208793b88b57ffb402c6fd01a7075e3c1";


    // Return codes used by services
    public static final int RETURN_PROOFREAD_OK = 1;
    public static final int RETURN_HASHCHECK_OK = 2;
    public static final int RETURN_TREECHECK_OK = 3;
    public static final int RETURN_TXLOAD_OK = 4;
    public static final int RETURN_TXCHECK_OK = 5;
    public static final int RETURN_HASH_OK = 6;
    public static final int RETURN_UPLOAD_OK = 7;
    public static final int RETURN_DOWNLOAD_OK = 8;
    public static final int RETURN_COPYANDHASH_OK = 9;
    public static final int RETURN_COPYFILE_OK = 10;
    public static final int RETURN_DBUPDATE_OK = 11;
    public static final int RETURN_ZIPFILE_OK = 12;

    public static final int RETURN_PROOFREAD_KO = 101;
    public static final int RETURN_HASHCHECK_KO = 102;
    public static final int RETURN_TREECHECK_KO = 103;
    public static final int RETURN_TXLOAD_KO = 104;
    public static final int RETURN_TXCHECK_KO = 105;
    public static final int RETURN_HASH_KO = 106;
    public static final int RETURN_UPLOAD_KO = 107;
    public static final int RETURN_DOWNLOAD_KO = 108;
    public static final int RETURN_COPYANDHASH_KO = 109;
    public static final int RETURN_COPYFILE_KO = 110;
    public static final int RETURN_DBUPDATE_KO = 111;
    public static final int RETURN_ZIPFILE_KO = 112;


    // Status codes (used for REQUEST status column)
    public static final int STATUS_ERROR = -1;
    public static final int STATUS_DELETED = -2;
    public static final int STATUS_INITIALIZED = 0;
    public static final int STATUS_HASH_OK = 1;
    public static final int STATUS_SUBMITTED = 2;
    public static final int STATUS_READY = 4;
    public static final int STATUS_FINISHED_ZIP = 5;
    public static final int STATUS_FINISHED_PDF = 6;
    public static final int STATUS_FINISHED_ALL = 7;  // all types of finished status
    public static final int STATUS_ALL = 8;


    // Database
    // General strings
    public static final int DB_VERSION = 1;
    public static final String DB_NAME = "proof.db";
    public static final String TABLE_REQUEST = "table_request";

    // DB columns
    public static final int REQUEST_NUM_COL_ID = 0;
    public static final int REQUEST_NUM_COL_FILENAME = 1;
    public static final int REQUEST_NUM_COL_HASH = 2;
    public static final int REQUEST_NUM_COL_TREE = 3;
    public static final int REQUEST_NUM_COL_TXID = 4;
    public static final int REQUEST_NUM_COL_INFO = 5;
    public static final int REQUEST_NUM_COL_STATUS = 6;
    public static final int REQUEST_NUM_COL_REQUEST_DATE = 7;
    public static final String REQUEST_COL_ID = "_id";
    public static final String REQUEST_COL_FILENAME = "filename";
    public static final String REQUEST_COL_HASH = "hash";
    public static final String REQUEST_COL_TREE = "tree";
    public static final String REQUEST_COL_TXID = "txid";
    public static final String REQUEST_COL_INFO = "info";
    public static final String REQUEST_COL_STATUS = "status";
    public static final String REQUEST_COL_REQUEST_DATE = "request_date";

    // Temporary REQUEST id before AUTOINCREMENT insert
    public static final int REQUEST_NOID = -1;

    // All REQUEST ids
    public static final int IDBDD_ALL = -1;


    // JSON Names used by the Block explorer
    public static final String BLOCK_EXPLORER_COL_TXID = "hash";
    public static final String BLOCK_EXPLORER_COL_NB_CONFIRM = "confirmations";
    public static final String BLOCK_EXPLORER_COL_DATE_CONFIRM = "confirmed";
    public static final String BLOCK_EXPLORER_COL_OUTPUTS = "outputs";
    public static final String BLOCK_EXPLORER_COL_DATA_HEX = "data_hex";


    // JSON names used by the server
    public static final String PROOF_COL_REQUEST = "request";
    public static final String PROOF_COL_STATUS = "status";
    public static final String PROOF_COL_CHAIN = "chain";
    public static final String PROOF_COL_TREE = "tree";
    public static final String PROOF_COL_TXID = "txid";
    public static final String PROOF_COL_INFO = "info";

    // Permissions management
    public static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1001;

    //Job ids
    public static final int JOB_SERVICE_PREPARE = 1;
    public static final int JOB_SERVICE_UPLOAD = 2;
    public static final int JOB_SERVICE_DOWNLOAD = 3;
    public static final int JOB_SERVICE_DISPLAY = 4;

}