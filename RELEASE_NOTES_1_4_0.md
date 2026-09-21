# JARVIS 1.4.0 — Groww + Angel One Live Trading

- Added official Groww Trading API live order adapter.
- Added Angel One SmartAPI login, TOTP, LTP, RMS and live order adapter.
- Added broker selection and broker-specific order fields.
- Added explicit live gate and emergency kill switch.
- Added daily drawdown, max-order-notional, max-position and daily-order limits.
- Added encrypted credential storage.
- Added live trade action schema for the JARVIS agent.
- Default remains non-live until the explicit live gate is enabled.

Live trading depends on valid broker credentials, account permissions, instrument identifiers and the broker's network/API compliance requirements. No profit or zero-loss guarantee is possible.
