# Build OpenJarvis 1.4.1 APK without Android Studio

This project includes a GitHub Actions workflow at `.github/workflows/android-apk.yml`.

## Steps

1. Create a GitHub repository.
2. Upload the contents of this project to the repository.
3. Open **Actions** → **OpenJarvis Android APK**.
4. Run **Build workflow**.
5. Wait for tests, APK build and lint to finish successfully.
6. Open the completed workflow run → **Artifacts** → `OpenJarvis-1.4.1-debug-apk`.
7. Download the ZIP on your Android phone, extract it, and install `app-debug.apk`.

The workflow uses JDK 17, Android SDK 34, Build Tools 34.0.0 and Gradle 8.2 to match this project's Android Gradle Plugin 8.2.0 configuration.

## Device test

Install the APK on the phone and test permissions one by one:

- Accessibility Service
- Display over other apps
- Microphone
- Notifications (Android 13+)
- Notification Access
- Screen capture when the screenshot feature is used

Also test denial/retry, app restart, device reboot, dark mode, rotation, and background/foreground transitions.

A successful GitHub Actions build verifies compilation, unit tests and lint. It cannot certify every physical Android device; real-device testing is still required for OEM-specific behavior such as Xiaomi/MIUI, Samsung/One UI, Oppo/ColorOS, Vivo/Funtouch OS and aggressive battery management.
