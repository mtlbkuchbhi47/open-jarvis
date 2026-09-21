package com.openjarvis.accessibility

import com.openjarvis.agent.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Executes every action advertised by the agent prompt. */
class ActionExecutor(private val service: JarvisAccessibilityService) {

    private val screenReader = ScreenReader(service)

    suspend fun execute(actions: List<Action>): ExecutionResult = withContext(Dispatchers.Main.immediate) {
        val results = mutableListOf<ActionResult>()
        for (action in actions) {
            val result = executeStep(action)
            results += result
            if (!result.success) {
                return@withContext ExecutionResult(results, false, result.errorMessage)
            }
            delay(250)
        }
        ExecutionResult(results, true, null)
    }

    private suspend fun executeStep(action: Action): ActionResult = try {
        when (action.action) {
            Action.OPEN_APP -> {
                val ok = when {
                    action.packageName != null -> service.openAppByPackage(action.packageName)
                    action.label != null -> service.openAppByLabel(action.label)
                    else -> false
                }
                result(action, ok, "Could not open app")
            }
            Action.TAP -> result(action, service.tapByText(action.text.orEmpty()), "Could not tap '${action.text}'")
            Action.TAP_COORDS -> result(action, service.tapAt(action.x ?: -1, action.y ?: -1), "Coordinate tap failed")
            Action.LONG_PRESS -> result(action, service.longPressByText(action.text.orEmpty()), "Long press failed")
            Action.TYPE -> result(action, service.typeText(action.value.orEmpty()), "Could not type text")
            Action.CLEAR_TYPE -> result(action, service.clearAndTypeText(action.value.orEmpty()), "Could not clear/type")
            Action.SWIPE -> result(action, service.swipe(action.direction.orEmpty(), action.distance ?: "medium"), "Swipe failed")
            Action.SCROLL -> result(action, service.scroll(action.direction ?: "down"), "Scroll failed")
            Action.PRESS_BACK -> result(action, service.pressBack(), "Back failed")
            Action.PRESS_HOME -> result(action, service.pressHome(), "Home failed")
            Action.PRESS_RECENTS -> result(action, service.pressRecents(), "Recents failed")
            Action.WAIT_FOR -> result(action, service.waitForText(action.text.orEmpty(), action.timeoutMs), "Timed out waiting for '${action.text}'")
            Action.READ_SCREEN, Action.EXTRACT_TEXT -> {
                val text = screenReader.extractAllText()
                result(action, text.isNotBlank(), "Screen is empty")
            }
            Action.SCREENSHOT -> result(action, service.captureScreenshot() != null, "Screenshot unavailable")
            Action.ERROR -> ActionResult(action, false, action.message ?: "Agent returned an error")
            else -> ActionResult(action, false, "Unsupported action: ${action.action}")
        }
    } catch (e: Exception) {
        ActionResult(action, false, e.message ?: "Action failed")
    }

    private fun result(action: Action, ok: Boolean, error: String): ActionResult =
        ActionResult(action, ok, if (ok) null else error)

    data class ActionResult(val action: Action, val success: Boolean, val errorMessage: String?)
    data class ExecutionResult(val partialResults: List<ActionResult>, val success: Boolean, val errorMessage: String?)
}
