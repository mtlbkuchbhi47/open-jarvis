package com.openjarvis.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Open Jarvis dark UI palette.
 * "Void" scale goes from lightest (600) to darkest/near-black (950),
 * matching the dark, glass-panel aesthetic used across the dashboard,
 * onboarding, settings and floating overlay screens.
 */
object VoidColor {

    // Background scale
    val Void600 = Color(0xFF3A3A47)
    val Void700 = Color(0xFF2A2A35)
    val Void800 = Color(0xFF1C1C26)
    val Void900 = Color(0xFF13131A)
    val Void950 = Color(0xFF0A0A0F)

    // Text
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA0A0B0)
    val TextDisabled = Color(0xFF5A5A66)

    // Borders
    val BorderSubtle = Color(0x33FFFFFF)
    val BorderGlow = Color(0x668B5CF6)

    // Accents
    val Violet = Color(0xFF8B5CF6)
    val VioletDim = Color(0xFF6D28D9)
    val Cyan = Color(0xFF22D3EE)
    val Amber = Color(0xFFF59E0B)
    val Green = Color(0xFF4CAF50)
    val Red = Color(0xFFF44336)
}
