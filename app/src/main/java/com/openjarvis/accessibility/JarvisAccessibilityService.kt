package com.openjarvis.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class JarvisAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun openAppByPackage(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startActivity(intent)
        return true
    }

    fun openAppByLabel(label: String): Boolean {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = pm.queryIntentActivities(intent, 0)
        val normalized = label.trim().lowercase()
        val match = apps.firstOrNull {
            val appLabel = it.loadLabel(pm).toString().lowercase()
            appLabel == normalized || appLabel.contains(normalized) || normalized.contains(appLabel)
        }
        return match?.let { openAppByPackage(it.activityInfo.packageName) } ?: false
    }

    fun tapByText(text: String): Boolean {
        val node = findNodeByText(text) ?: return false
        return try { clickNodeOrParent(node) } finally { node.recycle() }
    }

    fun tapByHint(hint: String): Boolean {
        val node = findNodeByHint(hint) ?: return false
        return try { clickNodeOrParent(node) } finally { node.recycle() }
    }

    fun tapAt(x: Int, y: Int): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 24) return false
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun longPressAt(x: Int, y: Int, durationMs: Long = 700): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 24) return false
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(400, 3000)))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun longPressByText(text: String): Boolean {
        val node = findNodeByText(text) ?: return false
        return try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            longPressAt(bounds.centerX(), bounds.centerY())
        } finally { node.recycle() }
    }

    fun swipe(direction: String, distance: String = "medium"): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 24) return false
        val metrics = resources.displayMetrics
        val cx = metrics.widthPixels / 2f
        val cy = metrics.heightPixels / 2f
        val d = when (distance.lowercase()) { "short" -> 0.25f; "long" -> 0.75f; else -> 0.5f }
        val horizontal = direction.equals("left", true) || direction.equals("right", true)
        val delta = if (horizontal) metrics.widthPixels * d else metrics.heightPixels * d
        val (x1, y1, x2, y2) = when (direction.lowercase()) {
            "up" -> floatArrayOf(cx, cy + delta / 2, cx, cy - delta / 2)
            "down" -> floatArrayOf(cx, cy - delta / 2, cx, cy + delta / 2)
            "left" -> floatArrayOf(cx + delta / 2, cy, cx - delta / 2, cy)
            "right" -> floatArrayOf(cx - delta / 2, cy, cx + delta / 2, cy)
            else -> return false
        }
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 450))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val node = findScrollableNode(root)
            node?.performAction(
                if (direction.equals("down", true)) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            ) ?: swipe(direction, "medium")
        } finally { root.recycle() }
    }

    fun typeText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return try {
            val focused = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            focused?.let {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } ?: false
        } finally { rootNode.recycle() }
    }

    fun clearAndTypeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally { root.recycle() }
    }

    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun getCurrentPackageName(): String = rootInActiveWindow?.packageName?.toString().orEmpty()

    suspend fun captureScreenshot(): android.graphics.Bitmap? {
        if (android.os.Build.VERSION.SDK_INT < 30) return null
        return suspendCancellableCoroutine { cont ->
            takeScreenshot(DISPLAY_ID, mainExecutor, object : TakeScreenshotCallback() {
                override fun onSuccess(result: ScreenshotResult) {
                    val hardware = result.hardwareBuffer
                    val colorSpace = result.colorSpace
                    val bitmap = try {
                        android.graphics.Bitmap.wrapHardwareBuffer(hardware, colorSpace)
                    } catch (_: Exception) { null }
                    hardware.close()
                    cont.resume(bitmap)
                }
                override fun onFailure(errorCode: Int) { cont.resume(null) }
            })
        }
    }


    fun waitForText(text: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(100, 120_000)
        while (System.currentTimeMillis() < deadline) {
            if (containsText(text)) return true
            Thread.sleep(150)
        }
        return false
    }

    private fun containsText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try { findNodeRecursive(root, text.lowercase()) != null } finally { root.recycle() }
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return try { findNodeRecursive(root, text.trim().lowercase()) } finally { root.recycle() }
    }

    fun findNodeByHint(hint: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return try { findHintRecursive(root, hint.trim().lowercase()) } finally { root.recycle() }
    }

    private fun findNodeRecursive(root: AccessibilityNodeInfo, normalized: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(root))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            if ((text.contains(normalized) || desc.contains(normalized)) && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }
        return null
    }

    private fun findHintRecursive(root: AccessibilityNodeInfo, normalized: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(root))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val hint = node.hintText?.toString()?.lowercase().orEmpty()
            if (hint.contains(normalized) && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var parent = node.parent
        repeat(4) {
            if (parent == null) return false
            if (parent.isClickable) {
                val ok = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return ok
            }
            val next = parent.parent
            parent.recycle()
            parent = next
        }
        return false
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(root))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            node.recycle()
        }
        return null
    }

    companion object {
        private const val DISPLAY_ID = 0
        @Volatile var instance: JarvisAccessibilityService? = null
            private set
    }
}
