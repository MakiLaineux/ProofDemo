package com.technoprimates.proofdemo.util;

import android.util.Log;
import static com.technoprimates.proofdemo.util.Constants.*;

public class ProofException extends Exception {
    private String mErrorString;

    public ProofException() {
        super();
        this.mErrorString = "";
        Log.i(TAG, "ProofException -- Empty constructor");
    }

    public ProofException(String msg) {
        super();
        this.mErrorString = msg;
        Log.i(TAG, "ProofException -- String constructor: "+msg);
    }
    public String getProofError(){
        return mErrorString;
    }
}
