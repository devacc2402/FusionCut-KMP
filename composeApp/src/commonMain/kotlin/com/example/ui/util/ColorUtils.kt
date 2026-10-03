package com.example.ui.util

import androidx.compose.ui.graphics.Color

/**
 * Safely converts a 64-bit ARGB Long (e.g. 0xFF0A0D14L) or Int to Compose [Color].
 * Uses explicit (r, g, b, a) RGBA integer components to guarantee sRGB ColorSpace ID 0 in Skiko/Compose.
 */
fun Long.toComposeColor(): Color {
    val a = ((this shr 24) and 0xFFL).toInt()
    val r = ((this shr 16) and 0xFFL).toInt()
    val g = ((this shr 8) and 0xFFL).toInt()
    val b = (this and 0xFFL).toInt()
    return Color(red = r, green = g, blue = b, alpha = a)
}

fun Int.toComposeColor(): Color {
    val a = (this shr 24) and 0xFF
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    return Color(red = r, green = g, blue = b, alpha = a)
}

/**
 * Safe alternative to [Color.copy(alpha = ...)] in Compose Skiko Desktop.
 * Creates a fresh sRGB Color with specified alpha (0.0f .. 1.0f) without corrupting ColorSpace bits.
 */
fun Color.withAlpha(alphaFloat: Float): Color {
    val a = (alphaFloat.coerceIn(0f, 1f) * 255f).toInt()
    val r = (this.red * 255f).toInt()
    val g = (this.green * 255f).toInt()
    val b = (this.blue * 255f).toInt()
    return Color(red = r, green = g, blue = b, alpha = a)
}
