package com.openjarvis.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

class ScreenReader(private val serviceProvider: () -> JarvisAccessibilityService?) {

    constructor(service: JarvisAccessibilityService) : this({ service })
    constructor(@Suppress("UNUSED_PARAMETER") context: android.content.Context) : this({ JarvisAccessibilityService.instance })

    fun extractAllText(): String {
        val root = serviceProvider()?.rootInActiveWindow ?: return ""
        val builder = StringBuilder()
        try { extractTextRecursive(root, builder) } finally { root.recycle() }
        return builder.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun extractTextRecursive(node: AccessibilityNodeInfo, builder: StringBuilder) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append(it).append(' ') }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                try { extractTextRecursive(child, builder) } finally { child.recycle() }
            }
        }
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? =
        serviceProvider()?.findNodeByText(text)

    fun findNodeByHint(hint: String): AccessibilityNodeInfo? =
        serviceProvider()?.findNodeByHint(hint)

    fun getFocusedNode(): AccessibilityNodeInfo? {
        val root = serviceProvider()?.rootInActiveWindow ?: return null
        return try { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } finally { root.recycle() }
    }
}
