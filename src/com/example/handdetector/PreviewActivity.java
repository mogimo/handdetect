
package com.example.handdetector;

import java.util.ArrayList;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;


public class PreviewActivity extends Activity implements CvCameraViewListener2 {
    private static final String TAG = "HandDetector";
    private static final Scalar DETECT_COLOR = new Scalar(0, 0, 255, 255);
    private static final Scalar LINE_COLOR = new Scalar(0, 255, 0, 255);
    private static final Scalar AREA_COLOR = new Scalar(255, 0, 0, 255);

    private CameraBridgeViewBase mOpenCvCameraView;

    private Mat mRgba;
    private Mat mGray;
    private Mat mTemp;
    private Mat mHsv;
    private Mat m32f;
    private Mat mBin;

    private enum ViewType {RGB, HSV, BLUR, BIN, DIST};
    private ViewType mode = ViewType.RGB;

    private int mWidth, mHeight;
    private int mLeftRatio = 2, mRightRatio = 2, mMiddleRatio = 3,
            mFrontRatio = 2, mRearRatio = 1;
    private double mLeftBorder, mRightBorder, mFrontBorder;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV loaded successfully");
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
        mOpenCvCameraView.setMaxFrameSize(800, 480);

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
        mWidth = width;
        mHeight = height;
        updateBorder();

        mGray = new Mat();
        mRgba = new Mat();
        mTemp = new Mat();
        mHsv = new Mat();
        m32f = new Mat();
        mBin = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
        mTemp.release();
        mHsv.release();
        m32f.release();
        mBin.release();
    }


    private static boolean isPortrait = false; // この試みは失敗に終わったので永久にfalse
    private static boolean isDebugDraw = false; // 輪郭線とか凹凸の線とかを画面表示
    //private static final Scalar LOWER_RANGE = new Scalar(0, 38, 89);
    //private static final Scalar UPPER_RANGE = new Scalar(25, 192, 243);
    private static final Scalar LOWER_RANGE = new Scalar(0, 38, 50);
    private static final Scalar UPPER_RANGE = new Scalar(24, 190, 228);
    private static final float EDGE_ANGLE = 60.0f;
    private static final int MEDIAN_BLUR_THRESH = 11;
    private static final int FINGER_EDGE_THRESH = 6;

    private void rotate90() {
        MatOfPoint2f src = new MatOfPoint2f(
                new Point(100, 200), new Point(300, 200), new Point(300, 100));
        MatOfPoint2f dst = new MatOfPoint2f(
                new Point(100, 200), new Point(100, 400), new Point(200, 400));
        Mat rot = Imgproc.getAffineTransform(src, dst);
        Imgproc.warpAffine(mRgba, mRgba, rot, mRgba.size());
    }

    private boolean isValid() {
        return ((mLeftRatio + mMiddleRatio + mRightRatio != 0) &&
            (mFrontRatio + mRearRatio != 0)) ? true : false;
    }

    private void updateBorder() {
        if (isValid()) {
            int sumx = mLeftRatio + mMiddleRatio + mRightRatio;
            mLeftBorder = mWidth/sumx * mLeftRatio;
            mRightBorder = mWidth/sumx * (mLeftRatio + mMiddleRatio);
            mFrontBorder = mHeight/(mFrontRatio + mRearRatio) * mFrontRatio;
        }
    }

    private void drawAreaLine() {
        Core.line(mRgba,
                new Point(mLeftBorder, 0), new Point(mLeftBorder, mHeight),
                AREA_COLOR, 2);
        Core.line(mRgba,
                new Point(mRightBorder, 0), new Point(mRightBorder, mHeight),
                AREA_COLOR, 2);
        Core.line(mRgba,
                new Point(mLeftBorder, mFrontBorder), new Point(mRightBorder, mFrontBorder),
                AREA_COLOR, 2);
    }

    private void judgeArea(Point target) {
        if (Double.compare(target.x, mLeftBorder) < 0) {
            // turn left!
            MotionControl.moveLeft();
        } else if (Double.compare(target.x, mRightBorder) > 0) {
            // turn right!
            MotionControl.moveRight();
        } else if (Double.compare(target.y, mFrontBorder) > 0) {
            // move forward!
            MotionControl.moveForward();
        } else {
            // move back!
            MotionControl.moveBack();
        }
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        // (0) flip around y-axis
        Core.flip(mRgba, mRgba, 1);
        if (isPortrait) {
            rotate90();
        }

        // (1) convert to HSV
        Imgproc.cvtColor(mRgba, mTemp, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(mTemp, mHsv, Imgproc.COLOR_RGB2HSV);

        // (2) smooth with median
        // 遅くなるのでやめた
        //Imgproc.medianBlur(mHsv, mTemp, MEDIAN_BLUR_THRESH);
        //Imgproc.GaussianBlur(mHsv, mTemp, new Size(15,15), 8);

        // (3) skin color detection
        Core.inRange(mHsv, LOWER_RANGE, UPPER_RANGE, mBin);

        // (4) distance transform
        Imgproc.distanceTransform(mBin, m32f, Imgproc.CV_DIST_L2, 5);
        Core.convertScaleAbs(m32f, mGray);

        // (5) retrieve contours 
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(mGray, contours, hierarchy,
                1 /*CV_RETR_LIST*/,
                1 /*CV_CHAIN_APPROX_NONE*/);

        // (6) find max contour
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
        
        // draw area line
        drawAreaLine();
        if (isDebugDraw) {
            Imgproc.drawContours(mRgba, contours, maxId, AREA_COLOR, 3);
        }
        // (7) find convex hull
        int edgeCount = 0;
        if ((maxContours != null) &&
                (maxContours.checkVector(2, CvType.CV_32S) > 3)) {
            MatOfInt hull = new MatOfInt();
            MatOfInt4 detectedHull = new MatOfInt4();
            Imgproc.convexHull(maxContours, hull);
            Imgproc.convexityDefects(maxContours, hull, detectedHull);
            int[] hulls = detectedHull.toArray();
            double x, y;
            float angle, angle1, angle2, pre_angle2 = 0.0f, pre_angle;
            for (int i=0; i<hulls.length; i+=4) {
                if (hulls[i+3] > 5000) {
                    Point[] points = maxContours.toArray();
                    if (isDebugDraw) {
                        Core.line(mRgba,
                            points[hulls[i]], points[hulls[i+2]],
                            LINE_COLOR, 3);
                    }
                    x = points[hulls[i]].x - points[hulls[i+2]].x;
                    y = points[hulls[i]].y - points[hulls[i+2]].y;
                    angle1 = Core.fastAtan2((float)y, (float)x);
                    if (isDebugDraw) {
                        Core.line(mRgba,
                            points[hulls[i+1]], points[hulls[i+2]],
                            LINE_COLOR, 3);
                    }
                    x = points[hulls[i+1]].x - points[hulls[i+2]].x;
                    y = points[hulls[i+1]].y - points[hulls[i+2]].y;
                    angle2 = Core.fastAtan2((float)y, (float)x);
                    angle = Float.compare(angle1, angle2) > 0 ?
                            (angle1 - angle2) : (angle2 - angle1);
                    Log.d(TAG, "angle=" + angle);
                    if (Float.compare(angle, EDGE_ANGLE) < 0) {
                            edgeCount++;
                    }
                    if (i != 0) {
                        pre_angle = Float.compare(pre_angle2, angle1) > 0 ?
                                (pre_angle2 - angle1) : (angle1 - pre_angle2);
                        if (Float.compare(pre_angle, EDGE_ANGLE) < 0) {
                            edgeCount++;
                        }
                    }
                    pre_angle2 = angle2;
                }
            }
        }
        // (8) show detected object as a hand
        Log.d(TAG, "edge=" + edgeCount);
        if (maxContours != null && (edgeCount >= FINGER_EDGE_THRESH)) {
            Point[] points = maxContours.toArray();
            double sumx = 0.0f, sumy = 0.0f;
            int num = points.length;
            for (int i=0; i<num; i++) {
                sumx += points[i].x;
                sumy += points[i].y;
            }
            Point center = new Point(sumx/num, sumy/num);
            Core.circle(mRgba, center, 20, DETECT_COLOR, 5);
            // (9) judge area (right or left)
            judgeArea(center);
        } else {
            // stop
            MotionControl.moveStop();
        }

        // switch preview mode
        switch (mode) {
            case HSV:
                return mHsv;
            case BLUR:
                return mTemp;
            case BIN:
                return mBin;
            case DIST:
                return mGray;
            case RGB:
            default:
                return mRgba;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null) {
            mLeftRatio = data.getIntExtra("left", 1);
            mRightRatio = data.getIntExtra("right", 1);
            mMiddleRatio = data.getIntExtra("middle", 2);
            mFrontRatio = data.getIntExtra("front", 2);
            mRearRatio = data.getIntExtra("rear", 1);
    
            updateBorder();
        }
    }

    /*
     * Menu
     */
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0,0,0,"Normal(RBG)");
        menu.add(0,1,0,"HSV");
        menu.add(0,2,0,"Toggle Debug");
        menu.add(0,3,0,"Binary");
        menu.add(0,4,0,"Distance");
        menu.add(0,5,0,"Area Edit");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                mode = ViewType.RGB;
                break;
            case 1:
                mode = ViewType.HSV;
                break;
            case 2:
                isDebugDraw = !isDebugDraw;
                break;
            case 3:
                mode = ViewType.BIN;
                break;
            case 4:
                mode = ViewType.DIST;
                break;
            case 5:
                Intent intent = new Intent(this, AreaRatioEditActivity.class);
                startActivityForResult(intent, 0);
            default:
                break;
        }
        return true;
    }
}
