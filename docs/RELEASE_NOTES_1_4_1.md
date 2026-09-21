# Open Jarvis 1.4.1

## Permission system hardening
- Centralized permission/access checks for Accessibility, overlay, microphone, notifications, and notification-listener access.
- Runtime microphone and Android 13+ notification permission requests use AndroidX activity result APIs.
- Settings-based permissions are re-checked whenever the app resumes, so returning from Settings updates the UI immediately.
- Safe fallbacks are used when an OEM does not expose a requested Settings page.
- Missing permissions are shown with clear enabled/disabled state instead of static checkmarks.
- Overlay service startup verifies overlay access before attempting to start the service.
- Added onboarding activity declaration to the manifest for safe explicit navigation.

Screen capture remains feature-scoped: MediaProjection consent should be requested only when a screen-capture feature is invoked, rather than as an unconditional startup permission.
