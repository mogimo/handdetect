package com.example.handdetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.highgui.Highgui;
import org.opencv.objdetect.CascadeClassifier;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;

public class SnapShotActivity extends Activity {
    private static final String TAG = "HandDetector";
    private static final int REQUEST_TAKE_PHOTO = 1;

    private ImageView mImage;
    private File mFile, mDetected;
    private CascadeClassifier mJavaDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.oneshot);
        mImage = (ImageView) findViewById(R.id.image);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "handdetect");
        mFile = new File(dir, "snapshot.jpg");
        mDetected = new File(dir, "detected.jpg");
        if (isIntentAvailable(this, MediaStore.ACTION_IMAGE_CAPTURE)) {
            dispatchTakePictureIntent();
        } else {
            Log.e(TAG, "device has no image capture capability");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (REQUEST_TAKE_PHOTO == requestCode && resultCode == RESULT_OK) {
            loadHandClassifier();
            detectFist();
        }
    }

    private void loadHandClassifier() {
        try {
            // load cascade file from application resources
            InputStream is = getResources().openRawResource(R.raw.fist_cascade);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File cascade = new File(
                    cascadeDir, "fist_cascade.xml");
            FileOutputStream os = new FileOutputStream(cascade);
            byte[] buffer = new byte[4096];
            int n = 0;
            while ((n = is.read(buffer)) != -1) {
                os.write(buffer, 0, n);
            }
            is.close();
            os.close();

            mJavaDetector = new CascadeClassifier();
            mJavaDetector.load(cascade.getAbsolutePath());
            if (mJavaDetector.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                mJavaDetector = null;
            } else {
                Log.i(TAG, "Loaded cascade classifier from "
                        + cascade.getAbsolutePath());
            }
            cascadeDir.delete();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade: " + e);
        }
    }

    private void detectFist() {
        Mat image = Highgui.imread(mFile.getAbsolutePath());
        MatOfRect detectedRects = new MatOfRect();
        mJavaDetector.detectMultiScale(image, detectedRects);

        for (Rect rect : detectedRects.toArray()) {
            Core.rectangle(image, new Point(rect.x, rect.y), 
                    new Point(rect.x + rect.width, rect.y + rect.height),
                    new Scalar(0, 255, 0));
        }
        Highgui.imwrite(mDetected.getAbsolutePath(), image);
        mImage.setImageURI(Uri.fromFile(mDetected));
    }

    private boolean isIntentAvailable(Context context, String action) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent intent = new Intent(action);
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mFile));
        startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
    }
}
