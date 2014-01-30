package com.example.handdetector;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class AreaRatioEditActivity extends Activity implements OnClickListener {
    private EditText mLeft, mMiddle, mRight, mFront, mRear;
    private Button mButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.editor);
        mLeft = (EditText)findViewById(R.id.editText1);
        mMiddle = (EditText)findViewById(R.id.editText2);
        mRight = (EditText)findViewById(R.id.editText3);
        mFront = (EditText)findViewById(R.id.editText4);
        mRear = (EditText)findViewById(R.id.editText5);
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
        setResult(RESULT_OK, intent);
        finish();
    }
}
