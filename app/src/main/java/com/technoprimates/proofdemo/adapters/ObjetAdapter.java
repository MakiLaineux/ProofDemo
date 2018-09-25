package com.technoprimates.proofdemo.adapters;


import android.database.Cursor;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.technoprimates.proofdemo.db.DatabaseHandler;
import com.technoprimates.proofdemo.db.ProofRequest;
import com.technoprimates.proofdemo.util.Globals;
import com.technoprimates.proofdemo.R;
import com.technoprimates.proofdemo.util.VisuProofListener;

/**
 * Created by MAKI LAINEUX on 15/03/2016.
 * Adapter pour la RecyclerView
 * Les données sont contenues dans mCursorObjets, (ré)initialisation via loadData(..)
 */
public class ObjetAdapter extends RecyclerView.Adapter<ObjetAdapter.ViewHolder>{

    private DatabaseHandler mBaseLocale = null;

    private Cursor mCursorObjets;    // Les données de la liste
    private int mCount;             // nombre d'items
    private int mExpandedItem = -1; // numéro de l'item développé ou -1 si aucun
    private String mZipName = null; // nom du fichier zip pour visu
    private String mZipEntryName = null; // nom entrée du fichier zip pour visu
    private VisuProofListener mVisuProofListener;

    public ObjetAdapter(int typeAffichage) {
        // get singleton instance of database
        mBaseLocale = DatabaseHandler.getInstance(Globals.context);
        loadData(typeAffichage);
    }

    public void setVisuProofListener(VisuProofListener visuProofListener) {
        this.mVisuProofListener = visuProofListener;
    }
    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        CardView vhCard;
        ImageView vhImageStatut;
        TextView vhFilename;
        TextView vhDateDemande;
        TextView vhExpandedText;
        TextView vhExpandedProofname;
        Button vhButtonProof;

        ViewHolder(View itemView) {
            super(itemView);
            vhCard = (CardView) itemView.findViewById(R.id.cv);
            vhImageStatut = (ImageView)itemView.findViewById(R.id.card_icon);
            vhButtonProof = (Button)itemView.findViewById(R.id.card_button);
            vhFilename = (TextView)itemView.findViewById(R.id.card_filename);
            vhDateDemande = (TextView)itemView.findViewById(R.id.card_date);
            vhExpandedText = (TextView)itemView.findViewById(R.id.card_text_expanded);
            vhExpandedProofname = (TextView)itemView.findViewById(R.id.card_proof_expanded);
            itemView.setOnClickListener(this);
            vhButtonProof.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            // Si le click est sur le bouton : visu
            if ((v.getId() == R.id.card_button) && (mVisuProofListener != null))
                mVisuProofListener.showProof(mZipName, mZipEntryName);
            else {
                // Si le click est sur l'item expansé : désexspansage
                if (getAdapterPosition()==mExpandedItem){
                    mExpandedItem = -1;
                } else {
                    // Sinon :expansage de l'item clické
                    mExpandedItem = getAdapterPosition();
                }
                notifyDataSetChanged();
            }
        }
    }


    public void loadData(int typeAffichage) {
        mExpandedItem = -1;
        if (mCursorObjets != null) mCursorObjets.close();
        mCursorObjets = mBaseLocale.getAllProofRequests(typeAffichage);
        if (mCursorObjets == null) {
            mCount = 0;
            return;
        }
        mCount = mCursorObjets.getCount();
    }

    public int getIdBddFromPosition (int pos){
        if ((mCursorObjets == null) || !mCursorObjets.moveToPosition(pos)) return -1;
        return mCursorObjets.getInt(Globals.OBJET_NUM_COL_ID);
    }

    public ProofRequest getOneProofRequest(int idBdd){
        return mBaseLocale.getOneProofRequest(idBdd);
    }

    public boolean deleteRecord (int idBdd){
        mBaseLocale.updateStatutProofRequest(idBdd, Globals.STATUS_DELETED);
        return true;
    }

    // TODO : gérer la sauvegarde de statut lors de la suppression
    public boolean restoreRecord (int idBdd){
        mBaseLocale.updateStatutProofRequest(idBdd, Globals.STATUS_ERROR);
        return true;
    }

    @Override
    public int getItemCount() {
        return mCount;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.card, viewGroup, false);
        ViewHolder rvh = new ViewHolder(v);
        return rvh;
    }

    @Override
    public void onBindViewHolder(ViewHolder rvh, int i) {
        mCursorObjets.moveToPosition(i);

        // Affectation des textes
        String fullName = mCursorObjets.getString(Globals.OBJET_NUM_COL_CHEMIN);
        String shortName = fullName.substring(fullName.lastIndexOf("/")+1);
        rvh.vhFilename.setText(shortName);
        rvh.vhDateDemande.setText(String.format(
                Globals.context.getResources().getString(R.string.txt_date_demande),
                mCursorObjets.getString(Globals.OBJET_NUM_COL_DATE_DEMANDE)));

        // Couleur de fond en fonction du statut
        int stat = mCursorObjets.getInt(Globals.OBJET_NUM_COL_STATUT);

        switch (stat) {
            case Globals.STATUS_INITIALIZED:
                rvh.vhCard.setCardBackgroundColor
                    (ContextCompat.getColor(Globals.context, R.color.colorCardInitialized));
                rvh.vhImageStatut.setImageResource(R.drawable.ic_attachment_black_48dp);
                break;
            case Globals.STATUS_HASH_OK:
                rvh.vhCard.setCardBackgroundColor
                    (ContextCompat.getColor(Globals.context, R.color.colorCardPrepared));
                rvh.vhImageStatut.setImageResource(R.drawable.ic_cloud_off_black_48dp);
                break;
            case Globals.STATUS_SUBMITTED:
                rvh.vhCard.setCardBackgroundColor
                    (ContextCompat.getColor(Globals.context, R.color.colorCardSubmitted));
                rvh.vhImageStatut.setImageResource(R.drawable.ic_cloud_queue_black_48dp);
                break;
            case Globals.STATUS_FINISHED_OK:
                rvh.vhCard.setCardBackgroundColor
                    (ContextCompat.getColor(Globals.context, R.color.colorCardProofOK));
                rvh.vhImageStatut.setImageResource(R.drawable.ic_done_black_48dp);
                // texte différent:
                rvh.vhDateDemande.setText(String.format(
                        Globals.context.getResources().getString(R.string.txt_date_preuve),
                        mCursorObjets.getString(Globals.OBJET_NUM_COL_INFO)));
                break;
            case Globals.STATUS_DELETED :
                rvh.vhCard.setCardBackgroundColor
                    (ContextCompat.getColor(Globals.context, R.color.colorCardError));
                rvh.vhImageStatut.setImageResource(R.drawable.ic_delete_black_48dp);
                break;
            case Globals.STATUS_ERROR :
                rvh.vhCard.setCardBackgroundColor
                    (ContextCompat.getColor(Globals.context, R.color.colorCardError));
                rvh.vhImageStatut.setImageResource(R.drawable.ic_error_outline_black_48dp);
                break;
            default :
                rvh.vhCard.setCardBackgroundColor(
                    ContextCompat.getColor(Globals.context, R.color.colorCardError));
                break;
        }
        // Si expanded item, affichage infos supplémentaires et bouton Visu
        if (stat == Globals.STATUS_FINISHED_OK && i == mExpandedItem){
            rvh.vhExpandedText.setVisibility(View.VISIBLE);
            rvh.vhExpandedProofname.setVisibility(View.VISIBLE);
            rvh.vhButtonProof.setVisibility(View.VISIBLE);
            String sId = String.format("%04d", mCursorObjets.getInt(Globals.OBJET_NUM_COL_ID));
            String nameSDCard = Globals.DIRECTORY_LOCAL
                    + shortName
                    + "."
                    + sId
                    +".zip";
            rvh.vhExpandedProofname.setText(nameSDCard);
            // mémorisation nom du zip et nom de l'ntrée preuve pour visu si click bouton:
            mZipName = Environment.getExternalStorageDirectory()+nameSDCard;
            mZipEntryName = "proof_"+sId+".txt";
        } else {
            rvh.vhExpandedText.setVisibility(View.GONE);
            rvh.vhExpandedProofname.setVisibility(View.GONE);
            rvh.vhButtonProof.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
    }
}