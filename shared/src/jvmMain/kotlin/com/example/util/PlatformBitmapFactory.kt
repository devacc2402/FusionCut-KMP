package com.example.util

import androidx.compose.ui.graphics.ImageBitmap

actual object PlatformBitmapFactory {
    actual fun create(width: Int, height: Int): PlatformBitmap {
        // ImageBitmap(w, h) creates a SkiaImageBitmap which is mutable on Desktop
        return DesktopPlatformBitmap(ImageBitmap(width, height))
    }
}
