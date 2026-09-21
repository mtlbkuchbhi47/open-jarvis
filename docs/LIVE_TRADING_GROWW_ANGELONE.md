# JARVIS Live Trading: Groww + Angel One

JARVIS now contains broker adapters for the official Groww Trading API and Angel One SmartAPI order endpoints.

## Important
- Live trading is OFF by default.
- The app requires the explicit `setLiveEnabled(true)` gate and `paperOnly=false` internally before a real order can be submitted.
- The emergency kill switch blocks all live orders.
- Risk limits are checked before every order.
- JARVIS does not and cannot guarantee profit or zero loss.

## Groww
Configure a current Groww access token using `configureGroww(token, live = true)` and select `Broker.GROWW`.
Groww's API supports order placement, order status, trades and margin APIs. The access token expires daily and Groww's API currently supports CASH and FNO trading.

## Angel One
Configure client code, PIN, API key and optionally the TOTP secret using `configureAngelOne(...)`, then select `Broker.ANGEL_ONE`.
The app can generate the 6-digit TOTP from a Base32 secret or accept a current TOTP in `loginAngelOne(totp)`.
For orders, Angel One requires the symbol token in addition to the trading symbol.

## Static IP / network compliance
As of April 1, 2026, Angel One requires API order requests to originate from a registered/whitelisted static IP. Groww also documents static-IP authorization for API order placement. A normal mobile-data connection can change its public IP, so live API execution should use a network/VPN/server arrangement whose outbound IP is the whitelisted static IP.

JARVIS cannot spoof the source IP with an HTTP header. `setAngelNetworkConfig()` only supplies the client metadata headers required by Angel One; the actual network source IP still has to match the broker's whitelist.

## Example action
```json
{
  "action":"trade_order",
  "broker":"GROWW",
  "symbol":"RELIANCE",
  "side":"buy",
  "qty":1,
  "exchange":"NSE",
  "segment":"CASH",
  "product":"CNC",
  "order_type":"MARKET"
}
```
For Angel One add `"symbol_token":"<broker symbol token>"`.
