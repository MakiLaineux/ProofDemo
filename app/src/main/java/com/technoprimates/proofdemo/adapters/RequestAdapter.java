package com.technoprimates.proofdemo.adapters;


import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.technoprimates.proofdemo.db.DatabaseHandler;
import com.technoprimates.proofdemo.util.ProofUtils;
import com.technoprimates.proofdemo.util.Constants;
import com.technoprimates.proofdemo.R;
import com.technoprimates.proofdemo.util.VisuProofListener;

/*
 * RecyclerView Adapter. The items displayed are some of the request records stored in local database.
 * A cursor with the items to display is build in public method loadData()
 *
 * When a user clicks on a request item with "finished" status, this item becomes "expanded", ie it displays additional details
 * and a clickable button. Only one item at a time may be expanded
 *
 * Created by JC, october 2018.
 */
public class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.ViewHolder>{

    private DatabaseHandler mLocalDb = null; // Local db
    private Cursor mCursorRequests;    // Items of the RecyclerView
    private int mRvCount;             // number of items in RecyclerView
    private int mExpandedItem = -1; // position of the expanded item, if any, or -1 if none
    private String mProofName = null; // nom du fichier zip pour visu
    private VisuProofListener mVisuProofListener;

    // Constructor : load the relevant data
    public RequestAdapter(Context context, int displayType) {
        // get singleton instance of database
        mLocalDb = DatabaseHandler.getInstance(context);
        loadData(displayType);
    }

    public void setVisuProofListener(VisuProofListener visuProofListener) {
        this.mVisuProofListener = visuProofListener;
    }

    // Custom view holder
    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        CardView vhCard;
        ImageView vhImageStatus;
        TextView vhFilename;
        TextView vhRequestDate;
        TextView vhRequestId;
        TextView vhExpandedText;
        TextView vhExpandedProofname;
        Button vhButtonProof;

        ViewHolder(View itemView) {
            super(itemView);
            vhCard = (CardView) itemView.findViewById(R.id.cv);
            vhImageStatus = (ImageView)itemView.findViewById(R.id.card_icon);
            vhButtonProof = (Button)itemView.findViewById(R.id.card_button);
            vhFilename = (TextView)itemView.findViewById(R.id.card_filename);
            vhRequestDate = (TextView)itemView.findViewById(R.id.card_date);
            vhRequestId = (TextView)itemView.findViewById(R.id.card_request_id);
            vhExpandedText = (TextView)itemView.findViewById(R.id.card_text_expanded);
            vhExpandedProofname = (TextView)itemView.findViewById(R.id.card_proof_expanded);
            itemView.setOnClickListener(this);
            vhButtonProof.setOnClickListener(this);
        }

        @Override
        // Manage click
        public void onClick(View v) {
            if ((v.getId() == R.id.card_button) && (mVisuProofListener != null))
                // Click on the expanded button : launches callback in calling activity
                mVisuProofListener.visuProof(mProofName);
            else {
                if (getAdapterPosition()==mExpandedItem){
                    // Click on the expanded item but not on the button : switch item to non-expanded
                    mExpandedItem = -1;
                } else {
                    // Click on non-expanded item : expand item
                    // This will affect item display only if item is in finished status
                    mExpandedItem = getAdapterPosition();
                }
                // refresh UI
                notifyDataSetChanged();
            }
        }
    }


    // Load recyclerview data with records from local db
    public void loadData(int displayType) {
        mExpandedItem = -1;
        if (mCursorRequests != null) mCursorRequests.close();
        mCursorRequests = mLocalDb.getAllProofRequests(displayType);
        if (mCursorRequests == null) {
            mRvCount = 0;
            return;
        }
        mRvCount = mCursorRequests.getCount();
    }

    @Override
    public int getItemCount() {
        return mRvCount;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.card, viewGroup, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder rvh, int i) {
        // feed displayed data from Cursor
        Context context = rvh.itemView.getContext();
        mCursorRequests.moveToPosition(i);
        rvh.vhFilename.setText(mCursorRequests.getString(Constants.REQUEST_NUM_COL_FILENAME));
        rvh.vhRequestDate.setText(String.format(
                context.getResources().getString(R.string.txt_date_demande),
                mCursorRequests.getString(Constants.REQUEST_NUM_COL_REQUEST_DATE)));
        rvh.vhRequestId.setText(mCursorRequests.getString(Constants.REQUEST_NUM_COL_ID));

        // Status-dependant fields
        // Background color and status image both depend on status
        // date field is modified to show proof date if status is "finished"
        int stat = mCursorRequests.getInt(Constants.REQUEST_NUM_COL_STATUS);
        switch (stat) {
            case Constants.STATUS_INITIALIZED:
                rvh.vhCard.setCardBackgroundColor
                    (ContextCompat.getColor(context, R.color.colorCardInitialized));
                rvh.vhImageStatus.setImageResource(R.drawable.ic_attachment_black_48dp);
                break;
            case Constants.STATUS_HASH_OK:
                rvh.vhCard.setCardBackgroundColor
                    (ContextCompat.getColor(context, R.color.colorCardPrepared));
                rvh.vhImageStatus.setImageResource(R.drawable.ic_cloud_off_black_48dp);
                break;
            case Constants.STATUS_SUBMITTED:
                rvh.vhCard.setCardBackgroundColor
                    (ContextCompat.getColor(context, R.color.colorCardSubmitted));
                rvh.vhImageStatus.setImageResource(R.drawable.ic_cloud_queue_black_48dp);
                break;
            case Constants.STATUS_FINISHED_ZIP:
            case Constants.STATUS_FINISHED_PDF:
                rvh.vhCard.setCardBackgroundColor
                    (ContextCompat.getColor(context, R.color.colorCardProofOK));
                rvh.vhImageStatus.setImageResource(R.drawable.ic_done_black_48dp);
                // overwrite date field
                rvh.vhRequestDate.setText(String.format(
                        context.getResources().getString(R.string.txt_date_preuve),
                        mCursorRequests.getString(Constants.REQUEST_NUM_COL_INFO)));
                break;
            case Constants.STATUS_DELETED :
                rvh.vhCard.setCardBackgroundColor
                    (ContextCompat.getColor(context, R.color.colorCardError));
                rvh.vhImageStatus.setImageResource(R.drawable.ic_delete_black_48dp);
                break;
            case Constants.STATUS_ERROR :
                rvh.vhCard.setCardBackgroundColor
                    (ContextCompat.getColor(context, R.color.colorCardError));
                rvh.vhImageStatus.setImageResource(R.drawable.ic_error_outline_black_48dp);
                break;
            default :
                rvh.vhCard.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.colorCardError));
                break;
        }
        // Extra fields if item is expanded and status is "finished"
        if ((stat == Constants.STATUS_FINISHED_ZIP || stat == Constants.STATUS_FINISHED_PDF)
                && i == mExpandedItem){
            rvh.vhExpandedText.setVisibility(View.VISIBLE);
            rvh.vhExpandedProofname.setVisibility(View.VISIBLE);
            rvh.vhButtonProof.setVisibility(View.VISIBLE);
            mProofName = ProofUtils.getProofFileName(
                    mCursorRequests.getInt(Constants.REQUEST_NUM_COL_ID),
                    mCursorRequests.getString(Constants.REQUEST_NUM_COL_FILENAME),
                    stat);
            rvh.vhExpandedProofname.setText(mProofName);
        } else {
            rvh.vhExpandedText.setVisibility(View.GONE);
            rvh.vhExpandedProofname.setVisibility(View.GONE);
            rvh.vhButtonProof.setVisibility(View.GONE);
        }
    }

}