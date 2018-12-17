package com.technoprimates.proofdemo.activities;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.technoprimates.proofdemo.R;
import com.technoprimates.proofdemo.db.DatabaseHandler;
import com.technoprimates.proofdemo.services.CheckService;
import static com.technoprimates.proofdemo.util.Constants.*;
import com.technoprimates.proofdemo.util.ProofUtils;
import com.technoprimates.proofdemo.util.ServiceResultReceiver;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class CheckActivity extends AppCompatActivity
        implements ServiceResultReceiver.Receiver {
    // Local Db to update
    private DatabaseHandler mDatabase;
    private Uri mFullProofUri = null; // Full uri of the proof file


    // UI elements
    private TextView mTvFileName, mTvDepositDate, mTvChecks, mTvDocHash, mTvMessage, mTvMixedHash, mTvTree, mTvRoot,
            mTvChain, mTvTxid, mTvDateConfirm, mTvNbConfirm, mTvOpReturnData;
    private CheckBox mCbProofLoad, mCbHashCheck, mCbTreeCheck, mCbTxLoad, mCbTxCheck;

    public ServiceResultReceiver mReceiver;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the receiver to manage communication from the service
        mReceiver = new ServiceResultReceiver(new Handler());
        mReceiver.setReceiver(this);

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (!Intent.ACTION_SEND.equals(action)) {
            finish(); // Handle only simple SEND intents
        }
        if ((!"application/pdf".equals(type)) &&
                !("application/zip".equals(type))) {
            finish(); // Handle only supported proof formats
        }

        setContentView(R.layout.activity_display);

        mTvFileName = findViewById(R.id.tv_filename_content);
        mTvDepositDate = findViewById(R.id.tv_display_deposit_date);
        mTvChecks = findViewById(R.id.tv_progress);
        mTvDocHash = findViewById(R.id.tv_dochash_content);
        mTvMessage = findViewById(R.id.tv_author_message_content);
        mTvMixedHash = findViewById(R.id.tv_mixed_hash_content);
        mTvTree = findViewById(R.id.tv_tree_content);
        mTvRoot = findViewById(R.id.tv_root_content);
        mTvChain = findViewById(R.id.tv_chain_content);
        mTvTxid = findViewById(R.id.tv_txid_content);
        mTvDateConfirm = findViewById(R.id.tv_confirm_date_content);
        mTvNbConfirm = findViewById(R.id.tv_nbconfirm_content);
        mTvOpReturnData = findViewById(R.id.tv_opreturn_content);

        mCbProofLoad = findViewById(R.id.cb_proofload);
        mCbHashCheck = findViewById(R.id.cb_hashcheck);
        mCbTreeCheck = findViewById(R.id.cb_treecheck);
        mCbTxLoad = findViewById(R.id.cb_txload);
        mCbTxCheck = findViewById(R.id.cb_txcheck);

        // initialize UI
//TODO : real name
        mTvDepositDate.setText("");
        mTvDepositDate.setVisibility(View.INVISIBLE);
        mTvDocHash.setText("");
        mTvMessage.setText("");
        mTvMixedHash.setText("");
        mTvTree.setText("");
        mTvRoot.setText("");
        mTvChain.setText("");
        mTvTxid.setText("");
        mTvDateConfirm.setText("");
        mTvNbConfirm.setText("");
        mTvOpReturnData.setText("");
        // Temporary display "loading"
        mTvChecks.setText(R.string.info_load_proof);

        mCbProofLoad.setChecked(false);
        mCbHashCheck.setChecked(false);
        mCbTreeCheck.setChecked(false);
        mCbTxLoad.setChecked(false);
        mCbTxCheck.setChecked(false);

        mCbProofLoad.setText(R.string.info_load_proof);
        mCbHashCheck.setText(R.string.info_check_hash);
        mCbTreeCheck.setText(R.string.info_check_merkletree);
        mCbTxLoad.setText(R.string.info_load_blockchain);
        mCbTxCheck.setText(R.string.info_check_opreturn);

        checkFile(intent); // Handle single file being sent
        mTvFileName.setText(ProofUtils.getFilename(this, mFullProofUri));// Display Filename, uri is known only after file checks
    }


    void checkFile(Intent intent) {
	    //TODO : further checks of the incoming data, like check file magic, file size ...
        // get full uri

        mFullProofUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (mFullProofUri != null) {
            // Calcul du hash, MAJ en BDD et recopie du fichier par un service
            Intent i = new Intent(CheckActivity.this, CheckService.class);
            // name of the proof file
            i.putExtra(EXTRA_PROOFFULLURI, mFullProofUri.toString());
            //receiver for service feedback
            i.putExtra(EXTRA_RECEIVER, mReceiver);
            startService(i);
        }
    }

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        Log.i(TAG, "CheckActivity resultCode: "+resultCode);
        switch (resultCode) {
            case RETURN_PROOFREAD_OK: {
                mCbProofLoad.setChecked(true);
                mCbProofLoad.setText(getString(R.string.check_done, mCbProofLoad.getText()));
                mTvTxid.setText(resultData.getString("txid"));
                mTvChain.setText(resultData.getString("chain"));
                mTvTree.setText(resultData.getString("tiers"));
                mTvRoot.setText(resultData.getString("root"));
                mTvDocHash.setText(resultData.getString("dochash"));
                mTvMessage.setText(resultData.getString("message"));
                mTvDocHash.setText(resultData.getString("overhash"));
                mTvChecks.setText(R.string.info_check_hash);
                break;
            }
            case RETURN_HASHCHECK_OK : {
                mCbHashCheck.setChecked(true);
                mCbHashCheck.setText(getString(R.string.check_done, mCbHashCheck.getText()));
                mTvChecks.setText(R.string.info_check_merkletree);
                break;
            }
            case RETURN_TREECHECK_OK : {
                mCbTreeCheck.setChecked(true);
                mCbTreeCheck.setText(getString(R.string.check_done,mCbTreeCheck.getText()));
                mTvChecks.setText(R.string.info_load_blockchain);
                break;
            }
            case RETURN_TXLOAD_OK: {
                mCbTxLoad.setChecked(true);
                mCbTxLoad.setText(getString(R.string.check_done, mCbTxLoad.getText()));
                try {
                    SimpleDateFormat serverFormat = new SimpleDateFormat( // for input date string
                            "yyyy-MM-dd'T'HH:mm:ss'Z'", // ISO8601 returned by block explorer
                            Locale.US); // ASCII
                    serverFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Date date = serverFormat.parse(resultData.getString("date"));
                    SimpleDateFormat userFormat = new SimpleDateFormat( // for display date string
                            "dd-MMM-yyyy HH:mm", // Display format
                            Locale.getDefault()); // User locale
                    String s = userFormat.format(date);
                    mTvDateConfirm.setText(s);
                } catch (ParseException e) {
                    Log.e(TAG, "Error parsing ISO 8601 date");
                }
                mTvNbConfirm.setText(getString(R.string.info_check_confirmations, resultData.getInt("nbconfirm")));
                mTvOpReturnData.setText(resultData.getString("opreturn_data"));
                mTvChecks.setText(R.string.info_check_opreturn);
                break;
            }
            case RETURN_TXCHECK_OK : {
                mCbTxCheck.setChecked(true);
                mCbTxCheck.setText(getString(R.string.check_done, mCbTxCheck.getText()));
                mTvChecks.setText(R.string.check_result_valid_proof);
                mTvDepositDate.setText(getString(R.string.info_deposit_date, mTvDateConfirm.getText()));
                mTvDepositDate.setVisibility(View.VISIBLE);
                break;
            }
            // Something is wrong, get cause and display it
            case RETURN_PROOFREAD_KO: {
                mCbProofLoad.setChecked(true);
                mCbProofLoad.setText(getString(R.string.check_failure, mCbProofLoad.getText()));
                mTvChecks.setText(resultData.getString("error"));
                break;
            }
            case RETURN_HASHCHECK_KO: {
                mCbHashCheck.setChecked(true);
                mCbHashCheck.setText(getString(R.string.check_failure, mCbHashCheck.getText()));
                mTvChecks.setText(resultData.getString("error"));
                break;
            }
            case RETURN_TREECHECK_KO: {
                mCbTreeCheck.setChecked(true);
                mCbTreeCheck.setText(getString(R.string.check_failure, mCbTreeCheck.getText()));
                mTvChecks.setText(resultData.getString("error"));
                break;
            }
            case RETURN_TXLOAD_KO: {
                mCbTxLoad.setChecked(true);
                mCbTxLoad.setText(getString(R.string.check_failure, mCbTxLoad.getText()));
                mTvChecks.setText(resultData.getString("error"));
                break;
            }
            case RETURN_TXCHECK_KO: {
                mCbTxCheck.setText(getString(R.string.check_failure, mCbTxCheck.getText()));
                mTvChecks.setText(resultData.getString("error"));
                break;
            }
            //TODO : manage other failed checks, like no internet connection, block explorer cannot be reached, ...

            default:
                break;
        }
    }


    @Override
    protected void onResume() {
        mReceiver.setReceiver(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        mReceiver.setReceiver(null);
        super.onPause();
    }
}



