package com.example.ui.util

import androidx.compose.ui.graphics.Color

/**
 * Safely converts a 64-bit ARGB Long (e.g. 0xFF0A0D14L) to a Compose [Color].
 * Masks out high 32 bits to prevent invalid ColorSpace ID lookup crashes in Skiko/Compose.
 */
fun Long.toComposeColor(): Color {
    val argb = (this and 0xFFFFFFFFL).toInt()
    return Color(argb)
}
