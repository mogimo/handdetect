package com.example.handdetector;

import org.opencv.android.JavaCameraView;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;

public class ControlableCameraView extends JavaCameraView {
    private static final String TAG = "HandDetector";

    public ControlableCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
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

}
