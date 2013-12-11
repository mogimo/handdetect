package com.example.handdetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.Utils;
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
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

    private DetectImage mImage;
    private File mFile;
    private CascadeClassifier mJavaDetector;
    private MatOfRect mDetectedRects = new MatOfRect();
    private int mImageWidth = 0, mImageHeight = 0;

    private class DetectImage extends ImageView {
        DetectImage(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (mDetectedRects == null) {
                return;
            }
            int width = canvas.getWidth();
            int height = canvas.getHeight();
            float scaleW = 1.0f, scaleH = 1.0f;
            if (width > mImageWidth) {
                scaleW = (width * 1.0f)/(mImageWidth * 1.0f);
            }
            if (height > mImageHeight) {
                scaleH = (height * 1.0f)/(mImageHeight * 1.0f);
            }
            float scale = (Float.compare(scaleW, scaleH) <= 0 ? scaleW : scaleH);
            Paint paint = new Paint();
            paint.setColor(Color.argb(255, 0, 255, 0));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            for (Rect rect : mDetectedRects.toArray()) {
                int x = (int) (rect.x * scale);
                int y = (int) (rect.y * scale);
                int h = (int) (rect.height * scale);
                int w = (int) (rect.width * scale);
                Log.d(TAG, String.format(
                        "draw rect [%d,%d - %d,%d] <- [%d,%d - %d,%d]",
                        x, y, h, w, rect.x, rect.y, rect.height, rect.width));
                canvas.drawRect(x, y, x + w, y + h, paint);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.oneshot);
        //mImage = (ImageView) findViewById(R.id.image);
        mImage = new DetectImage(this);
        setContentView(mImage);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "handdetect");
        mFile = new File(dir, "snapshot.jpg");
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
        mJavaDetector.detectMultiScale(image, mDetectedRects);
        Log.d(TAG, "detect fist num=" + mDetectedRects.toArray().length);
        /*
        for (Rect rect : mDetectedRects.toArray()) {
            Core.rectangle(image, new Point(rect.x, rect.y), 
                    new Point(rect.x + rect.width, rect.y + rect.height),
                    new Scalar(0, 255, 0));
        }
        boolean ret = Highgui.imwrite(mDetected.getAbsolutePath(), image);
        if (ret) {
            mImage.setImageURI(Uri.fromFile(mDetected));
        }
        */
        Bitmap bitmap = BitmapFactory.decodeFile(mFile.getAbsolutePath());
        mImageWidth = bitmap.getWidth();
        mImageHeight = bitmap.getHeight();
        //Bitmap bitmap = Bitmap.createBitmap(image.cols(), image.rows(), Config.RGB_565);
        //Utils.matToBitmap(image, bitmap);
        mImage.setImageBitmap(bitmap);
        mImage.invalidate();
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
