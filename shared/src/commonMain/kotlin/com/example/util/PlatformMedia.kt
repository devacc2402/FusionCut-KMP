package com.example.util

import androidx.compose.ui.graphics.ImageBitmap

expect object PlatformMedia {
    fun getFrame(uri: String, timeSec: Float): ImageBitmap?
}
