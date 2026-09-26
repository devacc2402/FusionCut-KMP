package com.example.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas

actual object PlatformBitmapFactory {
    actual fun create(width: Int, height: Int): PlatformBitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return AndroidPlatformBitmap(bitmap)
    }
}

// Update AndroidPlatformBitmap to implement getCanvas()
// I'll update AndroidServices.kt for that.
