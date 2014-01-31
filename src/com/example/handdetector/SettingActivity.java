package com.example.handdetector;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;

public class SettingActivity extends Activity
        implements OnClickListener, SeekBar.OnSeekBarChangeListener {
    private static final String TAG = "HandDetector";
    private EditText mLeft, mMiddle, mRight, mFront, mRear;
    private Button mButton;
    private int mMax = 0, mMin = 0, mExposure = 0;
    private SeekBar mSeekbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.editor);

        mLeft = (EditText)findViewById(R.id.editText1);
        mMiddle = (EditText)findViewById(R.id.editText2);
        mRight = (EditText)findViewById(R.id.editText3);
        mFront = (EditText)findViewById(R.id.editText4);
        mRear = (EditText)findViewById(R.id.editText5);

        Intent intent = getIntent();
        if (intent != null) {
            mLeft.setText(Integer.toString(intent.getIntExtra("left", 2)));
            mRight.setText(Integer.toString(intent.getIntExtra("right", 2)));
            mMiddle.setText(Integer.toString(intent.getIntExtra("middle", 3)));
            mFront.setText(Integer.toString(intent.getIntExtra("front", 2)));
            mRear.setText(Integer.toString(intent.getIntExtra("rear", 1)));
            mMax = intent.getIntExtra("max", 0);
            mMin = intent.getIntExtra("min", 0);
            mExposure = intent.getIntExtra("exposure", 0);
        }

        mSeekbar = (SeekBar)findViewById(R.id.seekbar);
        if (mMax == 0 && mMin == 0) {
            mSeekbar.setVisibility(View.INVISIBLE);
        } else {
            mSeekbar.setOnSeekBarChangeListener(this);
            mSeekbar.setMax(mMax-mMin);
            mSeekbar.setProgress(mExposure-mMin);
        }

        mButton = (Button)findViewById(R.id.button1);
        mButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View arg0) {
        Intent intent = new Intent();
        intent.putExtra("left", Integer.parseInt(mLeft.getText().toString()));
        intent.putExtra("middle", Integer.parseInt(mMiddle.getText().toString()));
        intent.putExtra("right", Integer.parseInt(mRight.getText().toString()));
        intent.putExtra("front", Integer.parseInt(mFront.getText().toString()));
        intent.putExtra("rear", Integer.parseInt(mRear.getText().toString()));
        intent.putExtra("exposure", mExposure);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
        if (fromUser) {
            Log.d(TAG, "value = " + value);
            mExposure = value + mMin;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar arg0) {
    }
}
