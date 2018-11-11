package com.technoprimates.proofdemo.util;

import android.content.Context;
import android.net.Uri;

public abstract class Utils {
    public abstract boolean createPdfProofFile(Context context, String displayName, String proofString, int requestNumber);
    protected abstract boolean addProofNeutralMetadata (Context context, String fileName);
    protected abstract String readProofFromFullUri(Context context, Uri fullUri);
    protected abstract boolean saveFileToHash(Context context,  String pdfName, String tmpString);
}