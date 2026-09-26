package com.example.util

import androidx.compose.ui.graphics.ImageBitmap

actual object PlatformMedia {
    actual fun getFrame(uri: String, timeSec: Float): ImageBitmap? {
        // Dynamic video decoding is currently only supported on Android.
        // On Desktop, we fallback to the initial frame/bitmap if available.
        return null 
    }
}
