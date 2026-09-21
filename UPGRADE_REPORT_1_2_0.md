# Open Jarvis 1.2.0 Upgrade Report

Added:
1. `finance/TradingEngine.kt` — paper-first Alpaca trading adapter, market data, technical analysis and guarded order execution.
2. `web/WebsiteBuilder.kt` — static website generator/exporter.
3. `apps/AppInstaller.kt` — HTTPS APK downloader + Android package-installer handoff.
4. `science/ScienceEngine.kt` — deterministic offline science calculations.
5. New agent actions: `trade_analyze`, `trade_order`, `build_website`, `download_app`, `science`.
6. FileProvider configuration and APK-install permission.
7. Data-sync foreground-service permission.
8. Version bump to 1.2.0.

Safety:
- Live trading is disabled unless explicitly enabled in the trading adapter.
- Default trading action is paper-only.
- Risk limits are checked before orders.
- No claim of guaranteed returns or zero loss is made.
- APK installation is delegated to Android and cannot be silent.
- HTTPS is required for APK downloads.

Validation note:
This source tree was statically inspected and packaged. A full Android build/device test could not be performed in this environment because an Android SDK/Gradle toolchain is not installed here. Build and device testing should therefore be performed in Android Studio/CI before a production release.
