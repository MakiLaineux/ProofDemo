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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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

import com.technoprimates.proofdemo.services.DownloadService;
import com.technoprimates.proofdemo.services.PrepareService;
import com.technoprimates.proofdemo.util.Constants;
import com.technoprimates.proofdemo.util.VisuProofListener;
import com.technoprimates.proofdemo.util.ServiceResultReceiver;
import com.technoprimates.proofdemo.adapters.RequestAdapter;
import com.technoprimates.proofdemo.R;
import com.technoprimates.proofdemo.services.UploadService;


import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class RequestListActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, ServiceResultReceiver.Receiver, VisuProofListener {

    // Adapter for the recyclerview
    private RequestAdapter mAdapter;

    // Display Type. Current activity manages all display types
    // Default display type is displaying all requests (STATUS_ALL)
    private int mDisplayType = Constants.STATUS_ALL;

    // Return code for file selection
    private static final int PICKFILE_RESULT_CODE = 1;

    // Callback for services feedback
    public ServiceResultReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(Constants.TAG, "--- RequestListActivity      --- onCreate");
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
                    Constants.MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }

        // End of onCreate
        // TODO : automatic start of downloadRequests service elsewhere
    }

    // Activity life cycle

    @Override
    protected void onResume() {
        // register callback for services and db feedback
        Log.d(Constants.TAG, "--- RequestListActivity      --- onResume");
        mReceiver.setReceiver(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        // unregister callback to avoid memory leaks
        Log.d(Constants.TAG, "--- RequestListActivity      --- onPause");
        mReceiver.setReceiver(null);
        super.onPause();
    }

    @Override
    protected void onStart() {
        Log.d(Constants.TAG, "--- RequestListActivity      --- onStart");
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.d(Constants.TAG, "--- RequestListActivity      --- onStop");
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(Constants.TAG, "--- RequestListActivity      --- onSaveInstanceState");
    // Save display type
        super.onSaveInstanceState(outState);
        outState.putInt("DISPLAY_TYPE", mDisplayType);
    }

    @Override
    public void onBackPressed() {
        Log.d(Constants.TAG, "--- RequestListActivity      --- onBackPressed");
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
        Log.d(Constants.TAG, "--- RequestListActivity      --- onOptionsItemSelected");
        View v;
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                displaySnackbarWithId(R.string.snackbar_settings, R.string.snackbar_noaction, null);
                break;
            case R.id.action_download: // Réception en provenance du serveur
                downloadRequests();
                break;
            case R.id.action_upload: // Envoi vers le serveur
                uploadRequests();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        // finish the activity if permission was not granted
        switch (requestCode) {
            case Constants.MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
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
            mDisplayType = Constants.STATUS_FINISHED_OK;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mDisplayType);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_soumises) {
            mDisplayType = Constants.STATUS_SUBMITTED;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mDisplayType);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_preparees) {
            mDisplayType = Constants.STATUS_HASH_OK;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mDisplayType);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_suppr) {
            mDisplayType = Constants.STATUS_DELETED;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mDisplayType);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_all) {
            mDisplayType = Constants.STATUS_ALL;
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
        Log.d(Constants.TAG, "--- RequestListActivity      --- visuProof");
        Intent intent = new Intent(this, DisplayAndCheckActivity.class);
        intent.putExtra(Constants.EXTRA_PROOFFILENAME, proofFilename);

        // Start the Display and check activity.
        startActivity(intent);
    }


    // If Floating action buton is pushed, select a file using the Storage Access Framework (API 19+)
    public void selectFileAndBuildRequest(){
        Log.d(Constants.TAG, "--- RequestListActivity      --- selectFileAndBuildRequest");

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
        Log.d(Constants.TAG, "--- RequestListActivity      --- uploadRequests");
        Intent i = new Intent(this, UploadService.class);
        //receiver for service feedback
        i.putExtra(Constants.EXTRA_RECEIVER, mReceiver);
        i.putExtra(Constants.EXTRA_REQUEST_ID, Constants.IDBDD_ALL);
        startService(i);
        displaySnackbarWithId(R.string.snackbar_lancement_upload, R.string.snackbar_noaction, null);
    }

    // Download ready proofs
    void downloadRequests(){
        Log.d(Constants.TAG, "--- RequestListActivity      --- downloadRequests");
        Intent i = new Intent(this, DownloadService.class);
        //receiver for service feedback
        i.putExtra(Constants.EXTRA_RECEIVER, mReceiver);
        startService(i);
        displaySnackbarWithId(R.string.snackbar_lancement_download, R.string.snackbar_noaction, null);
    }

    // a file was selected, initialize the request
    public void prepareRequest(String stringUri){
        Log.d(Constants.TAG, "--- RequestListActivity      --- prepareRequest");
        // Start a service to make a copy of the file and compute its SHA-256 hash
        Intent i = new Intent(this, PrepareService.class);
        i.putExtra(Constants.EXTRA_FILENAME, stringUri);  // string uri of file to handle
        i.putExtra(Constants.EXTRA_RECEIVER, mReceiver);   //receiver for service feedback
        startService(i);
    }


    // If a file was selected by the user, prepare the request
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        Log.d(Constants.TAG, "--- RequestListActivity      --- onActivityResult");
        if (resultData == null){
            displaySnackbarWithId(R.string.snackbar_no_file_selected, R.string.snackbar_noaction, null);
            return;
        }
        switch (requestCode) {
            // PICKFILE_RESULT_CODE : User should have selected a file
            case PICKFILE_RESULT_CODE:
                if (resultCode == RESULT_OK) {
                    // The returned intent contents a URI to the document
                    Uri uri = resultData.getData();
                    prepareRequest(uri.toString());
                    break;
                }
            default:
                displaySnackbarWithId(R.string.snackbar_errcode, R.string.snackbar_noaction, null);
                break;
        }
    }


    @Override
    // Services feedbacks
    public void onReceiveResult(int resultCode, Bundle resultData) {
        Log.d(Constants.TAG, "--- RequestListActivity      --- onReceiveResult");
        switch (resultCode) {
            case Constants.RETURN_COPYANDHASH_OK:
                // Copy and hash are OK, chain with Uploading service
                // db id to deal with
                int idBdd = resultData.getInt(Constants.EXTRA_REQUEST_ID);
                Log.d(Constants.TAG, "         Data : idbdd="+idBdd);
                Intent i = new Intent(this, UploadService.class);
                // store the receiver in extra
                i.putExtra(Constants.EXTRA_RECEIVER, mReceiver);
                // store the db id in extra
                i.putExtra(Constants.EXTRA_REQUEST_ID, idBdd);
                // Start the service
                Log.d(Constants.TAG, "       RETURN_COPYANDHASH_OK, starting Upload for 1 record");
                startService(i);
                // DB was changed, update UI
                mAdapter.loadData(mDisplayType);
                mAdapter.notifyDataSetChanged();
                break;
            case Constants.RETURN_DBUPDATE_OK:
            case Constants.RETURN_DOWNLOAD_OK:
                // DB was changed, update UI
                mAdapter.loadData(mDisplayType);
                mAdapter.notifyDataSetChanged();
                break;
            case Constants.RETURN_COPYANDHASH_KO:
                break;
            case Constants.RETURN_COPYFILE_KO:
                break;
            case Constants.RETURN_DBUPDATE_KO:
                break;
            case Constants.RETURN_ZIPFILE_KO:
                break;
            default:
                break;
        }
    }

    private String getCollapsingSubTitle(){
        switch (mDisplayType){
            case Constants.STATUS_ALL: return(getResources().getString(R.string.titre_all));
            case Constants.STATUS_FINISHED_OK: return(getResources().getString(R.string.titre_finished_ok));
            case Constants.STATUS_SUBMITTED: return(getResources().getString(R.string.titre_submitted));
            case Constants.STATUS_HASH_OK: return(getResources().getString(R.string.titre_prepared));
            case Constants.STATUS_DELETED: return(getResources().getString(R.string.titre_suppressed));
        }
        return null;
    }

    public void displaySnackbarWithId(int idTextMsg, int idTextAction, View.OnClickListener listener) {
        View v = findViewById(R.id.clayout);
        Snackbar.make(v, idTextMsg, Snackbar.LENGTH_SHORT)
                .setActionTextColor(Color.YELLOW)
                .setAction(idTextAction, listener).show();
    }

    public void displaySnackbarWithString(String text, int idTextAction, View.OnClickListener listener) {
        View v = findViewById(R.id.clayout);
        Snackbar.make(v, text, Snackbar.LENGTH_LONG)
                .setActionTextColor(Color.YELLOW)
                .setAction(idTextAction, listener).show();
    }
}
