package com.openjarvis.finance

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Broker execution engine for JARVIS.
 *
 * Supports real order APIs for Groww and Angel One SmartAPI. Live mode is
 * disabled by default and every order passes the local risk gate first.
 * No trading system can guarantee profit or zero loss.
 */
class TradingEngine(private val context: Context) {
    enum class Broker { GROWW, ANGEL_ONE, ALPACA }

    data class RiskConfig(
        val maxDailyLossPct: Double = 2.0,
        val maxPositionPct: Double = 10.0,
        val maxOrderNotional: Double = 5000.0,
        val maxOrdersPerDay: Int = 20,
        val requireStopLoss: Boolean = true,
        val stopLossPct: Double = 1.5,
        val takeProfitPct: Double = 3.0,
        val paperOnly: Boolean = true
    )

    data class OrderRequest(
        val symbol: String,
        val side: String,
        val qty: Int,
        val limitPrice: Double? = null,
        val exchange: String = "NSE",
        val segment: String = "CASH",
        val product: String = "CNC",
        val orderType: String = if (limitPrice == null) "MARKET" else "LIMIT",
        val symbolToken: String? = null,
        val duration: String = "DAY"
    )

    data class Quote(val symbol: String, val price: Double, val timestampMs: Long)
    data class Analysis(val symbol: String, val signal: String, val confidence: Double, val reasons: List<String>, val stopLoss: Double?, val takeProfit: Double?)

    private val client = OkHttpClient()
    private val prefs = EncryptedSharedPreferences.create(
        "jarvis_trading_prefs", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun configureGroww(accessToken: String, live: Boolean = false) {
        require(accessToken.isNotBlank()) { "Groww access token is required" }
        prefs.edit().putString("groww_token", accessToken).putBoolean("groww_live", live).apply()
        if (live) prefs.edit().putBoolean("broker_live", false).apply()
    }

    fun configureAngelOne(clientCode: String, pin: String, apiKey: String, totpSecret: String? = null, live: Boolean = false) {
        require(clientCode.isNotBlank() && pin.isNotBlank() && apiKey.isNotBlank()) { "Angel One client code, PIN and API key are required" }
        prefs.edit()
            .putString("angel_client_code", clientCode)
            .putString("angel_pin", pin)
            .putString("angel_api_key", apiKey)
            .apply()
        if (!totpSecret.isNullOrBlank()) prefs.edit().putString("angel_totp_secret", totpSecret).apply()
        prefs.edit().putBoolean("angel_live", live).apply()
        if (live) prefs.edit().putBoolean("broker_live", false).apply()
    }

    /** Optional legacy compatibility. */
    fun configureAlpaca(key: String, secret: String, paper: Boolean = true) {
        prefs.edit().putString("alpaca_key", key).putString("alpaca_secret", secret).putBoolean("alpaca_paper", paper).apply()
    }

    fun setBroker(broker: Broker) = prefs.edit().putString("broker", broker.name).apply()
    fun currentBroker(): Broker = runCatching { Broker.valueOf(prefs.getString("broker", Broker.GROWW.name)!!) }.getOrDefault(Broker.GROWW)

    /** Explicit second gate: credentials/config alone never enables live orders. */
    fun setLiveEnabled(enabled: Boolean) = prefs.edit().putBoolean("live_enabled", enabled).apply()
    fun isLiveEnabled(): Boolean = prefs.getBoolean("live_enabled", false)

    fun setKillSwitch(active: Boolean) = prefs.edit().putBoolean("kill_switch", active).apply()
    fun isKillSwitchActive(): Boolean = prefs.getBoolean("kill_switch", false)

    fun setAngelNetworkConfig(localIp: String?, publicIp: String?, macAddress: String?) {
        prefs.edit().apply {
            if (!localIp.isNullOrBlank()) putString("angel_local_ip", localIp)
            if (!publicIp.isNullOrBlank()) putString("angel_public_ip", publicIp)
            if (!macAddress.isNullOrBlank()) putString("angel_mac", macAddress)
        }.apply()
    }

    fun setRiskDefaults(maxDailyLossPct: Double, maxOrderNotional: Double, maxOrdersPerDay: Int) {
        require(maxDailyLossPct > 0 && maxOrderNotional > 0 && maxOrdersPerDay > 0)
        prefs.edit().putFloat("max_daily_loss_pct", maxDailyLossPct.toFloat())
            .putFloat("max_order_notional", maxOrderNotional.toFloat())
            .putInt("max_orders_day", maxOrdersPerDay).apply()
    }

    private fun configuredRisk(input: RiskConfig): RiskConfig = input.copy(
        maxDailyLossPct = prefs.getFloat("max_daily_loss_pct", input.maxDailyLossPct.toFloat()).toDouble(),
        maxOrderNotional = prefs.getFloat("max_order_notional", input.maxOrderNotional.toFloat()).toDouble(),
        maxOrdersPerDay = prefs.getInt("max_orders_day", input.maxOrdersPerDay)
    )

    private fun growwRequest(url: String): Request.Builder = Request.Builder().url(url)
        .addHeader("Authorization", "Bearer ${prefs.getString("groww_token", "")}")
        .addHeader("Accept", "application/json")
        .addHeader("X-API-VERSION", "1.0")

    private fun angelRequest(url: String): Request.Builder = Request.Builder().url(url)
        .addHeader("Authorization", "Bearer ${prefs.getString("angel_jwt", "")}")
        .addHeader("Accept", "application/json")
        .addHeader("Content-Type", "application/json")
        .addHeader("X-UserType", "USER")
        .addHeader("X-SourceID", "WEB")
        .addHeader("X-PrivateKey", prefs.getString("angel_api_key", "") ?: "")
        .addHeader("X-ClientLocalIP", prefs.getString("angel_local_ip", "0.0.0.0") ?: "0.0.0.0")
        .addHeader("X-ClientPublicIP", prefs.getString("angel_public_ip", "0.0.0.0") ?: "0.0.0.0")
        .addHeader("X-MACAddress", prefs.getString("angel_mac", "00:00:00:00:00:00") ?: "00:00:00:00:00:00")

    suspend fun loginAngelOne(totp: String? = null): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val code = totp?.takeIf { it.isNotBlank() } ?: prefs.getString("angel_totp_secret", "")?.let { generateTotp(it) }
                ?: error("Angel One TOTP is required")
            val body = JSONObject().apply {
                put("clientcode", prefs.getString("angel_client_code", ""))
                put("password", prefs.getString("angel_pin", ""))
                put("totp", code)
            }.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("https://apiconnect.angelone.in/rest/auth/angelbroking/user/v1/loginByPassword")
                .addHeader("Content-Type", "application/json").addHeader("Accept", "application/json")
                .addHeader("X-UserType", "USER").addHeader("X-SourceID", "WEB")
                .addHeader("X-PrivateKey", prefs.getString("angel_api_key", "") ?: "")
                .post(body).build()
            val response = client.newCall(request).execute()
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Angel One login failed: HTTP ${response.code}: $text")
            val json = JSONObject(text)
            if (!json.optBoolean("status", false)) error("Angel One login rejected: ${json.optString("message")}")
            val data = json.optJSONObject("data") ?: error("Angel One login returned no session data")
            prefs.edit().putString("angel_jwt", data.optString("jwtToken"))
                .putString("angel_refresh", data.optString("refreshToken"))
                .putString("angel_feed", data.optString("feedToken")).apply()
            json
        }
    }

    suspend fun brokerAvailable(): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            when (currentBroker()) {
                Broker.GROWW -> {
                    val r = client.newCall(growwRequest("https://api.groww.in/v1/user/detail").get().build()).execute()
                    val text = r.body?.string().orEmpty(); if (!r.isSuccessful) error("Groww profile failed: HTTP ${r.code}: $text"); JSONObject(text)
                }
                Broker.ANGEL_ONE -> {
                    ensureAngelSession()
                    val r = client.newCall(angelRequest("https://apiconnect.angelone.in/rest/secure/angelbroking/user/v1/getProfile").get().build()).execute()
                    val text = r.body?.string().orEmpty(); if (!r.isSuccessful) error("Angel One profile failed: HTTP ${r.code}: $text"); JSONObject(text)
                }
                Broker.ALPACA -> account().getOrThrow()
            }
        }
    }

    private suspend fun ensureAngelSession() {
        if (prefs.getString("angel_jwt", "").isNullOrBlank()) loginAngelOne().getOrThrow()
    }

    suspend fun account(): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (prefs.getBoolean("alpaca_paper", true)) "https://paper-api.alpaca.markets" else "https://api.alpaca.markets"
            val response = client.newCall(Request.Builder().url("$url/v2/account")
                .addHeader("APCA-API-KEY-ID", prefs.getString("alpaca_key", "") ?: "")
                .addHeader("APCA-API-SECRET-KEY", prefs.getString("alpaca_secret", "") ?: "").get().build()).execute()
            if (!response.isSuccessful) error("Alpaca account failed: HTTP ${response.code}")
            JSONObject(response.body?.string().orEmpty())
        }
    }

    private suspend fun availableEquity(): Double {
        return when (currentBroker()) {
            Broker.GROWW -> {
                val r = client.newCall(growwRequest("https://api.groww.in/v1/margins/detail/user").get().build()).execute()
                val j = JSONObject(r.body?.string().orEmpty()); if (!r.isSuccessful || j.optString("status") != "SUCCESS") error("Groww margin request failed")
                j.optJSONObject("payload")?.optDouble("clear_cash", 0.0) ?: 0.0
            }
            Broker.ANGEL_ONE -> {
                ensureAngelSession()
                val r = client.newCall(angelRequest("https://apiconnect.angelone.in/rest/secure/angelbroking/user/v1/getRMS").get().build()).execute()
                val j = JSONObject(r.body?.string().orEmpty()); if (!r.isSuccessful || !j.optBoolean("status", false)) error("Angel One RMS request failed")
                j.optJSONObject("data")?.optString("availablecash", "0")?.toDoubleOrNull() ?: 0.0
            }
            Broker.ALPACA -> account().getOrThrow().optDouble("equity", 0.0)
        }
    }

    suspend fun latestQuote(symbol: String, exchange: String = "NSE", segment: String = "CASH", symbolToken: String? = null): Result<Quote> = withContext(Dispatchers.IO) {
        runCatching {
            when (currentBroker()) {
                Broker.GROWW -> {
                    val url = "https://api.groww.in/v1/live-data/quote?exchange=$exchange&segment=$segment&trading_symbol=${java.net.URLEncoder.encode(symbol.uppercase(), "UTF-8")}"
                    val r = client.newCall(growwRequest(url).get().build()).execute(); val j = JSONObject(r.body?.string().orEmpty())
                    if (!r.isSuccessful || j.optString("status") != "SUCCESS") error("Groww quote failed: HTTP ${r.code}")
                    val p = j.optJSONObject("payload")?.optDouble("ltp", Double.NaN) ?: Double.NaN
                    if (!p.isFinite()) error("Groww quote has no LTP")
                    Quote(symbol.uppercase(), p, System.currentTimeMillis())
                }
                Broker.ANGEL_ONE -> {
                    ensureAngelSession(); val token = symbolToken ?: error("Angel One requires symbolToken")
                    val body = JSONObject().put("exchange", exchange).put("tradingsymbol", symbol).put("symboltoken", token)
                        .toString().toRequestBody("application/json".toMediaType())
                    val r = client.newCall(angelRequest("https://apiconnect.angelone.in/rest/secure/angelbroking/order/v1/getLtpData").post(body).build()).execute()
                    val j = JSONObject(r.body?.string().orEmpty()); if (!r.isSuccessful || !j.optBoolean("status", false)) error("Angel One quote failed: HTTP ${r.code}")
                    val p = j.optJSONObject("data")?.optString("ltp")?.toDoubleOrNull() ?: error("Angel One quote has no LTP")
                    Quote(symbol.uppercase(), p, System.currentTimeMillis())
                }
                Broker.ALPACA -> error("Use Alpaca symbols with the legacy path")
            }
        }
    }

    suspend fun submitGuardedOrder(request: OrderRequest, riskInput: RiskConfig = RiskConfig()): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val risk = configuredRisk(riskInput)
            val side = request.side.lowercase()
            require(side == "buy" || side == "sell") { "side must be buy or sell" }
            require(request.qty > 0) { "quantity must be positive" }
            require(!isKillSwitchActive()) { "Emergency kill switch is active" }
            require(isLiveEnabled()) { "Live trading is disabled. Enable the explicit live gate first." }
            require(!risk.paperOnly) { "Risk config is paper-only" }

            val equity = availableEquity()
            require(equity > 0) { "Invalid available equity/margin" }
            val referencePrice = request.limitPrice ?: latestQuote(request.symbol, request.exchange, request.segment, request.symbolToken).getOrThrow().price
            val notional = request.qty * referencePrice
            require(notional <= risk.maxOrderNotional) { "Order exceeds maxOrderNotional" }
            require(notional / equity * 100.0 <= risk.maxPositionPct) { "Order exceeds maxPositionPct" }

            val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val storedDay = prefs.getString("risk_day", "")
            if (storedDay != day) prefs.edit().putString("risk_day", day).putFloat("day_start_equity", equity.toFloat()).putInt("orders_today", 0).apply()
            val dayStart = prefs.getFloat("day_start_equity", equity.toFloat()).toDouble()
            val dailyDrawdownPct = max(0.0, (dayStart - equity) / dayStart * 100.0)
            require(dailyDrawdownPct < risk.maxDailyLossPct) { "Daily loss limit reached (${String.format("%.2f", dailyDrawdownPct)}%)" }
            val ordersToday = prefs.getInt("orders_today", 0)
            require(ordersToday < risk.maxOrdersPerDay) { "Daily order limit reached" }

            val result = when (currentBroker()) {
                Broker.GROWW -> placeGroww(request)
                Broker.ANGEL_ONE -> placeAngel(request)
                Broker.ALPACA -> error("Alpaca live order adapter is intentionally not exposed by the India live-trading path")
            }
            prefs.edit().putInt("orders_today", ordersToday + 1).apply()
            result
        }
    }

    suspend fun submitGuardedOrder(symbol: String, side: String, qty: Double, limitPrice: Double?, risk: RiskConfig = RiskConfig()): Result<JSONObject> =
        submitGuardedOrder(OrderRequest(symbol, side, qty.toInt(), limitPrice), risk)

    private suspend fun placeGroww(r: OrderRequest): JSONObject {
        val tx = if (r.side.equals("buy", true)) "BUY" else "SELL"
        val body = JSONObject().apply {
            put("trading_symbol", r.symbol)
            put("quantity", r.qty)
            put("validity", r.duration)
            put("exchange", r.exchange)
            put("segment", r.segment)
            put("product", r.product)
            put("order_type", r.orderType)
            put("transaction_type", tx)
            if (r.limitPrice != null) put("price", r.limitPrice)
            put("order_reference_id", uniqueReference())
        }.toString().toRequestBody("application/json".toMediaType())
        val response = client.newCall(growwRequest("https://api.groww.in/v1/order/create").post(body).build()).execute()
        val text = response.body?.string().orEmpty(); if (!response.isSuccessful) error("Groww order rejected: HTTP ${response.code}: $text")
        val json = JSONObject(text); if (json.optString("status") != "SUCCESS") error("Groww order rejected: $text")
        return json
    }

    private suspend fun placeAngel(r: OrderRequest): JSONObject {
        ensureAngelSession()
        val token = r.symbolToken ?: error("Angel One order requires symbolToken")
        val body = JSONObject().apply {
            put("variety", "NORMAL")
            put("tradingsymbol", r.symbol)
            put("symboltoken", token)
            put("transactiontype", if (r.side.equals("buy", true)) "BUY" else "SELL")
            put("exchange", r.exchange)
            put("ordertype", r.orderType)
            put("producttype", when (r.product.uppercase()) { "CNC" -> "DELIVERY"; "MIS" -> "INTRADAY"; else -> r.product.uppercase() })
            put("duration", r.duration)
            put("price", r.limitPrice ?: "0")
            put("squareoff", "0")
            put("stoploss", "0")
            put("quantity", r.qty.toString())
            put("scripconsent", "yes")
            put("ordertag", "JARVIS")
        }.toString().toRequestBody("application/json".toMediaType())
        val response = client.newCall(angelRequest("https://apiconnect.angelone.in/rest/secure/angelbroking/order/v1/placeOrder").post(body).build()).execute()
        val text = response.body?.string().orEmpty(); if (!response.isSuccessful) error("Angel One order rejected: HTTP ${response.code}: $text")
        val json = JSONObject(text); if (!json.optBoolean("status", false)) error("Angel One order rejected: $text")
        return json
    }

    private fun uniqueReference(): String = "JV${System.currentTimeMillis().toString().takeLast(14)}"

    private fun generateTotp(secret: String): String {
        val normalized = secret.replace(" ", "").uppercase()
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = ByteArray(normalized.length * 5 / 8)
        var buffer = 0; var bits = 0; var idx = 0
        for (c in normalized) {
            val v = alphabet.indexOf(c); if (v < 0) error("Invalid Base32 TOTP secret")
            buffer = (buffer shl 5) or v; bits += 5
            if (bits >= 8) { bits -= 8; out[idx++] = (buffer shr bits).toByte(); buffer = buffer and ((1 shl bits) - 1) }
        }
        val counter = ByteBuffer.allocate(8).putLong(System.currentTimeMillis() / 1000L / 30L).array()
        val mac = Mac.getInstance("HmacSHA1"); mac.init(SecretKeySpec(out, "HmacSHA1")); val hash = mac.doFinal(counter)
        val offset = hash.last().toInt() and 0x0f
        val code = ((hash[offset].toInt() and 0x7f) shl 24) or ((hash[offset + 1].toInt() and 0xff) shl 16) or ((hash[offset + 2].toInt() and 0xff) shl 8) or (hash[offset + 3].toInt() and 0xff)
        return (code % 1_000_000).toString().padStart(6, '0')
    }

    fun analyze(symbol: String, closes: List<Double>, current: Double = closes.lastOrNull() ?: 0.0): Analysis {
        require(closes.size >= 20) { "At least 20 closing prices are required" }
        val sma20 = closes.takeLast(20).average(); val sma50 = if (closes.size >= 50) closes.takeLast(50).average() else sma20
        val gains = mutableListOf<Double>(); val losses = mutableListOf<Double>()
        closes.zipWithNext().takeLast(14).forEach { (a,b) -> if (b >= a) gains += b-a else losses += a-b }
        val avgGain = gains.average().coerceAtLeast(1e-9); val avgLoss = losses.average().coerceAtLeast(1e-9)
        val rsi = 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        val reasons = mutableListOf<String>(); var score = 0
        if (current > sma20) { score++; reasons += "price above SMA20" } else { score--; reasons += "price below SMA20" }
        if (sma20 > sma50) { score++; reasons += "SMA20 above trend baseline" } else { score--; reasons += "SMA20 below trend baseline" }
        if (rsi < 30) { score++; reasons += "RSI indicates oversold" }; if (rsi > 70) { score--; reasons += "RSI indicates overbought" }
        val signal = when { score >= 2 -> "BUY"; score <= -2 -> "SELL"; else -> "HOLD" }
        val confidence = min(0.95, 0.5 + abs(score) * 0.12)
        val sl = if (signal == "BUY") current * .985 else if (signal == "SELL") current * 1.015 else null
        val tp = if (signal == "BUY") current * 1.03 else if (signal == "SELL") current * .97 else null
        return Analysis(symbol.uppercase(), signal, confidence, reasons, sl, tp)
    }

    suspend fun historicalCloses(symbol: String, limit: Int = 100): Result<List<Double>> = Result.failure(UnsupportedOperationException("Historical candles are broker-specific; use the selected broker's historical-data adapter"))
}
