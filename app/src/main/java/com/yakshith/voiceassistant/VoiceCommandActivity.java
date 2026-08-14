package com.yakshith.voiceassistant;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.nl.entityextraction.EntityAnnotation;
import com.google.mlkit.nl.entityextraction.EntityExtraction;
import com.google.mlkit.nl.entityextraction.EntityExtractionParams;
import com.google.mlkit.nl.entityextraction.EntityExtractor;
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions;
import com.yakshith.voiceassistant.assistant.*;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VoiceCommandActivity extends AppCompatActivity {

    private static final int REQ_CODE     = 100;
    private static final int PERM_CONTACT = 200;
    private static final int PERM_CALL    = 201;
    private static final String TAG       = "VoiceCmd";

    private TextToSpeech tts;
    private EntityExtractor entityExtractor;

    private static IntentType lastIntent = null;
    private static String lastEntity     = null;
    private static int interactionCount  = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new android.view.View(this));

        if (FloatingService.instance != null)
            FloatingService.instance.hideBubble();

        // ✅ FIX: stop WakeWordService from competing for the mic while we're
        // actively listening for a command
        WakeWordService.pauseListening();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.getDefault());
        });

        entityExtractor = EntityExtraction.getClient(
                new EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        );
        entityExtractor.downloadModelIfNeeded()
                .addOnSuccessListener(u -> Log.d(TAG, "ML Kit ready"))
                .addOnFailureListener(e -> Log.w(TAG, "ML Kit fallback"));

        checkPermissionsAndStart();
    }

    private void checkPermissionsAndStart() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERM_CONTACT);
            return;
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, PERM_CALL);
            return;
        }
        startListening();
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        startActivityForResult(intent, REQ_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            if (results != null && !results.isEmpty()) {
                String topResult = results.get(0).toLowerCase().trim();
                Log.d(TAG, "Recognized: " + results);
                interactionCount++;

                // ✅ FIX: removed the redundant "call " special-case branch that
                // duplicated IntentClassifier's CALL_CONTACT handling and skipped
                // conversation memory. IntentClassifier already handles this.
                // ✅ FIX: removed the duplicate postDelayed(finish, 2500) here —
                // handleCommand() already schedules its own finish() at the right
                // time for each intent, so this was cutting TTS speech off early.
                handleCommand(topResult, results);
                return;
            }
        }
        new Handler().postDelayed(this::finish, 1600);
    }

    private void handleCommand(String text, List<String> allResults) {
        AssistantCommand command = IntentClassifier.classify(text);

        if (command.intent == IntentType.UNKNOWN && lastIntent != null) {
            command = handleFollowUp(text);
        }

        lastIntent = command.intent;
        lastEntity = command.entity;

        switch (command.intent) {

            case CALL_CONTACT:
                speak(getSentimentResponse("looking", command.entity));
                smartContactMatch(command.entity, allResults);
                return; // finish() handled by dialNumber()

            case WHATSAPP_MSG:
                speak("Opening WhatsApp for " + command.entity);
                openWhatsAppFor(command.entity);
                break;

            case SET_ALARM:
                setAlarm(command.entity);
                break;

            case SET_TIMER:
                setTimer(command.entity);
                break;

            case OPEN_APP:
                speak(getSentimentResponse("opening", command.entity));
                openAnyApp(command.entity);
                break;

            case PLAY_MUSIC:
                speak("Playing music");
                playMusic();
                break;

            case GET_BATTERY:
                speakBatteryStatus();
                break;

            case GET_WEATHER:
                openWeather(command.entity);
                break;

            case SEARCH_WEB:
                speak("Searching for " + command.entity);
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=" +
                                Uri.encode(command.entity != null ? command.entity : text))));
                break;

            case GET_TIME:
                speak("The time is " + DateFormat.getTimeInstance().format(new Date()));
                break;

            case GET_DATE:
                speak("Today is " + DateFormat.getDateInstance().format(new Date()));
                break;

            case GREETING:
                speak(getGreeting());
                break;

            default:
                speak("Sorry, I didn't understand that. Try saying open, call, or set alarm.");
        }

        new Handler().postDelayed(this::finish, 3000);
    }

    private AssistantCommand handleFollowUp(String text) {
        if (lastIntent == IntentType.CALL_CONTACT && lastEntity != null) {
            if (text.contains("try") || text.contains("mobile") || text.contains("home"))
                return new AssistantCommand(IntentType.CALL_CONTACT, lastEntity);
        }
        if (lastIntent == IntentType.OPEN_APP && lastEntity != null) {
            if (text.contains("again") || text.contains("retry"))
                return new AssistantCommand(IntentType.OPEN_APP, lastEntity);
        }
        return new AssistantCommand(IntentType.UNKNOWN, text);
    }

    private String getSentimentResponse(String action, String entity) {
        String name = entity != null ? entity : "";

        switch (action) {
            case "looking":
                String[] lookingResponses = {
                        "Looking for " + name,
                        "Let me find " + name + " for you",
                        "Searching for " + name
                };
                return lookingResponses[interactionCount % lookingResponses.length];

            case "opening":
                String[] openResponses = {
                        "Opening " + name,
                        "Launching " + name,
                        "Starting " + name + " for you"
                };
                return openResponses[interactionCount % openResponses.length];

            case "calling":
                String[] callResponses = {
                        "Calling " + name,
                        "Connecting you to " + name,
                        "Dialing " + name
                };
                return callResponses[interactionCount % callResponses.length];

            default:
                return action + " " + name;
        }
    }

    private String getGreeting() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12)      return "Good morning! How can I help you?";
        else if (hour < 17) return "Good afternoon! What can I do for you?";
        else                return "Good evening! How can I help?";
    }

    private void smartContactMatch(String spokenName, List<String> allHypotheses) {
        Map<String, String> allContacts = getAllContacts();

        EntityExtractionParams params = new EntityExtractionParams
                .Builder("call " + spokenName).build();

        entityExtractor.annotate(params)
                .addOnSuccessListener(annotations -> {
                    String mlName = extractCleanName(annotations, spokenName);
                    String matched = findBestContact(mlName, allHypotheses, allContacts);
                    if (matched != null) {
                        String number = allContacts.get(matched);
                        speak(getSentimentResponse("calling", matched));
                        new Handler().postDelayed(() -> dialNumber(number), 1200);
                    } else {
                        speak("I couldn't find that contact. Please try again.");
                        new Handler().postDelayed(this::finish, 2500);
                    }
                })
                .addOnFailureListener(e -> {
                    String matched = findBestContact(spokenName, allHypotheses, allContacts);
                    if (matched != null) {
                        String number = allContacts.get(matched);
                        speak(getSentimentResponse("calling", matched));
                        new Handler().postDelayed(() -> dialNumber(number), 1200);
                    } else {
                        speak("Contact not found.");
                        new Handler().postDelayed(this::finish, 2500);
                    }
                });
    }

    private String extractCleanName(List<EntityAnnotation> annotations, String fallback) {
        for (EntityAnnotation annotation : annotations) {
            if (!annotation.getEntities().isEmpty()) {
                String text = annotation.getAnnotatedText()
                        .toLowerCase().replaceFirst("call\\s+", "").trim();
                if (text.length() > 1) return text;
            }
        }
        return fallback;
    }

    private String findBestContact(String spokenName, List<String> allHypotheses,
                                   Map<String, String> allContacts) {
        String s     = spokenName.toLowerCase().trim();
        String withI = "i" + s;

        for (String c : allContacts.keySet())
            if (c.equalsIgnoreCase(s)) return c;

        for (String c : allContacts.keySet())
            if (c.split(" ")[0].equalsIgnoreCase(s)) return c;

        for (String c : allContacts.keySet()) {
            String cFirst = c.toLowerCase().split(" ")[0];
            if (cFirst.equals(withI) || c.equalsIgnoreCase(withI)) return c;
        }

        for (String hyp : allHypotheses) {
            String hypName = hyp.toLowerCase().replaceFirst("call\\s+", "").trim();
            for (String c : allContacts.keySet()) {
                if (c.equalsIgnoreCase(hypName)) return c;
                if (c.split(" ")[0].equalsIgnoreCase(hypName)) return c;
                if (c.split(" ")[0].equalsIgnoreCase("i" + hypName)) return c;
            }
        }

        for (String c : allContacts.keySet()) {
            String cLow = c.toLowerCase();
            if (cLow.startsWith(s) || cLow.split(" ")[0].startsWith(s)
                    || cLow.startsWith(withI)) return c;
        }

        String bestName = null; int bestScore = Integer.MAX_VALUE;
        for (String c : allContacts.keySet()) {
            String cLow = c.toLowerCase();
            int best = Math.min(
                    Math.min(levenshtein(s, cLow), levenshtein(s, cLow.split(" ")[0])),
                    Math.min(levenshtein(withI, cLow), levenshtein(withI, cLow.split(" ")[0]))
            );
            if (best < bestScore) { bestScore = best; bestName = c; }
        }
        return (bestScore <= 2) ? bestName : null;
    }

    private void setAlarm(String timeStr) {
        try {
            int hour = 0, minute = 0;
            timeStr = timeStr.toLowerCase().trim();
            boolean isPm = timeStr.contains("pm");
            timeStr = timeStr.replace("am", "").replace("pm", "").trim();

            if (timeStr.contains(":")) {
                String[] parts = timeStr.split(":");
                hour   = Integer.parseInt(parts[0].trim());
                minute = Integer.parseInt(parts[1].trim());
            } else {
                hour = Integer.parseInt(timeStr.trim());
            }
            if (isPm && hour != 12) hour += 12;

            Intent alarmIntent = new Intent(AlarmClock.ACTION_SET_ALARM);
            alarmIntent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            alarmIntent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            alarmIntent.putExtra(AlarmClock.EXTRA_MESSAGE, "Voice Assistant Alarm");
            alarmIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(alarmIntent);

            speak("Alarm set for " + (hour > 12 ? hour - 12 : hour) +
                    (minute > 0 ? ":" + minute : "") + (isPm ? " PM" : " AM"));
        } catch (Exception e) {
            speak("Sorry, I couldn't set the alarm. Please try again.");
            Log.e(TAG, "Alarm error: " + e.getMessage());
        }
    }

    private void setTimer(String timerStr) {
        try {
            long millis = 0;
            timerStr = timerStr.toLowerCase().trim();

            if (timerStr.contains("hour") || timerStr.contains("hr")) {
                int val = extractNumber(timerStr);
                millis = val * 3600000L;
            } else if (timerStr.contains("minute") || timerStr.contains("min")) {
                int val = extractNumber(timerStr);
                millis = val * 60000L;
            } else if (timerStr.contains("second") || timerStr.contains("sec")) {
                int val = extractNumber(timerStr);
                millis = val * 1000L;
            }

            if (millis > 0) {
                Intent timerIntent = new Intent(AlarmClock.ACTION_SET_TIMER);
                timerIntent.putExtra(AlarmClock.EXTRA_LENGTH, (int)(millis / 1000));
                timerIntent.putExtra(AlarmClock.EXTRA_MESSAGE, "Voice Assistant Timer");
                timerIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                timerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(timerIntent);
                speak("Timer set for " + timerStr);
            } else {
                speak("Sorry, I couldn't understand the timer duration.");
            }
        } catch (Exception e) {
            speak("Sorry, couldn't set the timer.");
            Log.e(TAG, "Timer error: " + e.getMessage());
        }
    }

    private int extractNumber(String text) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(\\d+)").matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private void openWhatsAppFor(String contactName) {
        Map<String, String> contacts = getAllContacts();
        String matched = findBestContact(contactName,
                new ArrayList<String>() {{ add(contactName); }}, contacts);

        if (matched != null) {
            String number = contacts.get(matched);
            if (number != null) {
                number = number.replaceAll("[\\s\\-()]", "");
                // NOTE: hardcoded +91 (India) country code — change this if you're
                // not in India, or better, store the user's country code in prefs.
                if (number.startsWith("0")) number = "+91" + number.substring(1);

                try {
                    Intent whatsappIntent = new Intent(Intent.ACTION_VIEW);
                    whatsappIntent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + number));
                    whatsappIntent.setPackage("com.whatsapp");
                    whatsappIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(whatsappIntent);
                    speak("Opening WhatsApp for " + matched);
                } catch (Exception e) {
                    speak("WhatsApp is not installed.");
                }
            }
        } else {
            speak("Contact not found for WhatsApp.");
        }
    }

    private void openAnyApp(String appName) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        String appNameLower = appName.toLowerCase().trim();
        String bestPkg      = null;
        int    bestScore    = Integer.MAX_VALUE;

        for (ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) == null) continue;

            String label = pm.getApplicationLabel(app).toString().toLowerCase();

            if (label.equals(appNameLower)) { bestPkg = app.packageName; break; }

            if (label.contains(appNameLower) || appNameLower.contains(label)) {
                int score = Math.abs(label.length() - appNameLower.length());
                if (score < bestScore) { bestScore = score; bestPkg = app.packageName; }
            }

            int dist = levenshtein(appNameLower, label);
            if (dist <= 2 && dist < bestScore) { bestScore = dist; bestPkg = app.packageName; }
        }

        if (bestPkg != null) {
            Intent launchIntent = pm.getLaunchIntentForPackage(bestPkg);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                return;
            }
        }

        speak("I couldn't find " + appName + " on your device.");
        Toast.makeText(this, "App not found: " + appName, Toast.LENGTH_SHORT).show();
    }

    private void playMusic() {
        if (!openPackage("com.spotify.music")) {
            if (!openPackage("com.google.android.apps.youtube.music")) {
                Intent musicIntent = new Intent(Intent.ACTION_MAIN);
                musicIntent.addCategory(Intent.CATEGORY_APP_MUSIC);
                musicIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(musicIntent); }
                catch (Exception e) { speak("No music app found."); }
            }
        }
    }

    private void speakBatteryStatus() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            int level  = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale  = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

            int pct = (int)((level / (float) scale) * 100);

            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;

            String msg = "Your battery is at " + pct + " percent";
            if (isCharging) msg += " and is currently charging.";
            else if (pct < 20) msg += ". Battery is low, please charge soon.";
            else msg += ".";

            speak(msg);
        } else {
            speak("Unable to read battery status.");
        }
    }

    private void openWeather(String location) {
        String query = location != null ? "weather in " + location : "weather";
        startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))));
        speak("Showing weather" + (location != null ? " for " + location : ""));
    }

    private Map<String, String> getAllContacts() {
        Map<String, String> contacts = new HashMap<>();
        Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                }, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String name   = cursor.getString(0);
                String number = cursor.getString(1);
                if (name != null && number != null) contacts.put(name, number);
            }
            cursor.close();
        }
        return contacts;
    }

    private void dialNumber(String number) {
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + number));
        startActivity(callIntent);
        new Handler().postDelayed(this::finish, 1000);
    }

    private boolean openPackage(String pkg) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private int levenshtein(String a, String b) {
        a = a.toLowerCase(); b = b.toLowerCase();
        int[] dp = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) dp[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int prev = dp[0]; dp[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int temp = dp[j];
                dp[j] = a.charAt(i-1) == b.charAt(j-1) ? prev
                        : 1 + Math.min(prev, Math.min(dp[j], dp[j-1]));
                prev = temp;
            }
        }
        return dp[b.length()];
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            checkPermissionsAndStart();
        else finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (entityExtractor != null) entityExtractor.close();
        if (FloatingService.instance != null) FloatingService.instance.showBubbleAgain();

        // ✅ FIX: resume wake-word listening now that we're done handling this command
        WakeWordService.resumeListening();
    }
}