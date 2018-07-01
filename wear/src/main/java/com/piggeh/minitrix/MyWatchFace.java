package com.piggeh.minitrix;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. Defaults to one second
     * because the watch face needs to update seconds in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toSeconds(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private float mXOffset;
        private float mYOffset;
        private Paint mBackgroundPaint;
        private Paint mTextPaint;
        private float mCenterX;
        private int mWidth;
        private Bitmap mBackgroundOn;
        private Bitmap mBackgroundOff;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mAmbient;

        private SharedPreferences mPrefs;
        private boolean mUse24hr;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            // Initialize SharedPreferences, used for loading settings
            mPrefs = getSharedPreferences("prefs", MODE_PRIVATE);
            mUse24hr = mPrefs.getBoolean(getString(R.string.pref_24hr_key), getResources().getBoolean(R.bool.pref_24hr_default));

            mCalendar = Calendar.getInstance();

            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            // Initializes background.
            mBackgroundPaint = new Paint();

            // Initializes text.
            mTextPaint = new Paint();
            mTextPaint.setTypeface(NORMAL_TYPEFACE);
            mTextPaint.setAntiAlias(true);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            mTextPaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            if (isRound){
                mBackgroundOn = BitmapFactory.decodeResource(resources, R.drawable.bg_round_on);
                mBackgroundOff = BitmapFactory.decodeResource(resources, R.drawable.bg_round_off);
            } else {
                mBackgroundOn = BitmapFactory.decodeResource(resources, R.drawable.bg_square_on);
                mBackgroundOff = BitmapFactory.decodeResource(resources, R.drawable.bg_square_off);
            }

            if (mBackgroundOn != null){
                float mScale = ((float) mWidth) / (float) mBackgroundOn.getWidth();
                mBackgroundOn = Bitmap.createScaledBitmap
                        (mBackgroundOn, (int)(mBackgroundOn.getWidth() * mScale),
                                (int)(mBackgroundOn.getHeight() * mScale), true);
                mBackgroundOff = Bitmap.createScaledBitmap
                        (mBackgroundOff, (int)(mBackgroundOff.getWidth() * mScale),
                                (int)(mBackgroundOff.getHeight() * mScale), true);
            }

            mTextPaint.setTextSize(textSize);
            buildStyle();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            mAmbient = inAmbientMode;
            if (mLowBitAmbient) {
                mTextPaint.setAntiAlias(!inAmbientMode);
            }

            //update prefs. this is probably not the best way to do this but it's better than in onDraw()

                buildStyle();

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mWidth = width;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (mAmbient) {
                canvas.drawBitmap(mBackgroundOff, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawBitmap(mBackgroundOn, 0, 0, mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = mUse24hr
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR_OF_DAY),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE));
            canvas.drawText(text, mCenterX, mCenterX + (mTextPaint.getTextSize() / 4), mTextPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private void buildStyle() {
            WatchFaceStyle.Builder style = new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setAcceptsTapEvents(false)
                    .setStatusBarGravity(Gravity.CENTER | (mWidth / 3))
                    .setAmbientPeekMode(WatchFaceStyle.AMBIENT_PEEK_MODE_VISIBLE)
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N){
                //Watch is running Android Wear 1.5 or lower, apply settings for 1.X

                //Apply Tall Peek Cards preference
                if (mPrefs.getBoolean(getString(R.string.pref_tall_key), getResources().getBoolean(R.bool.pref_tall_default))){
                    //Peek cards should be tall
                    style.setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE);
                } else {
                    //Peek cards should be short
                    style.setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT);
                }
                //Apply Translucent Peek Cards preference
                if (mPrefs.getBoolean(getString(R.string.pref_seethru_key), getResources().getBoolean(R.bool.pref_seethru_default))){
                    //Peek cards should be translucent
                    style.setPeekOpacityMode(WatchFaceStyle.PEEK_OPACITY_MODE_TRANSLUCENT);
                } else {
                    //Peek cards should be opaque
                    style.setPeekOpacityMode(WatchFaceStyle.PEEK_OPACITY_MODE_OPAQUE);
                }
            }
            setWatchFaceStyle(style.build());
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
