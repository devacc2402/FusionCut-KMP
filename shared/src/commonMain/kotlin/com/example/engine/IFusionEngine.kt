package com.example.engine

import com.example.model.Layer
import com.example.model.Project

interface IFusionEngine {
    fun initialize(): Boolean
    fun isFallbackActive(): Boolean
    fun renderFrame(
        project: Project,
        layers: List<Layer>,
        timeSec: Float,
        targetWidth: Int,
        targetHeight: Int,
        outPixels: IntArray
    )
    fun encodeVideoFrame(
        outputPath: String,
        width: Int,
        height: Int,
        fps: Int,
        frameIndex: Int,
        pixels: IntArray,
        isFinalFrame: Boolean
    ): Boolean = false
    fun release()
}
