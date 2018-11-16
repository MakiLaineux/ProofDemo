package com.technoprimates.proofdemo.util;

import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

public class ProofException extends Exception {
    private String mErrorString;

    public ProofException() {
        super();
        this.mErrorString = "";
        Log.i(Constants.TAG, "ProofException -- Empty constructor");
    }

    public ProofException(String msg) {
        super();
        this.mErrorString = msg;
        Log.i(Constants.TAG, "ProofException -- String constructor: "+msg);
    }
    public String getProofError(){
        return mErrorString;
    }
}
