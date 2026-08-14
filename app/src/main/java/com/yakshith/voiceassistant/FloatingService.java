package com.yakshith.voiceassistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.*;
import android.widget.ImageView;

import androidx.core.app.NotificationCompat;

public class FloatingService extends Service {

    private WindowManager windowManager;
    private View floatingView;

    public static FloatingService instance;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        startForeground(1, buildNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        if (Settings.canDrawOverlays(this)) {
            addFloatingBubble();
        }

        instance = this;
    }

    private void addFloatingBubble() {

        if (floatingView != null) return;

        floatingView = LayoutInflater.from(this)
                .inflate(R.layout.floating_bubble, null);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dpToPx(70),
                dpToPx(70),
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 300;

        windowManager.addView(floatingView, params);

        ImageView mic = floatingView.findViewById(R.id.btnMic);

        mic.setOnTouchListener(new View.OnTouchListener() {

            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - touchStartTime;

                        if (duration > 800) {
                            stopSelf();
                        } else if (duration < 200) {
                            hideBubble();

                            Intent intent = new Intent(
                                    FloatingService.this,
                                    VoiceCommandActivity.class
                            );
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ✅ FIX: NotificationCompat.Builder instead of Notification.Builder(ctx, channelId),
    // which requires API 26+ and would crash on older devices.
    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, "mic_channel")
                .setContentTitle("Voice Assistant")
                .setContentText("Floating mic running")
                .setSmallIcon(R.drawable.ic_mic)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "mic_channel",
                    "Floating Mic",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(channel);
        }
    }

    public void hideBubble() {
        if (floatingView != null) {
            floatingView.setVisibility(View.GONE);
        }
    }

    public void showBubbleAgain() {
        if (floatingView != null) {
            floatingView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
            floatingView = null;
        }

        stopForeground(true);
        instance = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}