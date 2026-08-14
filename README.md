# Smart Voice Assistant (NOVA)

An Android voice assistant app featuring a draggable floating mic bubble, background wake-word detection, and natural language command handling — built entirely in Java for Android Studio.

Say a wake word or tap the floating bubble, speak a command, and NOVA handles the rest: calling contacts, sending WhatsApp messages, setting alarms, opening apps, checking your battery, and more.

## Features

- **Floating mic bubble** — a draggable overlay bubble that works over any app
- **Wake word detection** — say "hey assistant," "wake up," or similar phrases to activate hands-free
- **Natural language understanding** — regex-based intent classification handles varied phrasing, not just exact keywords
- **Smart contact matching** — fuzzy matching (ML Kit entity extraction + Levenshtein distance) finds the right contact even with imperfect speech recognition
- **Text-to-speech responses** — NOVA speaks back, with varied phrasing so it doesn't sound robotic
- **Conversation memory** — basic follow-up handling (e.g. "call John" → "try mobile" retries the same contact)
- **Animated UI** — pulsing glow and mic animations on the home screen

## Voice Commands

| Say something like... | What happens |
|---|---|
| "Call John" / "Can you call mom for me" | Finds the best-matching contact and dials them |
| "Send WhatsApp to John" | Opens WhatsApp directly to that contact |
| "Set an alarm for 7am" | Opens the clock app with the alarm pre-set |
| "Set a timer for 10 minutes" | Opens the clock app with the timer pre-set |
| "Open YouTube" / "Launch Instagram" | Finds and launches any installed app |
| "Play some music" | Opens Spotify → falls back to YouTube Music → falls back to default player |
| "What's my battery" | Speaks current battery % and charging status |
| "What's the weather in Mumbai" | Opens a weather search for that location |
| "What's the time" / "What's the date" | Speaks the current time or date |
| "Search for cats" / "What is gravity" | Opens a Google search |
| "Hello" / "Good morning" | Time-appropriate greeting |

## Tech Stack

- **Language:** Java
- **Min SDK:** 21 · **Target/Compile SDK:** 36
- **Speech Recognition:** Android `SpeechRecognizer` / `RecognizerIntent`
- **NLU:** Custom regex-based intent classifier (`IntentClassifier`)
- **Contact Matching:** Google ML Kit Entity Extraction + Levenshtein distance
- **Text-to-Speech:** Android `TextToSpeech`
- **UI:** `ConstraintLayout`, `ObjectAnimator` for pulse/glow effects

## Architecture

```
com.yakshith.voiceassistant
├── assistant/
│   ├── AssistantCommand.java     # Data model: intent + extracted entity
│   ├── IntentClassifier.java     # Regex-based NLU — text → AssistantCommand
│   └── IntentType.java           # Enum of all supported command types
├── MainActivity.java             # Launcher screen, permissions, starts services
├── FloatingService.java          # Draggable overlay mic bubble
├── WakeWordService.java          # Background wake-word listener
└── VoiceCommandActivity.java     # Captures speech, executes commands
```

**Flow:** `MainActivity` → grants permissions → starts `FloatingService` (+ `WakeWordService` if enabled) → tapping the bubble or saying a wake word opens `VoiceCommandActivity` → speech is classified by `IntentClassifier` → the matching action executes (call, open app, alarm, etc.) → TTS confirms the result.

## Permissions Required

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Speech recognition |
| `READ_CONTACTS` | Matching spoken names to contacts |
| `CALL_PHONE` | Placing calls |
| `SYSTEM_ALERT_WINDOW` | Floating bubble overlay |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | Keeping the bubble/listener alive in the background |
| `POST_NOTIFICATIONS` | Foreground service notifications on Android 13+ |
| `INTERNET` | One-time ML Kit model download |

## Setup

1. Clone the repo
2. Open in Android Studio
3. Let Gradle sync (dependencies: `androidx.constraintlayout`, `com.google.mlkit:entity-extraction`, `org.tensorflow:tensorflow-lite`)
4. Run on a physical device (mic/overlay permissions are unreliable on emulators)
5. On first launch, grant overlay, microphone, contacts, and notification permissions when prompted

## Known Limitations

- Wake-word listening uses Android's online `SpeechRecognizer`, which briefly pauses/ducks other audio (e.g. music) each time it listens — this is inherent to the API, not a bug
- Some OEMs (e.g. Vivo/Funtouch) aggressively restrict background mic access — enable "Allow all the time" microphone permission and disable battery optimization for the app if wake-word detection isn't triggering
- WhatsApp number formatting currently assumes an Indian (+91) country code as a fallback

## Roadmap Ideas

- Offline wake-word detection (e.g. Picovoice Porcupine) to avoid disturbing background media
- More commands: SMS, notes, brightness, Wi-Fi toggle
- Improved NLU for messier/more varied speech

---

Built with Android Studio.
