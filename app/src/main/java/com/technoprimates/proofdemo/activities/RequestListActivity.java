package com.technoprimates.proofdemo.activities;

/*
 * Main activity of ProofRequest
 *
 * - displays a recyclerview containing proof requests with their current status.
 * User can limit the display to requests with one particular status, using the Navigation Drawer
 *
 * - User can click on a request in status "finished" to display the proof details. This launches an
 * Activity
 *
 * - User can click on Floating action button to create a new proof request and uploadRequests it to the server. This launches an IntentService
 *
 * - User can click on Download (app bar button) to downloadRequests any completed proofs available on the server. This launches an
 * IntentService that is also launched when the app starts
 *
 * - click on Upload (app bar button) to force uploading requests to the server. This launches a IntentService that is also
 * launched after each request's creation. Therefore the uploadRequests button is only useful when some initial uploads did not complete
 * for some reason, e.g. if there was no internet connectivity
 *
 * JC october 2018
 */

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.technoprimates.proofdemo.services.DownloadService;
import com.technoprimates.proofdemo.services.SubmitService;
import static com.technoprimates.proofdemo.util.Constants.*;
import com.technoprimates.proofdemo.util.VisuProofListener;
import com.technoprimates.proofdemo.util.ServiceResultReceiver;
import com.technoprimates.proofdemo.adapters.RequestAdapter;
import com.technoprimates.proofdemo.R;

import java.io.File;

public class RequestListActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, ServiceResultReceiver.Receiver, VisuProofListener {

    // Adapter for the recyclerview
    private RequestAdapter mAdapter;

    // Display Type. Current activity manages all display types
    // Default display type is displaying all requests (STATUS_ALL)
    private int mDisplayType = STATUS_ALL;

    // Callback for services feedback
    public ServiceResultReceiver mReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "--- RequestListActivity      --- onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_objet_list);

        // Restore display type if it was previously saved
        if (savedInstanceState != null){
            mDisplayType = savedInstanceState.getInt("DISPLAY_TYPE");
        }

        // collapsing Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ViewCompat.setTransitionName(findViewById(R.id.appBarLayout), "Name");
        CollapsingToolbarLayout collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar);
        collapsingToolbarLayout.setTitle(getResources().getString(R.string.app_name));
        collapsingToolbarLayout.setExpandedTitleColor(ContextCompat.getColor(this, R.color.colorTextSecondary));

        // collapsing toolbar subtitle
        TextView collapsingSubtitle = findViewById(R.id.subtitle);
        collapsingSubtitle.setText(getCollapsingSubTitle());

        // floating action button, user selects a file on the device and a request is build
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.show();
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectFileAndBuildRequest();
            }
        });

        // navigation drawer
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // RecyclerView initialization
        RecyclerView recyclerView  = (RecyclerView)findViewById(R.id.rv_objet);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this)); // basic layout manager

        // Receiver for services feedbacks
        mReceiver = new ServiceResultReceiver(new Handler());
        mReceiver.setReceiver(this);


        // Adapter for the RecyclerView
        mAdapter = new RequestAdapter(this, mDisplayType);
        mAdapter.setVisuProofListener(this); // Bind the listener, used to display Proof details (method visuProof)
        recyclerView.setAdapter(mAdapter);

        // Permissions : App needs WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request permission, finish if not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }

        // Register the receiver of local broadcasts
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter(EVENT_REFRESH_UI));


        // End of onCreate
        // TODO : automatic start of downloadRequests service elsewhere
    }

    //Receiver for Local Broadcasts
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.w(TAG, " Receiving UI broadcast.");
            if (mAdapter != null){
                mAdapter.loadData(mDisplayType);
                mAdapter.notifyDataSetChanged();
            }
        }
    };



    // Activity life cycle

    @Override
    protected void onResume() {
        // register receivers
        Log.d(TAG, "--- RequestListActivity      --- onResume");
        mReceiver.setReceiver(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        // unregister receivers
        Log.d(TAG, "--- RequestListActivity      --- onPause");
        mReceiver.setReceiver(null);
        super.onPause();
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "--- RequestListActivity      --- onStart");
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "--- RequestListActivity      --- onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "--- RequestListActivity      --- onDestroy");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "--- RequestListActivity      --- onSaveInstanceState");
    // Save display type
        super.onSaveInstanceState(outState);
        outState.putInt("DISPLAY_TYPE", mDisplayType);
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "--- RequestListActivity      --- onBackPressed");
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.objet_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "--- RequestListActivity      --- onOptionsItemSelected");
        Intent intent;
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                intent = new Intent(this, MessageActivity.class);
                startActivityForResult(intent, MESSAGE_RESULT_CODE);
                return true;
            case R.id.action_download: // Réception en provenance du serveur
                downloadRequests();
                return true;
            case R.id.action_upload: // Envoi vers le serveur
                uploadRequests();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        // finish the activity if permission was not granted
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length == 0
                        || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // permission was not granted, stop
                    finish();
                }
                //permission was granted, go on
                return;
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        CollapsingToolbarLayout collapsingToolbarLayout = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        int id = item.getItemId();

        // Affichage sous-titre collapsing
        TextView collapsingSubtitle = (TextView) findViewById(R.id.subtitle);

        if (id == R.id.nav_ok) {
            mDisplayType = STATUS_FINISHED;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mDisplayType);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_soumises) {
            mDisplayType = STATUS_SUBMITTED;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mDisplayType);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_preparees) {
            mDisplayType = STATUS_HASH_OK;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mDisplayType);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_suppr) {
            mDisplayType = STATUS_DELETED;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mDisplayType);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_all) {
            mDisplayType = STATUS_ALL;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mDisplayType);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_avis) {
            displaySnackbarWithId(R.string.snackbar_avis, R.string.snackbar_noaction, null);

        } else if (id == R.id.nav_info) {
            displaySnackbarWithId(R.string.snackbar_apropos, R.string.snackbar_noaction, null);

        } else if (id == R.id.nav_aide) {
            displaySnackbarWithId(R.string.snackbar_aide, R.string.snackbar_noaction, null);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


    // User request : launch activity displaying and checking proof's details
    // case zip : proofFilename is the relative name of the zip file
    public void visuProof(String proofFilename) {
        Log.d(TAG, "--- RequestListActivity      --- visuProof");
        Intent intent = new Intent(this, DisplayAndCheckActivity.class);

        // put full uri of proof file
        File proofFile = new File(Environment.getExternalStorageDirectory() + DIRECTORY_LOCAL + proofFilename);
        intent.putExtra(EXTRA_PROOFFULLURI, Uri.fromFile(proofFile).toString());

        // Start the check activity.
        startActivity(intent);
    }


    // If Floating action buton is pushed, select a file using the Storage Access Framework (API 19+)
    public void selectFileAndBuildRequest(){
        Log.d(TAG, "--- RequestListActivity      --- selectFileAndBuildRequest");

        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file browser.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // No MIME filter, search for all documents available via installed storage providers
        intent.setType("*/*");
        startActivityForResult(intent, PICKFILE_RESULT_CODE);
    }

    // Upload all prepared requests
    void uploadRequests(){
        Log.d(TAG, "--- RequestListActivity      --- uploadRequests");
        Intent i = new Intent();
        // store the receiver in extra
        i.putExtra(EXTRA_RECEIVER, mReceiver);
        i.putExtra(EXTRA_REQUEST_ID, IDBDD_ALL);
        SubmitService.enqueueWork(this, TASK_UPLOAD, i);

        displaySnackbarWithId(R.string.snackbar_lancement_upload, R.string.snackbar_noaction, null);
    }

    // Download ready proofs
    void downloadRequests(){
        Log.d(TAG, "--- RequestListActivity      --- downloadRequests");
        Intent i = new Intent();
        //receiver for service feedback
        i.putExtra(EXTRA_RECEIVER, mReceiver);
        DownloadService.enqueueWork(this, i);

        displaySnackbarWithId(R.string.snackbar_lancement_download, R.string.snackbar_noaction, null);
    }

    // a file was selected, initialize the request
    public void prepareRequest(String stringUri){
        Log.d(TAG, "--- RequestListActivity      --- prepareRequest");
        // Start a service to make a copy of the file and compute its SHA-256 hash
        Intent i = new Intent();
        i.putExtra(EXTRA_FILENAME, stringUri);  // string uri of file to handle
        //receiver for service feedback
        i.putExtra(EXTRA_RECEIVER, mReceiver);
        SubmitService.enqueueWork(this, TASK_PREPARE, i);
    }


    // If a file was selected by the user, prepare the request
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        Log.d(TAG, "--- RequestListActivity      --- onActivityResult");
        switch (requestCode) {
            // Returning from file selection
            case PICKFILE_RESULT_CODE:
                if (resultData == null){ // No file selected
                    displaySnackbarWithId(R.string.snackbar_no_file_selected, R.string.snackbar_noaction, null);
                    break;
                }
                if (resultCode == RESULT_OK) {
                    // The returned intent contents a URI to the document
                    Uri uri = resultData.getData();
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    prepareRequest(uri.toString());
                    break;
                }
            // Returning from Message edition
            case MESSAGE_RESULT_CODE:
                if (resultCode == RESULT_OK) {
                    // Toast the success
                    Toast.makeText(this, "Proof author message was modified", Toast.LENGTH_SHORT).show();
                    break;
                } else {
                    Toast.makeText(this, "No modification performed", Toast.LENGTH_SHORT).show();
                }
            default:
                displaySnackbarWithId(R.string.snackbar_errcode, R.string.snackbar_noaction, null);
                break;
        }
    }


    @Override
    // Services feedbacks
    public void onReceiveResult(int resultCode, Bundle resultData) {
        switch (resultCode) {
            case RETURN_PREPARE_OK:
            case RETURN_DBUPDATE_OK:
            case RETURN_DOWNLOAD_OK:
            case(RETURN_UPLOAD_OK) :
            case(RETURN_UPLOAD_KO) : // uodate UI
                mAdapter.loadData(mDisplayType);
                mAdapter.notifyDataSetChanged();
                break;
            case RETURN_DBUPDATE_KO:
            default:
                break; // Do nothing
        }
    }

    private String getCollapsingSubTitle(){
        switch (mDisplayType){
            case STATUS_ALL: return(getResources().getString(R.string.titre_all));
            case STATUS_FINISHED: return(getResources().getString(R.string.titre_finished_ok));
            case STATUS_SUBMITTED: return(getResources().getString(R.string.titre_submitted));
            case STATUS_HASH_OK: return(getResources().getString(R.string.titre_prepared));
            case STATUS_DELETED: return(getResources().getString(R.string.titre_suppressed));
        }
        return null;
    }

    public void displaySnackbarWithId(int idTextMsg, int idTextAction, View.OnClickListener listener) {
        View v = findViewById(R.id.clayout);
        Snackbar.make(v, idTextMsg, Snackbar.LENGTH_SHORT)
                .setActionTextColor(Color.YELLOW)
                .setAction(idTextAction, listener).show();
    }
}
