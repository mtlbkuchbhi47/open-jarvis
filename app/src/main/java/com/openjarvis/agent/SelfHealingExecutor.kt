package com.openjarvis.agent

import android.content.Context
import com.openjarvis.accessibility.ScreenReader
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.llm.UniversalAdapter
import kotlinx.coroutines.delay

class SelfHealingExecutor(private val context: Context) {
    
    private val screenReader = ScreenReader(context)
    private val graphifyRepo = GraphifyRepository(context)
    private val llm = UniversalAdapter.getModelManager(context)
    
    private val maxAttempts = 3
    private val baseDelayMs = 1000L
    
    suspend fun executeWithHealing(
        action: Action,
        context: ExecutionContext,
        attempt: Int = 1
    ): ActionResult {
        
        val result = tryExecuteAction(action, context)
        
        if (result.success) return result
        
        if (attempt >= maxAttempts) {
            return ActionResult.Failed("Could not complete after $maxAttempts attempts")
        }
        
        val currentScreen = screenReader.extractAllText()
        
        val healingPrompt = buildHealingPrompt(action, context, currentScreen, attempt)
        
        val alternativeResponse = try {
            llm.complete(healingPrompt.first, healingPrompt.second)
        } catch (e: Exception) {
            null
        }
        
        delay(baseDelayMs * attempt)
        
        if (alternativeResponse != null) {
            val alternativeAction = parseActionFromLLM(alternativeResponse)
            if (alternativeAction != null) {
                return executeWithHealing(alternativeAction, context, attempt + 1)
            }
        }
        
        return executeWithHealing(action, context, attempt + 1)
    }
    
    private fun tryExecuteAction(action: Action, context: ExecutionContext): ActionResult {
        return try {
            val service = com.openjarvis.accessibility.JarvisAccessibilityService.instance
            
            when (action.action) {
                Action.TAP -> {
                    val ok = service?.tapByText(action.text.orEmpty()) == true
                    if (ok) ActionResult.Success("tapped ${action.text}") else ActionResult.Failed("tap failed")
                }
                Action.TYPE -> {
                    val ok = service?.typeText(action.value.orEmpty()) == true
                    if (ok) ActionResult.Success("typed") else ActionResult.Failed("type failed")
                }
                Action.OPEN_APP -> {
                    val ok = if (action.packageName != null) service?.openAppByPackage(action.packageName) == true
                    else action.label?.let { service?.openAppByLabel(it) } == true
                    if (ok) ActionResult.Success("opened") else ActionResult.Failed("app open failed")
                }
                Action.PRESS_BACK -> if (service?.pressBack() == true) ActionResult.Success("pressed back") else ActionResult.Failed("back failed")
                Action.PRESS_HOME -> if (service?.pressHome() == true) ActionResult.Success("pressed home") else ActionResult.Failed("home failed")
                Action.PRESS_RECENTS -> if (service?.pressRecents() == true) ActionResult.Success("pressed recents") else ActionResult.Failed("recents failed")
                Action.TAP_COORDS -> if (service?.tapAt(action.x ?: -1, action.y ?: -1) == true) ActionResult.Success("tapped coordinates") else ActionResult.Failed("coordinate tap failed")
                Action.LONG_PRESS -> if (service?.longPressByText(action.text.orEmpty()) == true) ActionResult.Success("long pressed") else ActionResult.Failed("long press failed")
                Action.SWIPE -> if (service?.swipe(action.direction.orEmpty(), action.distance ?: "medium") == true) ActionResult.Success("swiped") else ActionResult.Failed("swipe failed")
                Action.SCROLL -> if (service?.scroll(action.direction ?: "down") == true) ActionResult.Success("scrolled") else ActionResult.Failed("scroll failed")
                Action.CLEAR_TYPE -> if (service?.clearAndTypeText(action.value.orEmpty()) == true) ActionResult.Success("cleared and typed") else ActionResult.Failed("clear/type failed")
                Action.WAIT_FOR -> if (service?.waitForText(action.text.orEmpty(), action.timeoutMs) == true) ActionResult.Success("condition met") else ActionResult.Failed("timeout waiting for ${action.text}")
                else -> ActionResult.Failed("unsupported action ${action.action}")
            }
        } catch (e: Exception) {
            ActionResult.Failed(e.message ?: "Unknown error")
        }
    }
    
    private fun buildHealingPrompt(
        action: Action,
        context: ExecutionContext,
        currentScreen: String,
        attempt: Int
    ): Pair<String, String> {
        val system = """
Action failed: ${action.description ?: action.action}
Expected to see: ${context.expectedState ?: "task completion"}
Current screen shows: ${currentScreen.take(500)}
Attempt: $attempt/$maxAttempts

What went wrong and what alternative action should I try?
Respond with a single alternative Action JSON.
        """.trimIndent()
        
        return system to """
The action failed. Current screen: "$currentScreen"
Give me one alternative action that might work.
        """
    }
    
    private fun parseActionFromLLM(llmResponse: String): Action? {
        return try {
            val json = org.json.JSONObject(llmResponse.trim())
            Action(
                action = json.getString("action"),
                text = json.optString("text", null),
                value = json.optString("value", null),
                packageName = json.optString("package", null)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    data class ExecutionContext(
        val expectedState: String?,
        val phaseId: String,
        val previousPhaseOutcome: String?,
        val screenBefore: String
    )
    
    sealed class ActionResult {
        data class Success(val message: String) : ActionResult()
        data class Failed(val reason: String) : ActionResult()
    }
}