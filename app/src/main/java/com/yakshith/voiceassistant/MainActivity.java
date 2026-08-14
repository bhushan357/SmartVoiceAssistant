package com.yakshith.voiceassistant;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_MIC_NOTIF = 300;

    private View pulseRing;
    private View glowCircle;
    private TextView tvStatus;
    private AnimatorSet pulseAnimator;
    private Button btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart   = findViewById(R.id.btnStart);
        pulseRing  = findViewById(R.id.pulseRing);
        glowCircle = findViewById(R.id.glowCircle);
        tvStatus   = findViewById(R.id.tvStatus);

        startIdleAnimations();

        btnStart.setOnClickListener(v -> attemptActivation());
    }

    // ─────────────────────────────────────────
    //  PERMISSION-AWARE ACTIVATION FLOW
    // ─────────────────────────────────────────
    private void attemptActivation() {

        // 1) Overlay permission (needed for the floating bubble)
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Allow floating window permission, then tap the button again",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // 2) Runtime permissions: RECORD_AUDIO (required for WakeWordService's
        //    SpeechRecognizer) and POST_NOTIFICATIONS (required on Android 13+
        //    for the foreground service notifications to actually show)
        java.util.List<String> needed = new java.util.ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERM_MIC_NOTIF);
            return;
        }

        startAssistantServices();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERM_MIC_NOTIF) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,
                            "Microphone permission is required for voice commands",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }
            startAssistantServices();
        }
    }

    // ─────────────────────────────────────────
    //  START BOTH SERVICES
    // ─────────────────────────────────────────
    private void startAssistantServices() {
        animateActivation(btnStart);
        tvStatus.setText("ACTIVATING…");

        btnStart.postDelayed(() -> {
            // Floating mic bubble
            Intent floatingIntent = new Intent(this, FloatingService.class);
            startForegroundService(floatingIntent);

            // ✅ FIX: WakeWordService was declared but never started anywhere.
            // This is what actually enables "hey assistant" / "wake up" detection.
            Intent wakeIntent = new Intent(this, WakeWordService.class);
            startForegroundService(wakeIntent);

            Toast.makeText(this, "Voice Assistant Started", Toast.LENGTH_SHORT).show();
            finish();
        }, 600);
    }

    // Idle pulsing glow animation
    private void startIdleAnimations() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(pulseRing, "scaleX", 1f, 1.15f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(pulseRing, "scaleY", 1f, 1.15f, 1f);
        ObjectAnimator alpha  = ObjectAnimator.ofFloat(pulseRing, "alpha", 0.3f, 0.8f, 0.3f);

        pulseAnimator = new AnimatorSet();
        pulseAnimator.playTogether(scaleX, scaleY, alpha);
        pulseAnimator.setDuration(2500);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setRepeatCount(ValueAnimator.INFINITE);

        scaleX.setDuration(2500);
        scaleY.setDuration(2500);
        alpha.setDuration(2500);

        scaleX.start();
        scaleY.start();
        alpha.start();

        ObjectAnimator glowAlpha = ObjectAnimator.ofFloat(glowCircle, "alpha", 0.08f, 0.2f, 0.08f);
        glowAlpha.setDuration(3500);
        glowAlpha.setRepeatCount(ValueAnimator.INFINITE);
        glowAlpha.setInterpolator(new AccelerateDecelerateInterpolator());
        glowAlpha.start();
    }

    private void animateActivation(View btn) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(btn, "scaleX", 1f, 0.9f, 1.1f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(btn, "scaleY", 1f, 0.9f, 1.1f, 1f);
        scaleX.setDuration(500);
        scaleY.setDuration(500);
        scaleX.start();
        scaleY.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pulseAnimator != null) pulseAnimator.cancel();
    }
}