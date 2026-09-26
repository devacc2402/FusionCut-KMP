package com.example.engine

import com.example.model.Layer
import com.example.model.Project

class WindowsFusionEngine : IFusionEngine {

    companion object {
        private var isLibraryLoaded = false

        init {
            try {
                System.loadLibrary("fusion_engine")
                isLibraryLoaded = true
            } catch (e: Throwable) {
                println("WindowsFusionEngine: Native library 'fusion_engine.dll' not loaded: ${e.message}")
            }
        }
    }

    private external fun nInitEngine(): Boolean
    private external fun nRenderFrame(width: Int, height: Int, timeSec: Float, pixels: IntArray)
    private external fun nReleaseEngine()

    override fun initialize(): Boolean {
        if (!isLibraryLoaded) return false
        return try {
            nInitEngine()
        } catch (e: Throwable) {
            false
        }
    }

    override fun renderFrame(
        project: Project,
        layers: List<Layer>,
        timeSec: Float,
        targetWidth: Int,
        targetHeight: Int,
        outPixels: IntArray
    ) {
        if (!isLibraryLoaded) return
        try {
            nRenderFrame(targetWidth, targetHeight, timeSec, outPixels)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun release() {
        if (!isLibraryLoaded) return
        try {
            nReleaseEngine()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

actual object FusionEngineProvider {
    private val instance: IFusionEngine by lazy {
        WindowsFusionEngine().apply { initialize() }
    }

    actual fun getEngine(): IFusionEngine = instance
}
