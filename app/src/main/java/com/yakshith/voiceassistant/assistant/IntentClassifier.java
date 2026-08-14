package com.yakshith.voiceassistant.assistant;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IntentClassifier {

    public static AssistantCommand classify(String text) {
        text = text.toLowerCase().trim();

        // ── CALL ──────────────────────────────────────────
        Pattern callPattern = Pattern.compile(
                "(?:please |can you |i want to |could you )?call ([a-z ]+?)(?:\\s+for me)?$"
        );
        Matcher callMatcher = callPattern.matcher(text);
        if (callMatcher.find()) {
            String name = callMatcher.group(1).trim();
            if (!name.isEmpty())
                return new AssistantCommand(IntentType.CALL_CONTACT, name);
        }

        // ── WHATSAPP MESSAGE ──────────────────────────────
        Pattern waPattern = Pattern.compile(
                "(?:send (?:a )?(?:whatsapp|message|msg|text)(?: to)?|" +
                        "(?:whatsapp|message|msg|text) (?:to )?)([a-z ]+?)(?:\\s+on whatsapp)?$"
        );
        Matcher waMatcher = waPattern.matcher(text);
        if (waMatcher.find()) {
            String name = waMatcher.group(1).trim();
            if (!name.isEmpty())
                return new AssistantCommand(IntentType.WHATSAPP_MSG, name);
        }
        if (text.contains("whatsapp") && !text.contains("open")) {
            String name = text.replace("whatsapp", "").replace("send", "")
                    .replace("message", "").replace("to", "").trim();
            if (!name.isEmpty())
                return new AssistantCommand(IntentType.WHATSAPP_MSG, name);
        }

        // ── ALARM ─────────────────────────────────────────
        Pattern alarmPattern = Pattern.compile(
                "(?:set (?:an )?alarm(?: for)?|wake me up(?: at)?|alarm(?: at)?) " +
                        "(?:at |for )?([0-9]{1,2}(?::[0-9]{2})? ?(?:am|pm)?)"
        );
        Matcher alarmMatcher = alarmPattern.matcher(text);
        if (alarmMatcher.find()) {
            return new AssistantCommand(IntentType.SET_ALARM, alarmMatcher.group(1).trim());
        }
        if (text.contains("alarm") || text.contains("wake me"))
            return new AssistantCommand(IntentType.SET_ALARM, extractTime(text));

        // ── TIMER ─────────────────────────────────────────
        Pattern timerPattern = Pattern.compile(
                "(?:set (?:a )?timer(?: for)?|timer|countdown) " +
                        "(?:for )?([0-9]+) ?(minutes?|mins?|seconds?|secs?|hours?|hrs?)"
        );
        Matcher timerMatcher = timerPattern.matcher(text);
        if (timerMatcher.find()) {
            return new AssistantCommand(IntentType.SET_TIMER,
                    timerMatcher.group(1) + " " + timerMatcher.group(2));
        }
        if (text.contains("timer")) return new AssistantCommand(IntentType.SET_TIMER, text);

        // ── OPEN APP ──────────────────────────────────────
        Pattern appPattern = Pattern.compile(
                "(?:open|launch|start|run|i want to open|can you open) ([a-z0-9 ]+?)(?:\\s+app)?$"
        );
        Matcher appMatcher = appPattern.matcher(text);
        if (appMatcher.find()) {
            String appName = appMatcher.group(1).trim();
            if (!appName.isEmpty())
                return new AssistantCommand(IntentType.OPEN_APP, appName);
        }

        // ── PLAY MUSIC ────────────────────────────────────
        if (text.contains("play") &&
                (text.contains("music") || text.contains("song") ||
                        text.contains("spotify") || text.contains("audio"))) {
            String query = text.replace("play", "").replace("some", "")
                    .replace("music", "").replace("songs", "").trim();
            return new AssistantCommand(IntentType.PLAY_MUSIC,
                    query.isEmpty() ? "music" : query);
        }

        // ── BATTERY ───────────────────────────────────────
        if (text.contains("battery") || text.contains("charge") || text.contains("charging"))
            return new AssistantCommand(IntentType.GET_BATTERY, null);

        // ── WEATHER ───────────────────────────────────────
        if (text.contains("weather") || text.contains("temperature") ||
                text.contains("rain") || text.contains("hot") || text.contains("cold"))
            return new AssistantCommand(IntentType.GET_WEATHER, extractLocation(text));

        // ── TIME ──────────────────────────────────────────
        if (text.contains("time") && !text.contains("timer"))
            return new AssistantCommand(IntentType.GET_TIME, null);

        // ── DATE ──────────────────────────────────────────
        if (text.contains("date") || text.contains("today") ||
                text.contains("day is") || text.contains("what day"))
            return new AssistantCommand(IntentType.GET_DATE, null);

        // ── GREETING ──────────────────────────────────────
        if (text.matches(".*(hello|hi|hey|good morning|good evening|good afternoon|what's up|wassup).*"))
            return new AssistantCommand(IntentType.GREETING, null);

        // ── SEARCH ────────────────────────────────────────
        Pattern searchPattern = Pattern.compile(
                "(?:search(?: for)?|google|look up|find|what is|who is|" +
                        "tell me about|show me) (.+)"
        );
        Matcher searchMatcher = searchPattern.matcher(text);
        if (searchMatcher.find())
            return new AssistantCommand(IntentType.SEARCH_WEB, searchMatcher.group(1).trim());

        // Fallback
        return new AssistantCommand(IntentType.UNKNOWN, text);
    }

    // ── HELPERS ───────────────────────────────────────────

    private static String extractTime(String text) {
        Pattern p = Pattern.compile("([0-9]{1,2}(?::[0-9]{2})? ?(?:am|pm)?)");
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : text;
    }

    // ✅ FIX: was `([a-z]+)` which only captured a single word — "weather in
    // new york" returned just "new". Now captures the rest of the phrase.
    private static String extractLocation(String text) {
        Pattern p = Pattern.compile("(?:in|at|for) ([a-z ]+)$");
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }
}