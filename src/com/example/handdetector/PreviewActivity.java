
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
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager;


public class PreviewActivity extends Activity implements CvCameraViewListener2 {
    private static final String TAG = "HandDetector";
    private static final Scalar DETECT_COLOR = new Scalar(0, 0, 255, 255);
    private static final Scalar LINE_COLOR = new Scalar(0, 255, 0, 255);
    private static final Scalar AREA_COLOR = new Scalar(255, 0, 0, 255);
    private static final Scalar FONT_COLOR = new Scalar(0, 128, 128);

    private Point TEXT_POINT;

    private ControlableCameraView mOpenCvCameraView;

    private Mat mRgba;
    private Mat mGray;
    private Mat mTemp;
    private Mat mHsv;
    private Mat m32f;
    private Mat mBin;
    private Mat mRoi;
    private Mat mHist;
    MatOfInt mChannels;
    MatOfInt mHistSize;
    MatOfFloat mRange;

    private enum ViewType {RGB, HSV, BLUR, BIN, DIST};
    private ViewType mode = ViewType.RGB;

    private int mWidth, mHeight;
    private int mLeftRatio = 2, mRightRatio = 2, mMiddleRatio = 3;
    private int mFrontRatio = 2, mRearRatio = 1;
    private double mLeftBorder, mRightBorder, mFrontBorder;
    private int mExposure = 0;

    private MotionControl mMotionControl;

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

    private static final int MSG_CALC_HIST = 1;
    private MessageHandler mHandler = new MessageHandler();
    private class MessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CALC_HIST:
                    Log.d(TAG, "timer expired!");
                    hasHistgram = true;
                    togglePreviewMode();
                    break;
                default:
                    break;
            }
        }
    }

    private enum PreviewMode {DETECT, ADJUST};
    private PreviewMode mPreviewMode = PreviewMode.DETECT;
    private boolean hasHistgram = false;

    public void togglePreviewMode() {
        StringBuilder builder = new StringBuilder();
        builder.append("preview mode changed: " + mPreviewMode);
        mHandler.removeMessages(MSG_CALC_HIST);
        if (mPreviewMode == PreviewMode.DETECT) {
            hasHistgram = false;
            mPreviewMode = PreviewMode.ADJUST;
        } else {
            mPreviewMode = PreviewMode.DETECT;
        }
        builder.append(" --> " + mPreviewMode);
        Log.d(TAG, builder.toString());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preview);
        mOpenCvCameraView = (ControlableCameraView) findViewById(R.id.cameraSurfaceview);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
        mOpenCvCameraView.enableFpsMeter();
        mOpenCvCameraView.setMaxFrameSize(800, 480);

        mMotionControl = new MotionControl();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "onCameraViewStarted()");
        mWidth = width;
        mHeight = height;
        updateBorder();

        mGray = new Mat();
        mRgba = new Mat();
        mTemp = new Mat();
        mHsv = new Mat();
        m32f = new Mat();
        mBin = new Mat();
        mRoi = new Mat();
        mHist = new Mat();

        // ヒストグラムのパラメータ
        mChannels = new MatOfInt(0, 1);  // use only H and S
        mHistSize = new MatOfInt(30, 32);
        mRange = new MatOfFloat(0, 180, 0, 256);
        hasHistgram = false;

        TEXT_POINT = new Point(0, mHeight-10);
        Log.d(TAG, "set exposure = " + mExposure);
        mOpenCvCameraView.setExposure(mExposure);

        updateTargetPoint();
    }

    @Override
    public void onCameraViewStopped() {
        Log.d(TAG, "onCameraViewStopped()");
        mGray.release();
        mRgba.release();
        mTemp.release();
        mHsv.release();
        m32f.release();
        mBin.release();
        mRoi.release();
        mHist.release();
    }


    private static boolean isPortrait = false; // この試みは失敗に終わったので永久にfalse
    private static boolean isDebugDraw = false; // 輪郭線とか凹凸の線とかを画面表示
    private static final Scalar LOWER_RANGE = new Scalar(0, 23, 25);
    private static final Scalar UPPER_RANGE = new Scalar(24, 190, 228);
    private static final float EDGE_ANGLE = 60.0f;
    //private static final int MEDIAN_BLUR_THRESH = 11;
    private static final int FINGER_EDGE_THRESH = 6;
    private static final int ROI_SIZE_HALF = 20;

    private Point mAreaPoint1 = new Point();
    private Point mAreaPoint2 = new Point();
    private Point mAreaPoint3 = new Point();
    private Point mAreaPoint4 = new Point();
    private Point mAreaPoint5 = new Point();
    private Point mAreaPoint6 = new Point();
    private Point mAreaPoint7 = new Point();
    private Point mAreaPoint8 = new Point();

    private Point mTargetPoint1 = new Point();
    private Point mTargetPoint2 = new Point();

    private void rotate90() {
        MatOfPoint2f src = new MatOfPoint2f(
                new Point(100, 200), new Point(300, 200), new Point(300, 100));
        MatOfPoint2f dst = new MatOfPoint2f(
                new Point(100, 200), new Point(100, 400), new Point(200, 400));
        Mat rot = Imgproc.getAffineTransform(src, dst);
        Imgproc.warpAffine(mRgba, mRgba, rot, mRgba.size());
    }

    private void updateTargetPoint() {
        mTargetPoint1.x = mWidth/2 - ROI_SIZE_HALF;
        mTargetPoint1.y = mHeight/2 - ROI_SIZE_HALF;
        mTargetPoint2.x = mWidth/2 + ROI_SIZE_HALF;
        mTargetPoint2.y = mHeight/2 + ROI_SIZE_HALF;
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
        mAreaPoint1.x = mAreaPoint2.x = mAreaPoint5.x = mLeftBorder;
        mAreaPoint1.y = mAreaPoint3.y = 0;
        mAreaPoint2.y = mAreaPoint4.y = mAreaPoint8.y = mHeight;
        mAreaPoint3.x = mAreaPoint4.x = mAreaPoint6.x = mRightBorder;
        mAreaPoint5.y = mAreaPoint6.y = mAreaPoint7.y = mFrontBorder;
        mAreaPoint7.x = mAreaPoint8.x = mWidth/2;
    }

    private void drawAreaLine() {
        if (mPreviewMode == PreviewMode.DETECT) {
            Core.line(mRgba, mAreaPoint1, mAreaPoint2, AREA_COLOR, 2);
            Core.line(mRgba, mAreaPoint3, mAreaPoint4, AREA_COLOR, 2);
            Core.line(mRgba, mAreaPoint5, mAreaPoint6, AREA_COLOR, 2);
            // バックできないらしいので後ろの部分を左右に分割
            Core.line(mRgba, mAreaPoint7, mAreaPoint8, AREA_COLOR, 2);
        } else {
            Core.rectangle(mRgba, mTargetPoint1, mTargetPoint2, AREA_COLOR, 2);
        }
    }

    private void judgeArea(double x, double y) {
        if (Double.compare(x, mLeftBorder) < 0) {
            // turn left!
            mMotionControl.moveLeft();
        } else if (Double.compare(x, mRightBorder) > 0) {
            // turn right!
            mMotionControl.moveRight();
        } else if (Double.compare(y, mFrontBorder) < 0) {
            // move forward!
            mMotionControl.moveForward();
        } else {
            // move back!
            //mMotionControl.moveBack();
            // バックはできないらしいので中央から左右に回転
            if (Double.compare(x, mWidth/2) > 0) {
                mMotionControl.moveRight();
            } else {
                mMotionControl.moveLeft();
            }
        }
    }

    private void calcHistgram() {
        if (!mHandler.hasMessages(MSG_CALC_HIST)) {
            Log.d(TAG, "start timeer 5sec");
            mHandler.sendEmptyMessageDelayed(MSG_CALC_HIST, 5000);
        }
        Rect rect = new Rect(mTargetPoint1, mTargetPoint2);
        updateHistgram(rect);
    }

    private void updateHistgram(final Rect rect) {
        // set ROI
        mHsv.submat(rect).copyTo(mRoi);

        Mat mask = new Mat();  // doesn't use mask
        List<Mat> list = new ArrayList<Mat>();
        Core.split(mRoi, list);
        Imgproc.calcHist(list, mChannels, mask, mHist, mHistSize, mRange);
    }

    private void backProjection() {
        List<Mat> list = new ArrayList<Mat>();
        Core.split(mHsv, list);
        if (hasHistgram && mHist.dims() != 0) {
            Imgproc.calcBackProject(list, mChannels, mHist, mTemp, mRange, 1.0f);
            Core.inRange(mTemp, new Scalar(30), new Scalar(256), mBin);
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
        Imgproc.cvtColor(mTemp, mHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // (2) smooth with median
        // 遅くなるのでやめた
        //Imgproc.medianBlur(mHsv, mTemp, MEDIAN_BLUR_THRESH);
        //Imgproc.GaussianBlur(mHsv, mTemp, new Size(15,15), 8);

        // (3) skin color detection
        if (mPreviewMode == PreviewMode.DETECT) {
            if (hasHistgram) {
                backProjection();
            } else {
                // use pre-defined color range
                Core.inRange(mHsv, LOWER_RANGE, UPPER_RANGE, mBin);
            }
        } else {
            calcHistgram();
        }

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

        if (mPreviewMode == PreviewMode.ADJUST) {
            return mRgba;
        }

        // draw detected contours
        if (isDebugDraw) {
            Imgproc.drawContours(mRgba, contours, maxId, AREA_COLOR, 3);
        }
        // draw text "exposure locked"
        if (mOpenCvCameraView.isExposureLocked()) {
            Core.putText(mRgba, "Auto Exposure Locked",
                    TEXT_POINT, Core.FONT_HERSHEY_DUPLEX, 1.0f, FONT_COLOR);
        }

        // (7) find convex hull
        int edgeCount = 0, fingerCount = 0;
        if ((maxContours != null) &&
                (maxContours.checkVector(2, CvType.CV_32S) > 3)) {
            MatOfInt hull = new MatOfInt();
            MatOfInt4 detectedHull = new MatOfInt4();
            Imgproc.convexHull(maxContours, hull);
            Imgproc.convexityDefects(maxContours, hull, detectedHull);
            double x, y;
            float angle, angle1, angle2, pre_angle2 = 0.0f, pre_angle;
            int[] hulls = detectedHull.toArray();  //TODO: Use Matrix!
            // hull = {start_point, end_point, depth_point, depth}
            for (int i=0; i<hulls.length; i+=4) {
                // choose only deeper depth
                if (hulls[i+3] > 5000) {
                    Point[] points = maxContours.toArray(); //TODO: Use Matrix!
                    // vector1: start_point ---> depth_point
                    if (isDebugDraw) {
                        Core.line(mRgba,
                            points[hulls[i]], points[hulls[i+2]],
                            LINE_COLOR, 3);
                    }
                    x = points[hulls[i]].x - points[hulls[i+2]].x;
                    y = points[hulls[i]].y - points[hulls[i+2]].y;
                    angle1 = Core.fastAtan2((float)y, (float)x);
                    // vector2: end_point ---> depth_point
                    if (isDebugDraw) {
                        Core.line(mRgba,
                            points[hulls[i+1]], points[hulls[i+2]],
                            LINE_COLOR, 3);
                    }
                    x = points[hulls[i+1]].x - points[hulls[i+2]].x;
                    y = points[hulls[i+1]].y - points[hulls[i+2]].y;
                    angle2 = Core.fastAtan2((float)y, (float)x);
                    // angle between Vector1 and Vector2
                    angle = Float.compare(angle1, angle2) > 0 ?
                            (angle1 - angle2) : (angle2 - angle1);
                    //Log.d(TAG, "angle=" + angle);
                    if (Float.compare(angle, EDGE_ANGLE) < 0) {
                            edgeCount++;
                            fingerCount++;
                    }
                    if (i != 0) {
                        // angle between previous vector2 and current vector1
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
        //Log.d(TAG, "edge=" + edgeCount + " (finger=" + fingerCount + ")");
        if (maxContours != null && (edgeCount >= FINGER_EDGE_THRESH)) {
            // draw a circle which indicates "found hand!"
            Point[] points = maxContours.toArray();
            double sumx = 0.0f, sumy = 0.0f;
            int num = points.length;
            for (int i=0; i<num; i++) {
                sumx += points[i].x;
                sumy += points[i].y;
            }
            double centerx = sumx/num;
            double centery = sumy/num;
            int range = ROI_SIZE_HALF;
            Point lt = new Point(centerx - range, centery - range);
            Point rb = new Point(centerx + range, centery + range);
            Core.rectangle(mRgba, lt, rb, DETECT_COLOR, 3);
            // update histogram as current "hot" location
            updateHistgram(new Rect(lt, rb));
            hasHistgram = true;
            // (9) judge area (right or left)
            judgeArea(centerx, centery);
        } else {
            // stop
            mMotionControl.moveStop();
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
            mExposure = data.getIntExtra("exposure", 0);

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
        menu.add(0,5,0,"Setting");
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
                Intent intent = new Intent(this, SettingActivity.class);
                intent.putExtra("left", mLeftRatio);
                intent.putExtra("right", mRightRatio);
                intent.putExtra("middle", mMiddleRatio);
                intent.putExtra("front", mFrontRatio);
                intent.putExtra("rear", mRearRatio);
                intent.putExtra("max", mOpenCvCameraView.getMaxExposure());
                intent.putExtra("min", mOpenCvCameraView.getMinExposure());
                intent.putExtra("exposure", mExposure);
                intent.putExtra("exlock", mOpenCvCameraView.getAutoExposuerLockState());
                startActivityForResult(intent, 0);
            default:
                break;
        }
        return true;
    }
}
