# Open Jarvis 1.1.0 upgrade

## Fixed / implemented

- Central Android accessibility action execution for app open, text tap, coordinate tap, long press, type, clear+type, swipe, scroll, Home, Back, Recents and wait-for-text.
- Accessibility node matching now checks content descriptions and can click a clickable parent.
- Android 11+ accessibility screenshot capture with ML Kit OCR, avoiding the old invalid `Surface.allocation` implementation.
- `ScreenReader(Context)` is safe before AccessibilityService startup; it resolves the service lazily.
- Agent execution now fails visibly when an action fails instead of marking the task successful.
- JSON action validation and parsing now support all advertised actions and single-object JSON responses.
- Clipboard read/write actions.
- Notification listener manifest registration, lifecycle singleton, privacy filtering and reply workflow checks.
- MCP action execution, configured-server discovery, API-key authorization headers and JSON-RPC result parsing.
- Scheduled automations now invoke `AgentCore`; Room DAO/entity mapping was corrected and WorkManager intervals are clamped to Android's 15-minute periodic minimum.
- Weekly natural-language schedule parsing.
- Foreground hands-free voice service with a configurable “Hey Jarvis” wake-phrase loop using Android SpeechRecognizer.
- Settings UI now persists voice enablement, speech output, STT mode and exposes Hands-Free Jarvis control.
- Version bumped to 1.1.0.

## Android requirements

The following are intentionally user-granted capabilities and cannot be bypassed by the app:

1. Accessibility Service — device UI automation and screen tree access.
2. Overlay permission — floating Jarvis UI.
3. Microphone permission — voice input.
4. Notification access — notification reading/reply features.
5. Network/API credentials — cloud LLM providers and MCP servers.

The hands-free service is a wake-phrase approximation based on Android SpeechRecognizer, not a dedicated low-power offline wake-word DSP. OEM battery/background restrictions can also affect continuous listening.

## Build note

The source was statically audited and corrected, but this environment does not contain an Android SDK/Gradle installation, so an actual APK build could not be executed here. Build the project in Android Studio with JDK 17 and the Android SDK/Gradle versions required by the project.
