package com.example.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.engine.BitmapCache

actual object PlatformMedia {
    actual fun getFrame(uri: String, timeSec: Float): ImageBitmap? {
        return BitmapCache.getFrame(uri, timeSec)?.asImageBitmap()
    }
}
