
package com.example.handdetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;


public class PreviewActivity extends Activity implements CvCameraViewListener2 {
    private static final String TAG = "HandDetector";
    private static final float RELATIVE_FACESIZE = 0.3f;
    private static final Scalar DETECT_RECT_COLOR = new Scalar(0, 255, 0, 255);
    private static final Scalar LINE_COLOR = new Scalar(255, 0, 0, 255);
    private static final int AREA_NUM = 3;

    private CameraBridgeViewBase mOpenCvCameraView;
    private CascadeClassifier mJavaDetector;

    private Mat mRgba;
    private Mat mGray;
    private Mat mTemp;
    private Mat mHsv;
    private Mat m32f;

    private void loadClassifier(final int resId) {
        try {
            // load cascade file from application resources
            InputStream is = getResources().openRawResource(resId);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File cascade = new File(cascadeDir, "cascade.xml");
            FileOutputStream os = new FileOutputStream(cascade);
            byte[] buffer = new byte[4096];
            int n = 0;
            while ((n = is.read(buffer)) != -1) {
                os.write(buffer, 0, n);
            }
            is.close();
            os.close();

            mJavaDetector = new CascadeClassifier(cascade.getAbsolutePath());
            if (mJavaDetector.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                mJavaDetector = null;
            } else {
                Log.i(TAG, "Loaded cascade classifier from "
                        + getResources().getResourceName(resId));
            }
            cascadeDir.delete();
            cascade.delete();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade: " + e);
        }
    }

    private void loadFaceClassifier() {
        loadClassifier(R.raw.lbpcascade_frontalface);
    }

    private void loadHandClassifier() {
        loadClassifier(R.raw.hand_cascade);
    }

    private void loadFistClassifier() {
        loadClassifier(R.raw.fist_cascade);
    }

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV loaded successfully");
                    //loadFaceClassifier();
                    //loadHandClassifier();
                    loadFistClassifier();
                    mOpenCvCameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preview);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.cameraSurfaceview);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
        mOpenCvCameraView.enableFpsMeter();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
        mTemp = new Mat();
        mHsv = new Mat();
        m32f = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        Core.flip(mRgba, mRgba, 1);
        // (1) convert to HSV
        Imgproc.cvtColor(mRgba, mTemp, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(mTemp, mHsv, Imgproc.COLOR_RGB2HSV);
        // (2) smooth with median
        Imgproc.GaussianBlur(mHsv, mTemp, new Size(15,15), 8);
        // (3) skin color detection
        Core.inRange(mTemp, new Scalar(0, 38, 89), new Scalar(25, 192, 243), mHsv);
        // (4) distance transform
        Imgproc.distanceTransform(mHsv, m32f, Imgproc.CV_DIST_L2, 5);
        Core.convertScaleAbs(m32f, mGray);
        // (5) retrieve contours 
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mGray, contours, hierarchy,
                1 /*CV_RETR_LIST*/,
                1 /*CV_CHAIN_APPROX_NONE*/);
        int index = 0, maxId = -1;
        double area = 0.0f, maxArea = 0.0f;
        MatOfPoint maxContours = null;
        for (MatOfPoint cont : contours) {
            area = Imgproc.contourArea(cont);
            if (Double.compare(maxArea, area) < 0) {
                maxArea = area;
                maxId = index;
                maxContours = cont;
            }
            index++;
        }
        // (6) show detect area as a "hand"
        if (maxContours != null) {
            Point[] points = maxContours.toArray();
            double sumx = 0.0f, sumy = 0.0f;
            int num = points.length;
            for (int i=0; i<num; i++) {
                sumx += points[i].x;
                sumy += points[i].y;
            }
            Core.circle(mRgba,
                    new Point(sumx/num, sumy/num),
                    10, new Scalar(0,0,255), 5);
        }
        Imgproc.drawContours(mRgba, contours, maxId, new Scalar(255,0,0), 3);
        return mRgba;
    }

    /*
     * Menu
     */
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0,0,0,"Detect Fist");
        menu.add(0,1,0,"Detect Hand");
        menu.add(0,2,0,"Detect Face");
        menu.add(0,3,0,"Detect for Snap");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                mJavaDetector = null;
                loadFistClassifier();
                break;
            case 1:
                mJavaDetector = null;
                loadHandClassifier();
                break;
            case 2:
                mJavaDetector = null;
                loadFaceClassifier();
                break;
            case 3:
            default:
                Intent intent = new Intent(this, SnapShotActivity.class);
                startActivity(intent);
                break;
        }
        return true;
    }    

}
