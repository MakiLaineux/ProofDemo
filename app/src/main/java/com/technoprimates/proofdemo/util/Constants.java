package com.technoprimates.proofdemo.util;


/*
 * Created by JC on 21/10/2018.
 */
public final class Constants {

    private Constants() {} // no instantiation allowed

    // Tag for Log messages
    public static final String TAG = "PROOF";

    // Name of directory on SD card
    public static final String DIRECTORY_LOCAL = "/DigitProof/";

    // Internal broadcast
    public static final String EVENT_REFRESH_UI = "com.technoprimates.proofdemo.EVENT_REFRESH_UI"; // pour broadcast interne à l'appli

    // Identifiers used in extras
    public static final String EXTRA_REQUEST_ID = "idbdd";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_PROOFFULLURI = "prooffulluri";
    public static final String EXTRA_RECEIVER = "receiver";
    public static final String EXTRA_RESULT_VALUE = "resultValue";
    public static final String EXTRA_WORK_TYPE = "worktype";

    // URLs
    // Server URLs
//    public static final String URL_DOWNLOAD_PROOF = "http://192.168.1.25/get_proof.php?instance=\'%1$s\'";
//    public static final String URL_UPLOAD_DEMANDE = "http://192.168.1.25/request_proof.php?instance=\'%1$s\'&idrequest=\'%2$d\'&hash=\'%3$s\'";
//    public static final String URL_SIGNOFF_PROOF = "http://192.168.1.25/signoff_proof.php?instance=\'%1$s\'&idrequest=\'%2$d\'";
    public static final String URL_DOWNLOAD_PROOF = "http://technoprimates.com/get_proof.php?instance=\'%1$s\'";
    public static final String URL_UPLOAD_DEMANDE = "http://technoprimates.com/request_proof.php?instance=\'%1$s\'&idrequest=\'%2$d\'&hash=\'%3$s\'";
    public static final String URL_SIGNOFF_PROOF = "http://technoprimates.com/signoff_proof.php?instance=\'%1$s\'&idrequest=\'%2$d\'";

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
    public static final int RETURN_DOWNLOAD_OK = 8;
    public static final int RETURN_UPLOAD_OK = 9;
    public static final int RETURN_DBUPDATE_OK = 11;
    public static final int RETURN_PREPARE_OK = 13;

    public static final int RETURN_PROOFREAD_KO = 101;
    public static final int RETURN_HASHCHECK_KO = 102;
    public static final int RETURN_TREECHECK_KO = 103;
    public static final int RETURN_TXLOAD_KO = 104;
    public static final int RETURN_TXCHECK_KO = 105;
    public static final int RETURN_DOWNLOAD_KO = 108;
    public static final int RETURN_UPLOAD_KO = 109;
    public static final int RETURN_DBUPDATE_KO = 111;
    public static final int RETURN_PREPARE_KO = 113;


    // Status codes (used for REQUEST status column)
    public static final int STATUS_ERROR = -1;
    public static final int STATUS_DELETED = -2;
    public static final int STATUS_INITIALIZED = 0;
    public static final int STATUS_HASH_OK = 1;
    public static final int STATUS_SUBMITTED = 2;
    public static final int STATUS_READY = 4;
    public static final int STATUS_FINISHED = 5;
    public static final int STATUS_ALL = 8;

    // Protocol version
    public static final String STATEMENT_SYNTAX_VERSION = "1.0";
    // Database
    // General strings
    public static final int DB_VERSION = 1;
    public static final String DB_NAME = "proof.db";
    public static final String TABLE_REQUEST = "table_request";

    // DB columns
    public static final int REQUEST_NUM_COL_ID = 0;
    public static final int REQUEST_NUM_COL_FILENAME = 1;
    public static final int REQUEST_NUM_COL_FILETYPE = 2;
    public static final int REQUEST_NUM_COL_MESSAGE = 3;
    public static final int REQUEST_NUM_COL_DOC_HASH = 4;
    public static final int REQUEST_NUM_COL_OVER_HASH = 5;
    public static final int REQUEST_NUM_COL_STATUS = 6;
    public static final int REQUEST_NUM_COL_REQUEST_DATE = 7;
    public static final String REQUEST_COL_ID = "_id";
    public static final String REQUEST_COL_FILENAME = "filename";
    public static final String REQUEST_COL_FILETYPE = "type";
    public static final String REQUEST_COL_MESSAGE = "message";
    public static final String REQUEST_COL_DOC_HASH = "hash";
    public static final String REQUEST_COL_OVER_HASH = "mixed";
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
    public static final int TASK_NOTASK = 0;
    public static final int TASK_PREPARE = 1;
    public static final int TASK_UPLOAD = 2;
    public static final int JOB_SERVICE_DOWNLOAD = 4;
    public static final int JOB_SERVICE_SUBMIT = 5;

    public static final int VARIANT_PDF = 1;
    public static final int VARIANT_ZIP = 2;

    // Return codes for activities
    public static final int PICKFILE_RESULT_CODE = 1;
    public static final int MESSAGE_RESULT_CODE = 2;
}