package com.openjarvis.agent

import org.json.JSONArray
import org.json.JSONObject

data class Action(
    val action: String,
    val packageName: String? = null,
    val label: String? = null,
    val text: String? = null,
    val value: String? = null,
    val hint: String? = null,
    val direction: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val distance: String? = null,
    val timeoutMs: Long = 3000L,
    val message: String? = null,
    val prompt: String? = null,
    val outputKey: String? = null,
    val description: String? = null,
    val argsJson: String? = null,
    val sender: String? = null,
    val symbol: String? = null,
    val side: String? = null,
    val qty: Double? = null,
    val limitPrice: Double? = null,
    val exchange: String? = null,
    val segment: String? = null,
    val product: String? = null,
    val orderType: String? = null,
    val symbolToken: String? = null,
    val broker: String? = null,
    val url: String? = null,
    val siteName: String? = null
) {
    companion object {
        const val OPEN_APP = "open_app"
        const val TAP = "tap"
        const val TAP_COORDS = "tap_coords"
        const val TYPE = "type"
        const val CLEAR_TYPE = "clear_type"
        const val LONG_PRESS = "long_press"
        const val SWIPE = "swipe"
        const val SCROLL = "scroll"
        const val PRESS_BACK = "press_back"
        const val PRESS_HOME = "press_home"
        const val PRESS_RECENTS = "press_recents"
        const val WAIT_FOR = "wait_for"
        const val SCREENSHOT = "screenshot"
        const val READ_SCREEN = "read_screen"
        const val AI_PROMPT = "ai_prompt"
        const val EXTRACT_TEXT = "extract_text"
        const val MCP_CALL = "mcp_call"
        const val REPLY_NOTIFICATION = "reply_notification"
        const val READ_CLIPBOARD = "read_clipboard"
        const val WRITE_CLIPBOARD = "write_clipboard"
        const val HIGHLIGHT_ELEMENT = "highlight_element"
        const val ERROR = "error"
        const val TRADE_ANALYZE = "trade_analyze"
        const val TRADE_ORDER = "trade_order"
        const val BUILD_WEBSITE = "build_website"
        const val DOWNLOAD_APP = "download_app"
        const val SCIENCE = "science"
    }
}

sealed class AgentState {
    object Idle : AgentState()
    data class Running(val step: String) : AgentState()
    data class Done(val result: String) : AgentState()
    data class Error(val message: String) : AgentState()
}

object ActionJsonParser {
    fun parse(json: String): List<Action>? {
        return try {
            val clean = json
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            
            val jsonArray = if (clean.startsWith("{")) JSONArray().put(JSONObject(clean)) else JSONArray(clean)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                Action(
                    action = obj.getString("action"),
                    packageName = obj.optString("package", null).takeIf { it.isNotBlank() },
                    label = obj.optString("label", null).takeIf { it.isNotBlank() },
                    text = obj.optString("text", null).takeIf { it.isNotBlank() },
                    value = obj.optString("value", null).takeIf { it.isNotBlank() },
                    hint = obj.optString("hint", null).takeIf { it.isNotBlank() },
                    direction = obj.optString("direction", null).takeIf { it.isNotBlank() },
                    x = if (obj.has("x")) obj.getInt("x") else null,
                    y = if (obj.has("y")) obj.getInt("y") else null,
                    distance = obj.optString("distance", null).takeIf { it.isNotBlank() },
                    timeoutMs = obj.optLong("timeout_ms", 3000L),
                    message = obj.optString("message", null).takeIf { it.isNotBlank() },
                    prompt = obj.optString("prompt", null).takeIf { it.isNotBlank() },
                    outputKey = obj.optString("outputKey", null).takeIf { it.isNotBlank() },
                    description = obj.optString("description", null).takeIf { it.isNotBlank() },
                    argsJson = obj.optJSONObject("args")?.toString() ?: obj.optString("args", null).takeIf { it.isNotBlank() },
                    sender = obj.optString("sender", null).takeIf { it.isNotBlank() },
                    symbol = obj.optString("symbol", null).takeIf { it.isNotBlank() },
                    side = obj.optString("side", null).takeIf { it.isNotBlank() },
                    qty = if (obj.has("qty")) obj.optDouble("qty") else null,
                    limitPrice = if (obj.has("limit_price")) obj.optDouble("limit_price") else null,
                    exchange = obj.optString("exchange", null).takeIf { it.isNotBlank() },
                    segment = obj.optString("segment", null).takeIf { it.isNotBlank() },
                    product = obj.optString("product", null).takeIf { it.isNotBlank() },
                    orderType = obj.optString("order_type", null).takeIf { it.isNotBlank() },
                    symbolToken = obj.optString("symbol_token", null).takeIf { it.isNotBlank() },
                    broker = obj.optString("broker", null).takeIf { it.isNotBlank() },
                    url = obj.optString("url", null).takeIf { it.isNotBlank() },
                    siteName = obj.optString("siteName", null).takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}