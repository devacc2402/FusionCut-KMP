package com.example.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints

@Composable
fun rememberIsDesktopMode(threshold: Dp = 800.dp): Boolean {
    // We can't easily access screen width globally in commonMain without BoxWithConstraints or platform-specific info.
    // However, for this project, we can use a simpler approach if we wrap our screen in BoxWithConstraints.
    return false // Placeholder, will be determined by BoxWithConstraints in EditorScreen
}

data class WindowSizeInfo(
    val width: Dp,
    val height: Dp,
    val isDesktop: Boolean
)
