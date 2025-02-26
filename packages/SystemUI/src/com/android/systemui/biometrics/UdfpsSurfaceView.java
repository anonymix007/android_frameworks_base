/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.biometrics;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.android.systemui.res.R;

/**
 * Surface View for providing the Global High-Brightness Mode (GHBM) illumination for UDFPS.
 */
public class UdfpsSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "UdfpsSurfaceView";

    /**
     * Notifies {@link UdfpsView} when to enable GHBM illumination.
     */
    interface GhbmIlluminationListener {
        /**
         * @param surface the surface for which GHBM should be enabled.
         * @param onDisplayConfigured a runnable that should be run after GHBM is enabled.
         */
        void enableGhbm(@NonNull Surface surface, @Nullable Runnable onDisplayConfigured);
    }

    @NonNull private final SurfaceHolder mHolder;
    @NonNull private final Paint mSensorPaint;

    @Nullable private GhbmIlluminationListener mGhbmIlluminationListener;
    @Nullable private Runnable mOnDisplayConfigured;
    boolean mAwaitingSurfaceToStartIllumination;
    boolean mHasValidSurface;

    private Drawable mUdfpsIconPressed;

    @NonNull private final Paint mCutoutPaint;
    @NonNull private final Paint mMaskPaint;
    private final float[] mMaskAlphaValues;

    public UdfpsSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Make this SurfaceView draw on top of everything else in this window. This allows us to
        // 1) Always show the HBM circle on top of everything else, and
        // 2) Properly composite this view with any other animations in the same window no matter
        //    what contents are added in which order to this view hierarchy.
        setZOrderOnTop(true);

        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setFormat(PixelFormat.RGBA_8888);

        mMaskPaint = new Paint(0 /* flags */);
        mMaskPaint.setAntiAlias(true);
        mMaskPaint.setColor(Color.BLACK);
        mMaskPaint.setStyle(Paint.Style.FILL);

        mSensorPaint = new Paint(0 /* flags */);
        mSensorPaint.setAntiAlias(true);
        mSensorPaint.setColor(context.getColor(R.color.config_udfpsColor));
        mSensorPaint.setStyle(Paint.Style.FILL);
        mSensorPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));

        // TODO: can we drop this?
        mCutoutPaint = new Paint(0 /* flags */);
        mCutoutPaint.setAntiAlias(true);
        mCutoutPaint.setStyle(Paint.Style.FILL);
        mCutoutPaint.setColor(Color.TRANSPARENT);
        mCutoutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        mUdfpsIconPressed = context.getDrawable(R.drawable.udfps_icon_pressed);

        // TODO: move to resources and make null by default
        mMaskAlphaValues = new float[] {
            0.92f,   0.92f,   0.92f,   0.92f,   0.92f,   0.91f,   0.905f,  0.905f,
            0.91f,   0.91f,   0.8985f, 0.8939f, 0.8893f, 0.8847f, 0.8801f, 0.8775f,
            0.8729f, 0.8683f, 0.8637f, 0.8591f, 0.8525f, 0.8465f, 0.8405f, 0.8345f,
            0.8285f, 0.8245f, 0.8185f, 0.8095f, 0.8055f, 0.8015f, 0.7955f, 0.7891f,
            0.7847f, 0.7783f, 0.7699f, 0.7635f, 0.7571f, 0.7507f, 0.7443f, 0.7379f,
            0.7315f, 0.7244f, 0.7174f, 0.7104f, 0.7034f, 0.6964f, 0.6894f, 0.6824f,
            0.6754f, 0.6684f, 0.6614f, 0.6559f, 0.6504f, 0.6449f, 0.6394f, 0.6339f,
            0.6284f, 0.6229f, 0.6174f, 0.6119f, 0.6064f, 0.6004f, 0.5954f, 0.5874f,
            0.5794f, 0.5714f, 0.5634f, 0.5554f, 0.5474f, 0.5394f, 0.5334f, 0.5254f,
            0.5174f, 0.5094f, 0.5012f, 0.495f,  0.489f,  0.483f,  0.477f,  0.471f,
            0.465f,  0.459f,  0.453f,  0.447f,  0.441f,  0.435f,  0.428f,  0.421f,
            0.414f,  0.407f,  0.4f,    0.393f,  0.386f,  0.379f,  0.372f,  0.365f,
            0.357f,  0.349f,  0.341f,  0.333f,  0.325f,  0.319f,  0.313f,  0.307f,
            0.301f,  0.295f,  0.289f,  0.283f,  0.277f,  0.271f,  0.265f,  0.259f,
            0.253f,  0.247f,  0.241f,  0.235f,  0.228f,  0.221f,  0.214f,  0.207f,
            0.2f,    0.1967f, 0.1934f, 0.19f,   0.185f,  0.175f,  0.155f,  0.145f,
        };
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        mHasValidSurface = true;
        if (mAwaitingSurfaceToStartIllumination) {
            doIlluminate(mOnDisplayConfigured);
            mOnDisplayConfigured = null;
            mAwaitingSurfaceToStartIllumination = false;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Unused.
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        mHasValidSurface = false;
    }

    public void setGhbmIlluminationListener(@Nullable GhbmIlluminationListener listener) {
        mGhbmIlluminationListener = listener;
    }

    /**
     * Note: there is no corresponding method to stop GHBM illumination. It is expected that
     * {@link UdfpsView} will hide this view, which would destroy the surface and remove the
     * illumination dot.
     */
    public void startGhbmIllumination(@Nullable Runnable onDisplayConfigured) {
        if (mGhbmIlluminationListener == null) {
            Log.e(TAG, "startIllumination | mGhbmIlluminationListener is null");
            return;
        }

        if (mHasValidSurface) {
            doIlluminate(onDisplayConfigured);
        } else {
            mAwaitingSurfaceToStartIllumination = true;
            mOnDisplayConfigured = onDisplayConfigured;
        }
    }

    private void doIlluminate(@Nullable Runnable onDisplayConfigured) {
        if (mGhbmIlluminationListener == null) {
            Log.e(TAG, "doIlluminate | mGhbmIlluminationListener is null");
            return;
        }

        mGhbmIlluminationListener.enableGhbm(mHolder.getSurface(), onDisplayConfigured);
    }

    private void updateMaskPaintAlpha() {
        int systemBrightness = 0;
        try {
           systemBrightness = Settings.System.getInt(this.mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 2;
        } catch (Exception e) {
            Log.e(TAG, "Cannot get current brightness", e);
        }

        if (systemBrightness < 0 || systemBrightness > 127) {
            Log.e(TAG, "Brightness is out of bounds: " + systemBrightness);
            systemBrightness = 64;
        }
        float a = mMaskAlphaValues[systemBrightness];
        Log.d(TAG, "Mask alpha: " + a);
        mMaskPaint.setAlpha(Math.round(a * 255));
    }

    /**
     * Immediately draws the illumination dot on this SurfaceView's surface.
     */
    public void drawIlluminationDot(@NonNull RectF sensorRect) {
        if (!mHasValidSurface) {
            Log.e(TAG, "drawIlluminationDot | the surface is destroyed or was never created.");
            return;
        }
        Canvas canvas = null;
        try {
            canvas = mHolder.lockCanvas();
            if (mMaskAlphaValues != null) {
                updateMaskPaintAlpha();
                canvas.drawPaint(mMaskPaint);
                canvas.drawOval(sensorRect, mCutoutPaint);
            }
            mUdfpsIconPressed.setBounds(
                    Math.round(sensorRect.left),
                    Math.round(sensorRect.top),
                    Math.round(sensorRect.right),
                    Math.round(sensorRect.bottom)
            );
            mUdfpsIconPressed.draw(canvas);
            canvas.drawOval(sensorRect, mSensorPaint);
        } finally {
            // Make sure the surface is never left in a bad state.
            if (canvas != null) {
                mHolder.unlockCanvasAndPost(canvas);
            }
        }
    }
}
