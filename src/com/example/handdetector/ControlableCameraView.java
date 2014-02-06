package com.example.handdetector;

import org.opencv.android.JavaCameraView;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Camera;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Toast;

public class ControlableCameraView extends JavaCameraView {
    private static final String TAG = "HandDetector";

    private Context mContext;
    private TouchGestureListener mListener = new TouchGestureListener();
    private GestureDetector mTouchDetector;

    private boolean isExposureLocked = false;

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private class TouchGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            Camera.Parameters params = mCamera.getParameters();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                if (params.isAutoExposureLockSupported()) {
                    isExposureLocked = !isExposureLocked;
                    params.setAutoExposureLock(isExposureLocked);
                    mCamera.setParameters(params);
                    String message = (isExposureLocked) ? "Locked" : "Unlocked";
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
            return super.onDoubleTap(e);
        }
        @Override
        public void onLongPress(MotionEvent e) {
            ((PreviewActivity)mContext).togglePreviewMode();
            super.onLongPress(e);
        }
    }

    public ControlableCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mTouchDetector = new GestureDetector(getContext(), mListener);
    }

    public int getMaxExposure() {
        int max = 0;
        Camera.Parameters params = mCamera.getParameters();
        if (params != null) {
            max = params.getMaxExposureCompensation();
        }
        Log.d(TAG,"max exposure" + max);
        return max;
    }

    public int getMinExposure() {
        int min = 0;
        Camera.Parameters params = mCamera.getParameters();
        if (params != null) {
            min = params.getMinExposureCompensation();
        }
        Log.d(TAG,"max exposure" + min);
        return min;
    }

    public void setExposure(int value) {
        Camera.Parameters params = mCamera.getParameters();
        params.setExposureCompensation(value);
        mCamera.setParameters(params);
    }

    public boolean isExposureLocked() {
        return isExposureLocked;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public boolean getAutoExposuerLockState() {
        Camera.Parameters params = mCamera.getParameters();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (params.isAutoExposureLockSupported()) {
                return params.getAutoExposureLock();
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mTouchDetector.onTouchEvent(event);
        return true;
    }
}
