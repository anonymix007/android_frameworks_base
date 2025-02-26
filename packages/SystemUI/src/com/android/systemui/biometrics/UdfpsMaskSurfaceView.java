/*
 * Copyright (C) 2025 Paranoid Android
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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;

import com.android.systemui.res.R;

public class UdfpsMaskSurfaceView extends UdfpsSurfaceView {
    private static final String TAG = "UdfpsMaskSurfaceView";

    @NonNull private final Paint mMaskPaint;
    @NonNull private final float[] mMaskAlphaValues;

    public UdfpsMaskSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setZOrderOnTop(true);

        mMaskPaint = new Paint(0 /* flags */);
        mMaskPaint.setAntiAlias(true);
        mMaskPaint.setColor(Color.BLACK);
        mMaskPaint.setStyle(Paint.Style.FILL);

        mSensorPaint.setColor(Color.TRANSPARENT);

        // TODO: move to resources
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

    private void updateMaskViewAlpha() {
        int systemBrightness = getSystemBrightness() / 2;
        if (systemBrightness < 0 || systemBrightness > 127) {
            Log.e(TAG, " Brightness out of bounds: " + systemBrightness);
            systemBrightness = 64;
        }
        float a = mMaskAlphaValues[systemBrightness];
        Log.d(TAG, "Updating alpha: " + a);
        setAlpha(a);
    }

    private int getSystemBrightness() {
        int i = 0;
        try {
            i = Settings.System.getInt(this.mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return i;
    }

    @Override public void drawIlluminationDot(@NonNull RectF sensorRect) {
        if (!mHasValidSurface) {
            Log.e(TAG, "drawIlluminationDot | the surface is destroyed or was never created.");
            return;
        }
        Canvas canvas = null;
        try {
            updateMaskViewAlpha();
            canvas = mHolder.lockCanvas();
            canvas.drawPaint(mMaskPaint);
            canvas.drawOval(sensorRect, mSensorPaint);
        } finally {
            if (canvas != null) {
                mHolder.unlockCanvasAndPost(canvas);
            }
        }
    }
}
