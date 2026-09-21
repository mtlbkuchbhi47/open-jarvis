# Open Jarvis 1.2.0 — Expanded Capabilities

## Trading / market analysis
- Alpaca Trading API adapter with paper/live endpoint separation.
- Historical daily bars + latest trade retrieval.
- Deterministic SMA20/SMA50/RSI signal analysis.
- Guarded order submission with:
  - paper-first default
  - explicit live-trading gate
  - daily drawdown limit
  - maximum order notional
  - maximum position percentage
  - daily order count limit
  - bracket stop-loss / take-profit when enabled
- API keys are stored in EncryptedSharedPreferences.

### Important
No trading system can guarantee profit or zero loss. The risk controls reduce defined classes of risk; they cannot eliminate market gaps, slippage, outages, rejected orders, broker issues, or model errors.

The included Alpaca integration is a concrete example. Broker availability, products and permissions vary by jurisdiction/account. Paper trading is supported by Alpaca as a real-time simulation; paper results can differ from live execution.

## App downloader
- HTTPS-only APK download.
- Downloaded APK is passed to Android's package installer using FileProvider.
- Jarvis never silently installs an APK.
- Android user approval remains required.

## Website builder
- LLM-generated static sites.
- Produces `index.html`, `styles.css`, `script.js`, and README when the model follows the contract.
- Exports a ZIP into the app's private storage.
- Sanitizes relative paths and forbids `..` traversal.
- Does not put server secrets into frontend code.

## Science
- Offline deterministic helpers for common physics/chemistry calculations:
  force, kinetic energy, gravitational potential energy, Ohm's law, electrical power, wavelength/frequency, ideal gas pressure, pH, Celsius/Fahrenheit.
- Natural-language science questions can still be routed through Jarvis's LLM for explanations, while deterministic calculations should be used when exact formulas are needed.

## Existing functionality preserved
The change is additive: accessibility automation, OCR, voice, MCP, memory, local model support, app intelligence, notification access, builder mode and existing skills are retained.
