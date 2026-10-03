package com.example.engine

import com.example.model.Layer
import com.example.model.Project
import com.example.model.LayerType
import com.example.util.DesktopMediaProvider
import com.example.util.PlatformBitmap
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

data class TextureInfo(
    val width: Int,
    val height: Int,
    val pixels: IntArray
)

class WindowsFusionEngine : IFusionEngine {

    companion object {
        private var isLibraryLoaded = false
        private val imageCache = mutableMapOf<String, TextureInfo>()
        private val mediaProvider = DesktopMediaProvider()

        init {
            loadNativeLibrary()
        }

        private fun loadNativeLibrary() {
            try {
                val stream = WindowsFusionEngine::class.java.getResourceAsStream("/fusion_engine.dll")
                if (stream != null) {
                    val tempDll = File.createTempFile("fusion_engine_", ".dll")
                    tempDll.deleteOnExit()
                    FileOutputStream(tempDll).use { out -> stream.copyTo(out) }
                    System.load(tempDll.absolutePath)
                    isLibraryLoaded = true
                    println("WindowsFusionEngine: Successfully loaded native DLL from stream resource.")
                    return
                }
            } catch (e: Throwable) {
                println("WindowsFusionEngine: Stream resource load note: ${e.message}")
            }

            try {
                val userDir = System.getProperty("user.dir") ?: "."
                val paths = listOf(
                    File(userDir, "composeApp/src/jvmMain/resources/fusion_engine.dll"),
                    File(userDir, "composeApp/build/native/windows/fusion_engine.dll"),
                    File(userDir, "fusion_engine.dll")
                )
                for (file in paths) {
                    if (file.exists()) {
                        System.load(file.absolutePath)
                        isLibraryLoaded = true
                        println("WindowsFusionEngine: Successfully loaded native DLL from ${file.absolutePath}")
                        return
                    }
                }
            } catch (e: Throwable) {
                println("WindowsFusionEngine: File path load note: ${e.message}")
            }

            try {
                System.loadLibrary("fusion_engine")
                isLibraryLoaded = true
                println("WindowsFusionEngine: Loaded via System.loadLibrary('fusion_engine')")
            } catch (e: Throwable) {
                println("WindowsFusionEngine: System.loadLibrary note: ${e.message}")
            }
        }

        fun getImageTexture(mediaUri: String?): TextureInfo? {
            if (mediaUri.isNullOrBlank()) return null
            imageCache[mediaUri]?.let { return it }

            val bitmap: PlatformBitmap? = runBlocking {
                mediaProvider.loadBitmap(mediaUri)
            }
            if (bitmap == null) return null

            val w = bitmap.width.coerceAtLeast(1)
            val h = bitmap.height.coerceAtLeast(1)
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels)

            val info = TextureInfo(w, h, pixels)
            if (imageCache.size > 30) imageCache.clear()
            imageCache[mediaUri] = info
            return info
        }
    }

    private external fun nInitEngine(): Boolean
    private external fun nRenderFrameScene(
        width: Int,
        height: Int,
        projW: Int,
        projH: Int,
        timeSec: Float,
        bgPixel: Int,
        layerTypes: IntArray,
        shapeTypes: IntArray,
        transforms: FloatArray,
        colors: IntArray,
        dimensions: FloatArray,
        imageWidths: IntArray?,
        imageHeights: IntArray?,
        imagePixelArrays: Array<IntArray?>?,
        videoUris: Array<String?>?,
        pixels: IntArray
    )
    private external fun nEncodeVideoFrameMP4(
        outputPath: String,
        width: Int,
        height: Int,
        fps: Int,
        frameIndex: Int,
        pixels: IntArray,
        isFinalFrame: Boolean
    ): Boolean
    private external fun nReleaseEngine()

    override fun encodeVideoFrame(
        outputPath: String,
        width: Int,
        height: Int,
        fps: Int,
        frameIndex: Int,
        pixels: IntArray,
        isFinalFrame: Boolean
    ): Boolean {
        if (!isLibraryLoaded) return false
        return try {
            nEncodeVideoFrameMP4(outputPath, width, height, fps, frameIndex, pixels, isFinalFrame)
        } catch (e: Throwable) {
            false
        }
    }

    override fun isFallbackActive(): Boolean = !isLibraryLoaded

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
        if (targetWidth <= 0 || targetHeight <= 0 || outPixels.size < targetWidth * targetHeight) return

        if (isLibraryLoaded) {
            try {
                val activeLayers = layers.filter { it.isVisible && it.isActiveAt(timeSec) }.sortedBy { it.orderIndex }
                val layerCount = activeLayers.size

                val layerTypes = IntArray(layerCount)
                val shapeTypes = IntArray(layerCount)
                val transforms = FloatArray(layerCount * 6)
                val colors = IntArray(layerCount * 2)
                val dimensions = FloatArray(layerCount * 4)
                val imageWidths = IntArray(layerCount)
                val imageHeights = IntArray(layerCount)
                val imagePixelArrays = arrayOfNulls<IntArray>(layerCount)
                val videoUris = arrayOfNulls<String>(layerCount)

                for (i in 0 until layerCount) {
                    val layer = activeLayers[i]
                    val anim = layer.getAnimatedTransformAt(timeSec)

                    layerTypes[i] = layer.type.ordinal
                    shapeTypes[i] = layer.shapeType.ordinal

                    transforms[i * 6 + 0] = anim.positionX
                    transforms[i * 6 + 1] = anim.positionY
                    transforms[i * 6 + 2] = anim.scaleX
                    transforms[i * 6 + 3] = anim.scaleY
                    transforms[i * 6 + 4] = anim.rotation
                    transforms[i * 6 + 5] = anim.opacity

                    val fillColor = if (layer.type == LayerType.TEXT) layer.textColor else layer.fillColor
                    colors[i * 2 + 0] = (fillColor and 0xFFFFFFFFL).toInt()
                    colors[i * 2 + 1] = (layer.strokeColor and 0xFFFFFFFFL).toInt()

                    dimensions[i * 4 + 0] = layer.baseWidth
                    dimensions[i * 4 + 1] = layer.baseHeight
                    dimensions[i * 4 + 2] = layer.strokeWidth
                    dimensions[i * 4 + 3] = layer.cornerRadius

                    if (layer.type == LayerType.IMAGE || layer.type == LayerType.VIDEO) {
                        val imgInfo = getImageTexture(layer.mediaUri)
                        if (imgInfo != null) {
                            imageWidths[i] = imgInfo.width
                            imageHeights[i] = imgInfo.height
                            imagePixelArrays[i] = imgInfo.pixels
                        } else {
                            imageWidths[i] = 0
                            imageHeights[i] = 0
                            imagePixelArrays[i] = null
                        }
                    } else {
                        imageWidths[i] = 0
                        imageHeights[i] = 0
                        imagePixelArrays[i] = null
                    }

                    if (layer.type == LayerType.VIDEO) {
                        videoUris[i] = layer.mediaUri
                    } else {
                        videoUris[i] = null
                    }
                }

                val bgPixel = (project.backgroundColor and 0xFFFFFFFFL).toInt()

                nRenderFrameScene(
                    targetWidth, targetHeight,
                    project.width, project.height,
                    timeSec, bgPixel,
                    layerTypes, shapeTypes, transforms, colors, dimensions,
                    imageWidths, imageHeights, imagePixelArrays,
                    videoUris,
                    outPixels
                )
                return
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        // Guaranteed Software Fallback Compositing Engine
        renderSoftwareFallback(project, layers, timeSec, targetWidth, targetHeight, outPixels)
    }

    private fun renderSoftwareFallback(
        project: Project,
        layers: List<Layer>,
        timeSec: Float,
        width: Int,
        height: Int,
        outPixels: IntArray
    ) {
        val totalPixels = width * height
        val bgPixel = (project.backgroundColor and 0xFFFFFFFFL).toInt()

        // 1. Fill canvas background
        outPixels.fill(bgPixel, 0, totalPixels)

        // 2. Render Active Layers (Shapes & Color Cards)
        val centerX = width / 2
        val centerY = height / 2

        val activeLayers = layers.filter { it.isVisible && it.isActiveAt(timeSec) }.sortedBy { it.orderIndex }

        for (layer in activeLayers) {
            val anim = layer.getAnimatedTransformAt(timeSec)
            val layerCenterX = centerX + (anim.positionX * (width.toFloat() / project.width.coerceAtLeast(1))).toInt()
            val layerCenterY = centerY + (anim.positionY * (height.toFloat() / project.height.coerceAtLeast(1))).toInt()

            val baseW = if (layer.type == LayerType.SOLID) project.width.toFloat() else layer.baseWidth
            val baseH = if (layer.type == LayerType.SOLID) project.height.toFloat() else layer.baseHeight

            val scaleW = ((baseW * (width.toFloat() / project.width.coerceAtLeast(1))) * anim.scaleX).toInt().coerceAtLeast(10)
            val scaleH = ((baseH * (height.toFloat() / project.height.coerceAtLeast(1))) * anim.scaleY).toInt().coerceAtLeast(10)

            val startX = (layerCenterX - scaleW / 2).coerceIn(0, width)
            val endX = (layerCenterX + scaleW / 2).coerceIn(0, width)
            val startY = (layerCenterY - scaleH / 2).coerceIn(0, height)
            val endY = (layerCenterY + scaleH / 2).coerceIn(0, height)

            val colorPixel = (layer.fillColor and 0xFFFFFFFFL).toInt()

            for (y in startY until endY) {
                val rowOffset = y * width
                for (x in startX until endX) {
                    outPixels[rowOffset + x] = colorPixel
                }
            }
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
