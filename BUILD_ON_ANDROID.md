# Build Open Jarvis APK

## Easiest: GitHub Actions
1. Create a GitHub repository and upload this project.
2. Push to the `main` branch, or open **Actions → Build Open Jarvis APK → Run workflow**.
3. Open the completed workflow run and download the `open-jarvis-debug-apk` artifact.
4. Extract the artifact and install `app-debug.apk` on Android.

## Android Studio
Open this folder as an existing Gradle project. Use JDK 17 and Android SDK 34, then run:

```bash
gradle assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Important
This project does not contain an API key. After installation, configure an LLM provider in the app settings. Accessibility/overlay/microphone permissions may be required for device-control features.
