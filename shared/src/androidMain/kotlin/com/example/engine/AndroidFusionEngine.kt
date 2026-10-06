package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import com.example.model.Layer
import com.example.model.LayerType
import com.example.model.Project
import com.example.util.AndroidMediaProvider
import com.example.util.AndroidServices
import com.example.util.PlatformBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

data class AndroidTextureInfo(
    val width: Int,
    val height: Int,
    val pixels: IntArray
)

class AndroidFusionEngine : IFusionEngine {

    companion object {
        private var isLibraryLoaded = false
        private val textCache = mutableMapOf<String, AndroidTextureInfo>()
        private val imageCache = mutableMapOf<String, AndroidTextureInfo>()

        init {
            try {
                System.loadLibrary("fusion_engine")
                isLibraryLoaded = true
            } catch (e: Throwable) {
                println("AndroidFusionEngine: Native library 'libfusion_engine.so' not loaded: ${e.message}")
            }
        }

        fun getTextTexture(text: String, fontSize: Float, textColor: Long, isBold: Boolean, isItalic: Boolean, baseW: Float, baseH: Float): AndroidTextureInfo {
            val key = "$text@$fontSize@$textColor@$isBold@$isItalic@${baseW}x$baseH"
            textCache[key]?.let { return it }

            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = fontSize.coerceAtLeast(12f)
                color = (textColor and 0xFFFFFFFFL).toInt()
                isFakeBoldText = isBold
                textSkewX = if (isItalic) -0.25f else 0f
            }

            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            val measuredW = (bounds.width() + 40).coerceAtLeast(baseW.toInt()).coerceAtLeast(100)
            val measuredH = (bounds.height() + 20).coerceAtLeast(baseH.toInt()).coerceAtLeast(40)

            val bitmap = Bitmap.createBitmap(measuredW, measuredH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val x = ((measuredW - paint.measureText(text)) / 2f).coerceAtLeast(0f)
            val y = ((measuredH - bounds.height()) / 2f + bounds.height()).coerceAtLeast(bounds.height().toFloat())
            canvas.drawText(text, x, y, paint)

            val pixels = IntArray(measuredW * measuredH)
            bitmap.getPixels(pixels, 0, measuredW, 0, 0, measuredW, measuredH)
            bitmap.recycle()

            if (textCache.size > 50) textCache.clear()
            val info = AndroidTextureInfo(measuredW, measuredH, pixels)
            textCache[key] = info
            return info
        }

        fun getImageTexture(mediaUri: String?): AndroidTextureInfo? {
            if (mediaUri.isNullOrBlank()) return null
            imageCache[mediaUri]?.let { return it }

            val ctx = AndroidServices.context ?: return null
            val bitmap: PlatformBitmap = runBlocking(Dispatchers.IO) {
                AndroidMediaProvider(ctx).loadBitmap(mediaUri)
            } ?: return null

            val w = bitmap.width.coerceAtLeast(1)
            val h = bitmap.height.coerceAtLeast(1)
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels)

            val info = AndroidTextureInfo(w, h, pixels)
            if (imageCache.size > 30) imageCache.clear()
            imageCache[mediaUri] = info
            return info
        }

        fun getVideoFrameTexture(mediaUri: String?, timeSec: Float): AndroidTextureInfo? {
            if (mediaUri.isNullOrBlank()) return null
            val bucket = (timeSec * 30).toInt().coerceAtLeast(0) // 30 FPS exact frame timecode
            val cacheKey = "$mediaUri@$bucket"
            imageCache[cacheKey]?.let { return it }

            val ctx = AndroidServices.context ?: return null
            val bitmap: PlatformBitmap = runBlocking(Dispatchers.IO) {
                AndroidMediaProvider(ctx).loadFrame(mediaUri, timeSec, 640, 640) ?: AndroidMediaProvider(ctx).loadBitmap(mediaUri, isVideo = true)
            } ?: return null

            val w = bitmap.width.coerceAtLeast(1)
            val h = bitmap.height.coerceAtLeast(1)
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels)

            val info = AndroidTextureInfo(w, h, pixels)
            if (imageCache.size > 60) imageCache.clear()
            imageCache[cacheKey] = info
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
        pixels: IntArray
    )
    private external fun nReleaseEngine()

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

                    if (layer.type == LayerType.TEXT) {
                        val textInfo = getTextTexture(
                            text = layer.text,
                            fontSize = layer.fontSize,
                            textColor = layer.textColor,
                            isBold = layer.isBold,
                            isItalic = layer.isItalic,
                            baseW = layer.baseWidth,
                            baseH = layer.baseHeight
                        )

                        dimensions[i * 4 + 0] = textInfo.width.toFloat()
                        dimensions[i * 4 + 1] = textInfo.height.toFloat()
                        dimensions[i * 4 + 2] = layer.strokeWidth
                        dimensions[i * 4 + 3] = layer.cornerRadius

                        imageWidths[i] = textInfo.width
                        imageHeights[i] = textInfo.height
                        imagePixelArrays[i] = textInfo.pixels
                    } else if (layer.type == LayerType.VIDEO) {
                        val vidInfo = getVideoFrameTexture(layer.mediaUri, timeSec) ?: getImageTexture(layer.mediaUri)
                        if (vidInfo != null) {
                            dimensions[i * 4 + 0] = layer.baseWidth
                            dimensions[i * 4 + 1] = layer.baseHeight
                            dimensions[i * 4 + 2] = layer.strokeWidth
                            dimensions[i * 4 + 3] = layer.cornerRadius

                            imageWidths[i] = vidInfo.width
                            imageHeights[i] = vidInfo.height
                            imagePixelArrays[i] = vidInfo.pixels
                        } else {
                            dimensions[i * 4 + 0] = layer.baseWidth
                            dimensions[i * 4 + 1] = layer.baseHeight
                            dimensions[i * 4 + 2] = layer.strokeWidth
                            dimensions[i * 4 + 3] = layer.cornerRadius

                            imageWidths[i] = 0
                            imageHeights[i] = 0
                            imagePixelArrays[i] = null
                        }
                    } else if (layer.type == LayerType.IMAGE) {
                        val imgInfo = getImageTexture(layer.mediaUri)
                        if (imgInfo != null) {
                            dimensions[i * 4 + 0] = layer.baseWidth
                            dimensions[i * 4 + 1] = layer.baseHeight
                            dimensions[i * 4 + 2] = layer.strokeWidth
                            dimensions[i * 4 + 3] = layer.cornerRadius

                            imageWidths[i] = imgInfo.width
                            imageHeights[i] = imgInfo.height
                            imagePixelArrays[i] = imgInfo.pixels
                        } else {
                            dimensions[i * 4 + 0] = layer.baseWidth
                            dimensions[i * 4 + 1] = layer.baseHeight
                            dimensions[i * 4 + 2] = layer.strokeWidth
                            dimensions[i * 4 + 3] = layer.cornerRadius

                            imageWidths[i] = 0
                            imageHeights[i] = 0
                            imagePixelArrays[i] = null
                        }
                    } else {
                        dimensions[i * 4 + 0] = layer.baseWidth
                        dimensions[i * 4 + 1] = layer.baseHeight
                        dimensions[i * 4 + 2] = layer.strokeWidth
                        dimensions[i * 4 + 3] = layer.cornerRadius

                        imageWidths[i] = 0
                        imageHeights[i] = 0
                        imagePixelArrays[i] = null
                    }
                }

                val bgPixel = (project.backgroundColor and 0xFFFFFFFFL).toInt()

                nRenderFrameScene(
                    targetWidth, targetHeight,
                    project.width, project.height,
                    timeSec, bgPixel,
                    layerTypes, shapeTypes, transforms, colors, dimensions,
                    imageWidths, imageHeights, imagePixelArrays,
                    outPixels
                )
                return
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

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

        outPixels.fill(bgPixel, 0, totalPixels)

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
        AndroidFusionEngine().apply { initialize() }
    }

    actual fun getEngine(): IFusionEngine = instance
}
