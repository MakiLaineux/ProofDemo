package com.technoprimates.proofdemo.activities;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.technoprimates.proofdemo.R;

import java.util.UUID;

public class MessageActivity extends AppCompatActivity {

    SharedPreferences mSharedPreferences;

    // UI elements
    private TextView mTvCurrentMessage;
    private EditText mEtNewMessage;
    private Button mMessageOK, mMessageCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);
        mTvCurrentMessage = findViewById(R.id.current_message);
        mEtNewMessage = findViewById(R.id.new_message);
        mMessageCancel = findViewById(R.id.message_cancel);
        mMessageOK = findViewById(R.id.message_ok);

        // Display current author message from SharedPreferences
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String currentMessage = mSharedPreferences.getString("ProofAuthorMessage", "");
        mTvCurrentMessage.setText(currentMessage);
        mTvCurrentMessage.setMovementMethod(new ScrollingMovementMethod()); // enable vertical scrolling
    }

    public void cancelMessage(View v){
        setResult(RESULT_CANCELED);
        finish();
    }

    public void registerMessage(View v){
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        String newMessage = mEtNewMessage.getText().toString();
        editor.putString("ProofAuthorMessage", newMessage);
        editor.apply();
        setResult(RESULT_OK);
        finish();
    }

}




