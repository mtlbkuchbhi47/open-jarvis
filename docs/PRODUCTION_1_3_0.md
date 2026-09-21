# Open Jarvis 1.3.0 Production Hardening

## Scope
- Trading remains paper-first and risk-gated. No implementation can guarantee zero losses.
- Added deterministic local backtesting; historical backtests are not future guarantees.
- Website builder now rejects path traversal instead of rewriting suspicious paths.
- Expanded offline science calculations.
- Added unit tests for deterministic finance/science logic.
- Release remains R8/minified; release builds should be tested on target Android versions before distribution.

## Performance policy
- Keep network/database/model work off the UI thread.
- Prefer lazy lists with stable keys.
- Avoid unnecessary recompositions and continuous animations in data-heavy screens.
- Use Android Studio Layout Inspector, profiling and release builds to verify jank/ANR behavior.

## Safety policy
- Trading: paper by default, explicit live gate, order limits, daily loss limit and protective exits.
- Downloads/installers: HTTPS and user-confirmed Android installation; never silently install APKs.
- Generated websites: path traversal blocked and frontend secrets prohibited.
