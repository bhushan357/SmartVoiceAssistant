package com.yakshith.voiceassistant;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Locale;

/**
 * WakeWordService — listens continuously for wake words in the background.
 *
 * ✅ FIXES applied:
 *  - Was never started anywhere (see MainActivity) — now started alongside FloatingService.
 *  - NotificationCompat used instead of Notification.Builder(ctx, channelId) which
 *    crashes on API < 26.
 *  - Added pause()/resume() so it stops competing for the mic with
 *    VoiceCommandActivity's speech recognizer while a command is being processed.
 */
public class WakeWordService extends Service {

    private static final String TAG = "WakeWord";

    private static final String[] WAKE_WORDS = {
            "wake up", "hey assistant", "ok assistant",
            "hello assistant", "hi assistant", "start assistant"
    };

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private int errorCount = 0;
    private static final int MAX_ERRORS_BEFORE_BACKOFF = 5;
    private boolean isListening = false;

    // ✅ FIX: static instance + pause flag so VoiceCommandActivity can pause/resume us
    public static WakeWordService instance;
    private static volatile boolean isPaused = false;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        startForeground(2, createNotification());
        buildRecognizerIntent();
        startListening();
    }

    private void buildRecognizerIntent() {
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
    }

    private void startListening() {
        // ✅ FIX: don't start listening while paused (e.g. VoiceCommandActivity is active)
        if (isPaused || isListening) return;

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                errorCount = 0;

                ArrayList<String> list =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                float[] scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);

                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        String text = list.get(i).toLowerCase().trim();
                        float confidence = (scores != null && i < scores.length) ? scores[i] : 1.0f;

                        Log.d(TAG, "Heard: " + text + " (confidence: " + confidence + ")");

                        if (confidence >= 0.4f && isWakeWord(text)) {
                            Log.d(TAG, "Wake word detected: " + text);
                            triggerAssistant();
                            return;
                        }
                    }
                }

                restartWithDelay(300);
            }

            @Override
            public void onError(int error) {
                isListening = false;
                errorCount++;
                Log.w(TAG, "Error: " + error + " (count: " + errorCount + ")");

                long delay = errorCount > MAX_ERRORS_BEFORE_BACKOFF
                        ? Math.min(10000, 1000 * errorCount)
                        : 500;

                restartWithDelay(delay);
            }

            @Override public void onReadyForSpeech(Bundle params) { isListening = true; }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) {
                    String text = partial.get(0).toLowerCase().trim();
                    if (isWakeWord(text)) {
                        Log.d(TAG, "Wake word in partial: " + text);
                        triggerAssistant();
                    }
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        isListening = true;
        speechRecognizer.startListening(recognizerIntent);
    }

    private boolean isWakeWord(String text) {
        for (String wakeWord : WAKE_WORDS) {
            if (text.contains(wakeWord)) return true;
        }
        return false;
    }

    private void triggerAssistant() {
        destroyRecognizer();

        // ✅ FIX: pause ourselves instead of blindly restarting after a fixed delay —
        // VoiceCommandActivity will call resumeListening() when it's done.
        isPaused = true;

        if (FloatingService.instance == null) {
            Intent serviceIntent = new Intent(this, FloatingService.class);
            startForegroundService(serviceIntent);
        }

        Intent voiceIntent = new Intent(this, VoiceCommandActivity.class);
        voiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(voiceIntent);
    }

    private void restartWithDelay(long delayMs) {
        new android.os.Handler().postDelayed(() -> {
            destroyRecognizer();
            if (!isPaused) startListening();
        }, delayMs);
    }

    private void destroyRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        isListening = false;
    }

    // ✅ FIX: called by VoiceCommandActivity to stop competing for the mic
    public static void pauseListening() {
        isPaused = true;
        if (instance != null) instance.destroyRecognizer();
    }

    // ✅ FIX: called by VoiceCommandActivity (onDestroy) to resume wake-word listening
    public static void resumeListening() {
        isPaused = false;
        if (instance != null) instance.startListening();
    }

    private android.app.Notification createNotification() {
        String channelId = "wake_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Voice Assistant Wake Word", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        // ✅ FIX: NotificationCompat.Builder instead of Notification.Builder(ctx, channelId),
        // which requires API 26+ and would crash on older devices.
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Voice Assistant")
                .setContentText("Listening for wake word…")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyRecognizer();
        instance = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}