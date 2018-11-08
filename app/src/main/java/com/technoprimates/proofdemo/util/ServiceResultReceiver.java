package com.technoprimates.proofdemo.util;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

/**
 * Created by MAKI LAINEUX on 20/08/2016.
 */
// custom ResultReceiver actionné à la fin d'un service

@SuppressLint("ParcelCreator")
public class ServiceResultReceiver extends ResultReceiver {
    private Receiver receiver;

    // Constructor takes a handler
    public ServiceResultReceiver(Handler handler) {
        super(handler);
    }

    // Setter for assigning the receiver
    public void setReceiver(Receiver receiver) {
        this.receiver = receiver;
    }

    // Defines our event interface for communication
    public interface Receiver {
        void onReceiveResult(int resultCode, Bundle resultData);
    }

    // Delegate method which passes the result to the receiver if the receiver has been assigned
    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (receiver != null) {
            receiver.onReceiveResult(resultCode, resultData);
        }
    }
}
