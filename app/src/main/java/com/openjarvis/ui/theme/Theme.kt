package com.openjarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VoidColor {
    val Void950 = Color(0xFF050505)
    val Void900 = Color(0xFF0A0A0A)
    val Void800 = Color(0xFF101010)
    val Void700 = Color(0xFF181818)
    val Void600 = Color(0xFF242424)

    val Violet = Color(0xFF8B5CF6)
    val VioletDim = Color(0xFF6D3FD6)

    val Cyan = Color(0xFF06B6D4)
    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFEF4444)
    val Amber = Color(0xFFF59E0B)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextDisabled = Color(0xFF666666)

    val BorderSubtle = Color(0xFF2A2A2A)
    val BorderGlow = Color(0xFF8B5CF6)
}

private val JarvisDarkColors = darkColorScheme(
    primary = VoidColor.Violet,
    secondary = VoidColor.Cyan,
    background = VoidColor.Void950,
    surface = VoidColor.Void900,
    onPrimary = VoidColor.TextPrimary,
    onSecondary = VoidColor.TextPrimary,
    onBackground = VoidColor.TextPrimary,
    onSurface = VoidColor.TextPrimary,
    error = VoidColor.Red
)

@Composable
fun OpenJarvisTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisDarkColors,
        content = content
    )
}
