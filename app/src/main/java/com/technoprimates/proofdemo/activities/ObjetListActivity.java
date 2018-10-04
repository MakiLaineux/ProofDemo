package com.technoprimates.proofdemo.activities;

/**
 * Activité principale, gère une recyclerview présentant les demandes
 * JC Aout 2016
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
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

import com.technoprimates.proofdemo.db.DatabaseHandler;
import com.technoprimates.proofdemo.db.ProofRequest;
import com.technoprimates.proofdemo.services.DownloadService;
import com.technoprimates.proofdemo.util.FileUtils;
import com.technoprimates.proofdemo.util.Globals;
import com.technoprimates.proofdemo.services.CopyAndHashService;
import com.technoprimates.proofdemo.util.VisuProofListener;
import com.technoprimates.proofdemo.util.MyResultReceiver;
import com.technoprimates.proofdemo.adapters.ObjetAdapter;
import com.technoprimates.proofdemo.R;
import com.technoprimates.proofdemo.services.UploadService;


import java.io.File;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class ObjetListActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, MyResultReceiver.Receiver, VisuProofListener {

    // Adapter pour la recyclerview
    private ObjetAdapter mAdapter;

    // Type d'affichage. Une seule Activité pour tous les modes d'affichage
    // Par défaut : affichage de tout (STATUS_ALL)
    private int mTypeAffichage = Globals.STATUS_ALL;

    // Gestion des permissions
    // Il est obligatoire d'avoir WRITE_EXTERNAL_STORAGE sinon exit
    private static final String[] PERMS_LIST_1 = {WRITE_EXTERNAL_STORAGE}; // Liste numéro 1 de permissions dangereuses
    private static final int RESULT_PERMS_LIST_1 = 1; // identifiant de la liste numéro 1 pour les demandes

    // Code retour pour la sélection de fichier
    private static final int PICKFILE_RESULT_CODE = 1;

    // Callback de réception des infos informations envoées par les services
    public MyResultReceiver mReceiver;

    // TODO : voir si possibilité de  fusionner avec la précédente
    // Callback appelée pour rafraichir l'UI après les MAJ de la BDD
    private BroadcastReceiver mBroadcastReceiverRefreshUI = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(Globals.TAG, "--- ObjetListActivity      --- onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_objet_list);

        // Gestion collapsing Toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ViewCompat.setTransitionName(findViewById(R.id.appBarLayout), "Name");
        CollapsingToolbarLayout collapsingToolbarLayout = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        collapsingToolbarLayout.setTitle(getResources().getString(R.string.app_name));
        collapsingToolbarLayout.setExpandedTitleColor(ContextCompat.getColor(this, R.color.colorTextSecondary));

        // Affichage sous-titre collapsing
        TextView collapsingSubtitle = (TextView) findViewById(R.id.subtitle);
        collapsingSubtitle.setText(getCollapsingSubTitle());

        // Gestion floating button
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.show();
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseFile();
            }
        });

        // Gestion navigation drawer
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Gestion RecyclerView (JC)
        // initialisation recyclerview
        RecyclerView recyclerView  = (RecyclerView)findViewById(R.id.rv_objet);
        recyclerView.setHasFixedSize(true);

        // layout manager basique pour la recyclerview
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Création de l'adapter
        mAdapter = new ObjetAdapter(mTypeAffichage);
        recyclerView.setAdapter(mAdapter);
        mAdapter.setVisuProofListener(this); // Bind the listener

        // Mise en place du receiver pour la communication en provenance des services
        mReceiver = new MyResultReceiver(new Handler());
        mReceiver.setReceiver(this);

        // Réception des broadcast locaux après MAJ de la base ou fin de synchro
        // Il faut alors rafraichir la RecyclerView
        mBroadcastReceiverRefreshUI = new BroadcastReceiver() {
            @Override
            public void onReceive (Context context, Intent intent) {
                Log.w(Globals.TAG, "????????????????? Receiving UI broadcast.");
                mAdapter.loadData(mTypeAffichage);
                mAdapter.notifyDataSetChanged();
            }
        };

        // Si premier run et API >= M, demande d'habilitation (numéro 1)
        if (isFirstRun() && (useRuntimePermissions())) {
                requestPermissions(PERMS_LIST_1, RESULT_PERMS_LIST_1);
        }

        // WRITE_EXTERNAL_STORAE est obligatoire
        View v = findViewById(R.id.clayout);
        if (!hasPermission(WRITE_EXTERNAL_STORAGE)) {
            if (shouldShowWriteRationale()) {
                Snackbar.make(v, R.string.snackbar_blabla_permission_synchro, Snackbar.LENGTH_LONG)
                        .setActionTextColor(Color.YELLOW).setAction(R.string.snackbar_request_perm, clickListenerRequestPerm).show();

            } else {
                Snackbar.make(v, R.string.snackbar_manque_permission_synchro, Snackbar.LENGTH_LONG)
                        .setActionTextColor(Color.YELLOW).setAction("Action", null).show();
                finish();
            }
        }

        // Au lancement : synchro serveur
        demandeDownload();
        demandeUpload();
    }

    // gestion des permissions (pour API >= M)
    // vérification si on est en gestion des permisions dangerereuses au run (à partir de l'API M)
    private boolean useRuntimePermissions () {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
    }

    // Identification si on est au premier run avec les sharedPreferences
    private boolean isFirstRun(){
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(Globals.context);
        boolean result = sharedPref.getBoolean("firstrun", true);
        if (result) {
            sharedPref.edit().putBoolean("firstrun", false).apply();
        }
        return result;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    }

    // Vérification si une permission est donnée
    private boolean hasPermission (String perm) {
        if (useRuntimePermissions()) {
            return(checkSelfPermission(perm)== PackageManager.PERMISSION_GRANTED);
        }
        return true; // Si API < M jamais de pb la permission est dans le manifeste
    }

    // Faut-il expliquer pourquoi la permission
    private boolean shouldShowRationale(String perm) {
        if (useRuntimePermissions()) return(!hasPermission(perm)&&shouldShowRequestPermissionRationale(perm));
        return false;
    }
    private boolean shouldShowWriteRationale() {
        return(shouldShowRationale(WRITE_EXTERNAL_STORAGE));
    }
    // Callback Request Permission
    final View.OnClickListener clickListenerRequestPerm = new View.OnClickListener() {
        public void onClick(View v) {
            requestPermissions(PERMS_LIST_1, RESULT_PERMS_LIST_1);
        }
    };


    @Override
    protected void onResume() {
        // enregistrement callback de MAJ de la BDD ou fin de synchro
        Log.d(Globals.TAG, "--- ObjetListActivity      --- onResume");
        LocalBroadcastManager.getInstance(this).registerReceiver((mBroadcastReceiverRefreshUI),
                new IntentFilter(Globals.MAJ_BDD));
        mReceiver.setReceiver(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(Globals.TAG, "--- ObjetListActivity      --- onPause");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiverRefreshUI);
        mReceiver.setReceiver(null);
        super.onPause();
    }

    @Override
    protected void onStart() {
        Log.d(Globals.TAG, "--- ObjetListActivity      --- onStart");
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.d(Globals.TAG, "--- ObjetListActivity      --- onStop");
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        Log.d(Globals.TAG, "--- ObjetListActivity      --- onBackPressed");
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
        Log.d(Globals.TAG, "--- ObjetListActivity      --- onOptionsItemSelected");
        View v;
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                displaySnackbarWithId(R.string.snackbar_settings, R.string.snackbar_noaction, null);
                break;
            case R.id.action_download: // Réception en provenance du serveur
                demandeDownload();
                break;
            case R.id.action_upload: // Envoi vers le serveur
                demandeUpload();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        CollapsingToolbarLayout collapsingToolbarLayout = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        int id = item.getItemId();

        // Affichage sous-titre collapsing
        TextView collapsingSubtitle = (TextView) findViewById(R.id.subtitle);

        if (id == R.id.nav_ok) {
            mTypeAffichage = Globals.STATUS_FINISHED_OK;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mTypeAffichage);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_soumises) {
            mTypeAffichage = Globals.STATUS_SUBMITTED;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mTypeAffichage);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_preparees) {
            mTypeAffichage = Globals.STATUS_HASH_OK;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mTypeAffichage);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_suppr) {
            mTypeAffichage = Globals.STATUS_DELETED;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mTypeAffichage);
            mAdapter.notifyDataSetChanged();

        } else if (id == R.id.nav_all) {
            mTypeAffichage = Globals.STATUS_ALL;
            collapsingSubtitle.setText(getCollapsingSubTitle());
            mAdapter.loadData(mTypeAffichage);
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




    // Clic court : lancement Activité pour affichage des détails sur la requete cliquée
    public void showProof(String zipName, String zipEntryName) {
        Log.d(Globals.TAG, "--- ObjetListActivity      --- showProof");
        // Dezippage de l'entrée preuve dans unfichier générique "fichier preuve"
        FileUtils.unpackZipEntry(zipName, zipEntryName);

        // Visualisation
        Intent intent = new Intent(Intent.ACTION_VIEW);
        File tmpFile = new File(
                Environment.getExternalStorageDirectory() + Globals.DIRECTORY_LOCAL, "fichier preuve");
        Boolean b = tmpFile.exists();
        Log.d(Globals.TAG, b.toString());
        Uri u = Uri.fromFile(tmpFile);
        Log.d(Globals.TAG, u.toString());
        intent.setDataAndType(Uri.fromFile(tmpFile), "text/*");
        startActivity(intent);
    }


    // If Floating action buton is pushed, select a file using the Storage Access Framework (API 19+)
    public void chooseFile(){
        Log.d(Globals.TAG, "--- ObjetListActivity      --- chooseFile");

        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
        // browser.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Filter to show only images, using the image MIME data type.
        // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
        // To search for all documents available via installed storage providers,
        // it would be "*/*".
        intent.setType("*/*");

        startActivityForResult(intent, PICKFILE_RESULT_CODE);
    }

    void demandeUpload(){
        Log.d(Globals.TAG, "--- ObjetListActivity      --- demandeUpload");
        // Lancement du service d'envoi au serveur des demandes préparées
        Intent i = new Intent(this, UploadService.class);
        //passage au service d'un receiver pour informer en retour l'activité
        i.putExtra(Globals.SERVICE_RECEIVER, mReceiver);
        // passage du numero d'enregistrement à traiter : ici, tous
        i.putExtra(Globals.SERVICE_IDBDD, Globals.IDBDD_ALL);
        // Start the service
        startService(i);
        displaySnackbarWithId(R.string.snackbar_lancement_upload, R.string.snackbar_noaction, null);
    }

    // Demande synchro
    void demandeDownload(){
        Log.d(Globals.TAG, "--- ObjetListActivity      --- demandeDownload");
        // Lancement du service de réception des preuves dispo sur le serveur
        Intent i = new Intent(this, DownloadService.class);
        //passage au service du receiver
        i.putExtra(Globals.SERVICE_RECEIVER, mReceiver);
        // Start the service
        startService(i);
        displaySnackbarWithId(R.string.snackbar_lancement_download, R.string.snackbar_noaction, null);
    }

    // Actions déclenchées au retour du choix de fichier
    // Si un fichier est bien sélectionné : calculer le hash, puis upload
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        Log.d(Globals.TAG, "--- ObjetListActivity      --- onActivityResult");

        int idBdd = -1;

        if (resultData == null){
            displaySnackbarWithId(R.string.snackbar_no_file_selected, R.string.snackbar_noaction, null);
            return;
        }
        switch (requestCode) {
            // if PICKFILE_RESULT_CODE : User should have selected a file
            case PICKFILE_RESULT_CODE:
                if (resultCode == RESULT_OK) {
                    // The returned intent contents a URI to the document
                    Uri uri = resultData.getData();
                    Log.i(Globals.TAG, "  +++ Uri String: " + uri.toString());
                    Log.i(Globals.TAG, "  +++ Uri Path  : " + uri.getPath());

                    dumpImageMetaData(uri);
                    ProofRequest p = new ProofRequest(
                            Globals.OBJET_NOID,
                            uri.toString(),
                            null,
                            Globals.STATUS_INITIALIZED,
                            "N/A",
                            "N/A",
                            "N/A",
                            null);

                    if (p != null) {
                        // Insertion en base et MAJ liste
                        // TODO CLEAN déplacer appel bdd
                        DatabaseHandler baseLocale = DatabaseHandler.getInstance(Globals.context);
                        idBdd = (int) baseLocale.insertProofRequest(p);

                        // Start a service to make a copy of the file and compute its SHA-256 hash
                        Intent i = new Intent(this, CopyAndHashService.class);
                        i.putExtra(Globals.SERVICE_IDBDD, idBdd);          // numéro de requete (pour MAJ BDD)
                        i.putExtra(Globals.SERVICE_FILENAME, uri.toString());  // nom complet du fichier (pour recopie)
                        i.putExtra(Globals.SERVICE_RECEIVER, mReceiver);   //receiver pour informer en retour l'activité
                        startService(i);

                        // Update UI
                        mAdapter.loadData(mTypeAffichage);
                        mAdapter.notifyDataSetChanged();
                    } else {
                        displaySnackbarWithId(R.string.snackbar_error_insert, R.string.snackbar_noaction, null);
                        return;
                    }
                    break;
                }
            default:
                displaySnackbarWithId(R.string.snackbar_errcode, R.string.snackbar_noaction, null);
                break;
        }
    }

    // This method is only for debugging
    public void dumpImageMetaData(Uri uri) {

        // The query, since it only applies to a single document, will only return
        // one row. There's no need to filter, sort, or select fields, since we want
        // all fields for one document.
        Cursor cursor = getContentResolver()
                .query(uri, null, null, null, null, null);

        try {
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (cursor != null && cursor.moveToFirst()) {

                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                String displayName = cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                Log.i(Globals.TAG, "Display Name: " + displayName);

                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                // If the size is unknown, the value stored is null.  But since an
                // int can't be null in Java, the behavior is implementation-specific,
                // which is just a fancy term for "unpredictable".  So as
                // a rule, check if it's null before assigning to an int.  This will
                // happen often:  The storage API allows for remote files, whose
                // size might not be locally known.
                String size = null;
                if (!cursor.isNull(sizeIndex)) {
                    // Technically the column stores an int, but cursor.getString()
                    // will do the conversion automatically.
                    size = cursor.getString(sizeIndex);
                } else {
                    size = "Unknown";
                }
                Log.i(Globals.TAG, "Size: " + size);
            }
        } finally {
            cursor.close();
        }
    }
    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        Log.d(Globals.TAG, "--- ObjetListActivity      --- onReceiveResult");
        if (resultCode == RESULT_OK) {
            int resultValue = resultData.getInt(Globals.SERVICE_RESULT_VALUE);
            int idBdd = resultData.getInt(Globals.SERVICE_IDBDD);
            Log.d(Globals.TAG, "--- ObjetListActivity      --- onReceiveResult");
            Log.d(Globals.TAG, "         Data : idbdd="+idBdd+" resultValue="+resultValue);
            if (resultValue == Globals.HASH_SUCCESS){ // Lancement du service upload pour un seul enregistrement
                Intent i = new Intent(this, UploadService.class);
                //passage au service d'un receiver pour informer en retour l'activité
                i.putExtra(Globals.SERVICE_RECEIVER, mReceiver);
                // passage du numero d'enregistrement à traiter
                i.putExtra(Globals.SERVICE_IDBDD, idBdd);
                // Start the service
                Log.d(Globals.TAG, "       HASH_SUCCESS, starting Upload for 1 record");
                startService(i);
            }

            // MAJ UI
            mAdapter.loadData(mTypeAffichage);
            mAdapter.notifyDataSetChanged();
        }
    }

    private String getCollapsingSubTitle(){
        switch (mTypeAffichage){
            case Globals.STATUS_ALL: return(getResources().getString(R.string.titre_all));
            case Globals.STATUS_FINISHED_OK: return(getResources().getString(R.string.titre_finished_ok));
            case Globals.STATUS_SUBMITTED: return(getResources().getString(R.string.titre_submitted));
            case Globals.STATUS_HASH_OK: return(getResources().getString(R.string.titre_prepared));
            case Globals.STATUS_DELETED: return(getResources().getString(R.string.titre_suppressed));
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
