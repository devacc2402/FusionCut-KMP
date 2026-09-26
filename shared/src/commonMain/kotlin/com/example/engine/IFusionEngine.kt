package com.example.engine

import com.example.model.Layer
import com.example.model.Project
import com.example.util.PlatformBitmap

interface IFusionEngine {
    fun initialize(): Boolean
    fun renderFrame(
        project: Project,
        layers: List<Layer>,
        timeSec: Float,
        targetWidth: Int,
        targetHeight: Int,
        outPixels: IntArray
    )
    fun release()
}
