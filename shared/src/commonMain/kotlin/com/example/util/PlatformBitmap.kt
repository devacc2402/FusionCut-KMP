package com.example.util

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap

interface PlatformBitmap {
    val width: Int
    val height: Int
    fun asImageBitmap(): ImageBitmap
    fun getPixels(pixels: IntArray)
    fun setPixels(pixels: IntArray)
    fun isRecycled(): Boolean
}

expect object PlatformBitmapFactory {
    fun create(width: Int, height: Int): PlatformBitmap
}
