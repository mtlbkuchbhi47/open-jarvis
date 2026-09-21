package com.openjarvis.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.openjarvis.accessibility.JarvisAccessibilityService
import com.openjarvis.accessibility.ScreenReader
import com.openjarvis.graphify.AnalysisEngine
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.intelligence.AIAppInteractor
import com.openjarvis.intelligence.AIApps
import com.openjarvis.intelligence.AppAnalyzer
import com.openjarvis.intelligence.TaskRouter
import com.openjarvis.intelligence.TaskWorkingMemory
import com.openjarvis.intelligence.ClipboardIntelligence
import com.openjarvis.intelligence.JarvisNotificationListener
import com.openjarvis.mcp.MCPManager
import org.json.JSONObject
import com.openjarvis.llm.UniversalAdapter
import com.openjarvis.vision.VisionModule
import com.openjarvis.finance.TradingEngine
import com.openjarvis.web.WebsiteBuilder
import com.openjarvis.apps.AppInstaller
import com.openjarvis.science.ScienceEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentCore(private val context: Context) {

    private val graphifyRepo = GraphifyRepository(context)
    private val analysisEngine = AnalysisEngine(context)
    private val universalAdapter = UniversalAdapter(context)
    private val screenReader = ScreenReader(context)
    private val visionModule = VisionModule.getInstance(context)
    private val taskRouter = TaskRouter(context)
    private val appAnalyzer = AppAnalyzer(context)
    private val aiAppInteractor = AIAppInteractor(context)
    private val clipboard = ClipboardIntelligence(context)
    private val mcpManager = MCPManager(context)
    private val tradingEngine = TradingEngine(context)
    private val websiteBuilder = WebsiteBuilder(context)
    private val appInstaller = AppInstaller(context)
    private var workingMemory = TaskWorkingMemory()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val taskMutex = Mutex()

    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state

    private val systemPrompt = """
You are Open Jarvis — an Android device control AI agent.
The user gives you a cleanCommand in natural language.
You must respond with ONLY a valid JSON array of actions. No explanation. No markdown fences. No preamble. Pure JSON array only.

AVAILABLE ACTIONS:
open_app     → {"action":"open_app","package":"com.package","label":"AppName"}
tap          → {"action":"tap","text":"Button text on screen"}
tap_coords   → {"action":"tap_coords","x":540,"y":960}
long_press   → {"action":"long_press","text":"Element text"}
type         → {"action":"type","value":"text to type"}
clear_type   → {"action":"clear_type","value":"clears field then types"}
swipe        → {"action":"swipe","direction":"up|down|left|right","distance":"short|medium|long"}
scroll       → {"action":"scroll","direction":"up|down"}
press_back   → {"action":"press_back"}
press_home   → {"action":"press_home"}
press_recents → {"action":"press_recents"}
wait_for     → {"action":"wait_for","text":"expected text","timeout_ms":3000}
screenshot   → {"action":"screenshot"}
read_screen  → {"action":"read_screen"}
ai_prompt    → {"action":"ai_prompt","package":"com.openai.chatgpt","prompt":"{prompt}","outputKey":"result"}
extract_text → {"action":"extract_text","outputKey":"page_text"}
read_clipboard → {"action":"read_clipboard","outputKey":"clipboard"}
write_clipboard → {"action":"write_clipboard","value":"text"}
reply_notification → {"action":"reply_notification","package":"package.name","sender":"Person","value":"reply"}
mcp_call → {"action":"mcp_call","package":"server_id","text":"tool_name","args":{},"outputKey":"result"}
trade_analyze → {"action":"trade_analyze","symbol":"AAPL","value":"comma-separated closing prices","outputKey":"trade_analysis"}
trade_order → {"action":"trade_order","symbol":"AAPL","side":"buy|sell","qty":1,"limit_price":100}
build_website → {"action":"build_website","siteName":"my-site","prompt":"site requirements"}
download_app → {"action":"download_app","url":"https://.../app.apk"}
science → {"action":"science","value":"mass 2 kg, velocity 5 m/s","outputKey":"science_result"}

CURRENT SCREEN CONTENT: {SCREEN_OCR}

APP SELECTION REASONING: {APP_REASONING}

INSTALLED AI APPS: {AI_APPS}

RECENT MEMORY CONTEXT: {GRAPHIFY_CONTEXT}

MCP TOOLS AVAILABLE: {MCP_TOOLS}

RULES:
- Always start complex tasks with open_app
- Add wait_for after open_app to confirm app loaded
- If screen content is empty or unclear, add read_screen as first action
- Never assume UI state — always verify with wait_for
- Keep action arrays short: 2-8 steps per task
- Use ai_prompt to delegate complex reasoning to installed AI apps
- Use mcp_call only when a configured MCP server/tool is present
- Use clipboard actions for copy/paste workflows
- Verify risky sends/replies/trades with the user-facing confirmation layer when available
- Never promise profit or zero loss. Trading must obey risk limits; paper trading is the default.
- Never download/install an APK from a non-HTTPS URL; installation must be handed to Android for explicit user consent.
- Website generation must not embed secrets in frontend code.
- If a task is impossible to do safely, return: [{"action":"error","message":"reason"}]
""".trimIndent()

fun executeTask(cleanCommand: String): Job {
        workingMemory = TaskWorkingMemory()
        
        return scope.launch {
            taskMutex.withLock {
                try {
                    val sanitized = PromptSanitizer.sanitize(cleanCommand)
                    when (sanitized) {
                        is PromptSanitizer.SanitizeResult.Rejected -> {
                            _state.value = AgentState.Error(sanitized.reason)
                            return@withLock
                        }
                        is PromptSanitizer.SanitizeResult.Suspicious -> {
                            _state.value = AgentState.Running("analyzing...")
                        }
                        is PromptSanitizer.SanitizeResult.Clean -> { }
                    }
                    
                    val cleanCommand = when (sanitized) {
                        is PromptSanitizer.SanitizeResult.Suspicious -> sanitized.sanitized
                        is PromptSanitizer.SanitizeResult.Clean -> sanitized.text
                        else -> cleanCommand
                    }
                    
                    _state.value = AgentState.Running("analyzing task...")
                
                val plan = taskRouter.analyze(cleanCommand)
                
                _state.value = AgentState.Running("reading screen...")
                
                val screenText = withContext(Dispatchers.IO) {
                    screenReader.extractAllText()
                }
                
                _state.value = AgentState.Running("getting context...")
                
                val memoryContext = graphifyRepo.buildMemoryContext(cleanCommand)
                
                val fullSystem = systemPrompt
                    .replace("{SCREEN_OCR}", screenText.take(2000))
                    .replace("{APP_REASONING}", plan.reasoning)
                    .replace("{AI_APPS}", getInstalledAIApps())
                    .replace("{GRAPHIFY_CONTEXT}", if (memoryContext.isBlank()) "No recent tasks" else memoryContext)
                    .replace("{MCP_TOOLS}", try { mcpManager.buildMCPToolsPrompt(mcpManager.getAllTools()) } catch (_: Exception) { "No MCP tools available" })
                
                _state.value = AgentState.Running("thinking...")
                
                val startTime = System.currentTimeMillis()
                
                val result = universalAdapter.complete(fullSystem, cleanCommand)
                result.fold(
                    onSuccess = { rawJson ->
                        val latency = System.currentTimeMillis() - startTime
                        
                        val validation = LLMResponseValidator.validate(rawJson)
                        val actions = if (validation.isValid) {
                            validation.actions
                        } else {
                            val retry = universalAdapter.complete(
                                fullSystem,
                                "$cleanCommand\n\nRespond with JSON array ONLY. No other text."
                            )
                            retry.getOrNull()?.let { ActionJsonParser.parse(it) }
                        }
                        
                        if (actions == null) {
                            _state.value = AgentState.Error("Could not parse AI response")
                            graphifyRepo.logTask(cleanCommand, "failed: parse error", "", 0)
                            return@fold
                        }
                        
                        _state.value = AgentState.Running("executing ${actions.size} actions...")
                        
                        val executed = executeActions(actions)
                        if (!executed) {
                            graphifyRepo.logTask(
                                cleanCommand = cleanCommand,
                                result = "failed: action execution",
                                provider = universalAdapter.getProviderName(),
                                latencyMs = latency
                            )
                            return@fold
                        }
                        
                        graphifyRepo.logTask(
                            cleanCommand = cleanCommand,
                            result = "success",
                            provider = universalAdapter.getProviderName(),
                            latencyMs = latency
                        )
                        
                        analysisEngine.analyzeLastTask()
                        
                        _state.value = AgentState.Done("done in ${latency}ms")
                    },
                    onFailure = { error ->
                        val msg = when {
                            error.message?.contains("401") == true -> "Invalid API key"
                            error.message?.contains("429") == true -> "Rate limited — wait a moment"
                            error.message?.contains("timeout") == true -> "Request timed out"
                            error.message?.contains("Unable to resolve") == true -> "Network error — check connection"
                            else -> error.message ?: "Unknown error"
                        }
                        _state.value = AgentState.Error(msg)
                        graphifyRepo.logTask(cleanCommand, "failed: $msg", "", 0)
                    }
                )
            } catch (e: Exception) {
                _state.value = AgentState.Error(e.message ?: "Unknown error")
                graphifyRepo.logTask(cleanCommand, "failed: ${e.message}", "", 0)
            }
        }
    }

    suspend fun testConnection(): Result<Long> {
        return universalAdapter.testConnection()
    }
    
    fun getCurrentProviderName(): String {
        return universalAdapter.getProviderName()
    }
    
    fun getStateFlow(): StateFlow<AgentState> = state
    
    private fun getInstalledAIApps(): String {
        return AIApps.KNOWN_AI_APPS.keys.joinToString(", ")
    }
    
    suspend fun getAnalyzedAppCount(): Int = appAnalyzer.getAnalyzedCount()
    
    suspend fun getAIAppCount(): Int = appAnalyzer.getAICount()

    private suspend fun executeActions(actions: List<Action>): Boolean {
        val service = JarvisAccessibilityService.instance
            ?: run {
                _state.value = AgentState.Error("Accessibility service is not enabled")
                return false
            }

        for ((index, action) in actions.withIndex()) {
            _state.value = AgentState.Running("action ${index + 1}/${actions.size}: ${action.action}")
            val ok = try {
                when (action.action) {
                    Action.OPEN_APP -> {
                        val opened = if (action.packageName != null) service.openAppByPackage(action.packageName)
                        else action.label?.let { service.openAppByLabel(it) } == true
                        if (opened) delay(700)
                        opened
                    }
                    Action.TAP -> service.tapByText(action.text.orEmpty())
                    Action.TAP_COORDS -> service.tapAt(action.x ?: -1, action.y ?: -1)
                    Action.LONG_PRESS -> service.longPressByText(action.text.orEmpty())
                    Action.TYPE -> service.typeText(workingMemory.interpolate(action.value.orEmpty()))
                    Action.CLEAR_TYPE -> service.clearAndTypeText(workingMemory.interpolate(action.value.orEmpty()))
                    Action.SWIPE -> service.swipe(action.direction.orEmpty(), action.distance ?: "medium")
                    Action.SCROLL -> service.scroll(action.direction ?: "down")
                    Action.PRESS_BACK -> service.pressBack()
                    Action.PRESS_HOME -> service.pressHome()
                    Action.PRESS_RECENTS -> service.pressRecents()
                    Action.WAIT_FOR -> service.waitForText(action.text.orEmpty(), action.timeoutMs)
                    Action.READ_SCREEN -> {
                        val text = screenReader.extractAllText()
                        workingMemory.set(action.outputKey ?: "page_text", text)
                        text.isNotBlank()
                    }
                    Action.EXTRACT_TEXT -> {
                        val text = screenReader.extractAllText()
                        workingMemory.set(action.outputKey ?: "page_text", text)
                        text.isNotBlank()
                    }
                    Action.SCREENSHOT -> {
                        val bitmap = service.captureScreenshot()
                        if (bitmap != null) {
                            val text = visionModule.extractText(bitmap)
                            workingMemory.set(action.outputKey ?: "screenshot_text", text)
                            bitmap.recycle()
                            true
                        } else false
                    }
                    Action.READ_CLIPBOARD -> {
                        val text = clipboard.read().orEmpty()
                        workingMemory.set(action.outputKey ?: "clipboard", text)
                        true
                    }
                    Action.WRITE_CLIPBOARD -> {
                        clipboard.write(workingMemory.interpolate(action.value.orEmpty()))
                        true
                    }
                    Action.REPLY_NOTIFICATION -> {
                        val sender = action.sender ?: action.text
                        val pkg = action.packageName
                        if (sender != null && pkg != null) {
                            JarvisNotificationListener.instance?.replyToNotification(
                                sender, pkg, workingMemory.interpolate(action.value.orEmpty())
                            ) == true
                        } else false
                    }
                    Action.TRADE_ANALYZE -> {
                        val symbol = action.symbol
                        if (symbol == null) false else {
                            val supplied = action.value.orEmpty().split(",", " ", "\n", "\t").mapNotNull { it.toDoubleOrNull() }
                            val closes = if (supplied.size >= 20) supplied else tradingEngine.historicalCloses(symbol).getOrNull().orEmpty()
                            if (closes.size < 20) false else {
                                val analysis = tradingEngine.analyze(symbol, closes)
                                workingMemory.set(action.outputKey ?: "trade_analysis", analysis.toString())
                                true
                            }
                        }
                    }
                    Action.TRADE_ORDER -> {
                        val symbol = action.symbol
                        val side = action.side
                        val qty = action.qty
                        if (symbol == null || side == null || qty == null) false else {
                            // Live orders are intentionally gated inside TradingEngine. Default is paper-only.
                            action.broker?.let { broker -> runCatching { tradingEngine.setBroker(TradingEngine.Broker.valueOf(broker.uppercase().replace(" ", "_"))) } }
                            val order = TradingEngine.OrderRequest(
                                symbol = symbol, side = side, qty = qty.toInt(), limitPrice = action.limitPrice,
                                exchange = action.exchange ?: "NSE", segment = action.segment ?: "CASH",
                                product = action.product ?: "CNC", orderType = action.orderType ?: if (action.limitPrice == null) "MARKET" else "LIMIT",
                                symbolToken = action.symbolToken
                            )
                            val result = tradingEngine.submitGuardedOrder(order, TradingEngine.RiskConfig(paperOnly = false))
                            result.onSuccess { workingMemory.set(action.outputKey ?: "trade_order", it.toString()) }
                            result.isSuccess
                        }
                    }
                    Action.BUILD_WEBSITE -> {
                        val result = websiteBuilder.build(action.prompt ?: action.value.orEmpty(), action.siteName ?: "jarvis-site")
                        result.onSuccess { workingMemory.set(action.outputKey ?: "website_zip", it.absolutePath) }
                        result.isSuccess
                    }
                    Action.DOWNLOAD_APP -> {
                        val url = action.url
                        if (url == null) false else {
                            val file = appInstaller.downloadApk(url).getOrNull()
                            if (file == null) false else { appInstaller.requestInstall(file); true }
                        }
                    }
                    Action.SCIENCE -> {
                        val answer = runCatching { ScienceEngine.calculate(action.value.orEmpty()) }.getOrElse { "Science calculation error: ${it.message}" }
                        workingMemory.set(action.outputKey ?: "science_result", answer)
                        !answer.startsWith("Science calculation error")
                    }
                    Action.MCP_CALL -> {
                        val serverId = action.packageName ?: action.label
                        val tool = action.text
                        if (serverId != null && tool != null) {
                            val args = try { JSONObject(action.argsJson ?: "{}") } catch (_: Exception) { JSONObject() }
                            val result = mcpManager.callTool(serverId, tool, args)
                            workingMemory.set(action.outputKey ?: "mcp_result", result)
                            !result.startsWith("Error:")
                        } else false
                    }
                    Action.HIGHLIGHT_ELEMENT -> {
                        val node = action.text?.let { screenReader.findNodeByText(it) }
                        if (node != null) {
                            val focused = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                            node.recycle()
                            focused
                        } else false
                    }
                    Action.AI_PROMPT -> {
                        val packageName = action.packageName
                        val prompt = workingMemory.interpolate(action.prompt.orEmpty())
                        val meta = packageName?.let { AIApps.KNOWN_AI_APPS[it] }
                        if (meta == null) false else {
                            val opened = aiAppInteractor.openAIApp(meta)
                            if (!opened) false else {
                                delay(2000)
                                val typed = aiAppInteractor.typePrompt(prompt)
                                if (!typed) false else {
                                    delay(1000)
                                    val response = aiAppInteractor.waitForResponse()
                                    workingMemory.set(action.outputKey ?: "ai_result", response)
                                    response.isNotBlank()
                                }
                            }
                        }
                    }
                    Action.ERROR -> {
                        _state.value = AgentState.Error(action.message ?: "Task failed")
                        false
                    }
                    else -> false
                }
            } catch (e: Exception) {
                _state.value = AgentState.Error(e.message ?: "Action failed")
                false
            }

            if (!ok) {
                _state.value = AgentState.Error("Action failed: ${action.action}${action.text?.let { " ($it)" } ?: ""}")
                return false
            }
            delay(350)
        }
        return true
    }

    private fun findPackageByLabel(label: String): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val apps: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        
        val normalizedLabel = label.lowercase().trim()
        
        for (app in apps) {
            val appLabel = app.loadLabel(pm).toString().lowercase()
            if (appLabel == normalizedLabel || appLabel.contains(normalizedLabel) || normalizedLabel.contains(appLabel)) {
                return app.activityInfo.packageName
            }
        }
        
        return null
    }
}
}
