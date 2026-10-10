package com.example.util

import com.example.model.Layer
import com.example.model.MediaMetadataInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DesktopPlatformBitmap(private val imageBitmap: ImageBitmap) : PlatformBitmap {
    override val width: Int get() = imageBitmap.width
    override val height: Int get() = imageBitmap.height
    
    override fun asImageBitmap(): ImageBitmap = imageBitmap

    private var cachedBytes: ByteArray? = null

    override fun getPixels(pixels: IntArray) {
        // readPixels on Desktop expects stride as number of Ints per row (width)
        imageBitmap.readPixels(
            buffer = pixels,
            startX = 0,
            startY = 0,
            width = width,
            height = height,
            bufferOffset = 0,
            stride = width
        )
    }

    override fun setPixels(pixels: IntArray) {
        val requiredCapacity = width * height * 4
        var bytes = cachedBytes
        if (bytes == null || bytes.size < requiredCapacity) {
            bytes = ByteArray(requiredCapacity)
            cachedBytes = bytes
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        buf.asIntBuffer().put(pixels)

        val skiaImage = Image.makeRaster(
            ImageInfo.makeN32Premul(width, height),
            bytes,
            width * 4
        )
        val canvas = Canvas(imageBitmap)
        canvas.drawImage(skiaImage.toComposeImageBitmap(), androidx.compose.ui.geometry.Offset.Zero, Paint())
    }

    override fun isRecycled(): Boolean = false
}

class DesktopAudioPlayer : AudioPlayer {
    override fun onPlay(startTime: Float, layers: List<Layer>) { /* TODO */ }
    override fun onPause() { /* TODO */ }
    override fun onSeek(time: Float, layers: List<Layer>) { /* TODO */ }
    override fun release() { /* TODO */ }
}

class DesktopMediaProvider : MediaProvider {
    private val bitmapCache = mutableMapOf<String, PlatformBitmap>()

    private fun resolveFile(uriString: String): File? {
        var cleanPath = uriString
            .removePrefix("file:///")
            .removePrefix("file://")
            .removePrefix("file:/")
            .replace("%20", " ")
        while (cleanPath.startsWith("/") || cleanPath.startsWith("\\")) {
            cleanPath = cleanPath.substring(1)
        }
        val f1 = File(cleanPath)
        if (f1.exists()) return f1
        val f2 = File(uriString)
        if (f2.exists()) return f2
        return null
    }

    override suspend fun getMediaMetadata(uriString: String): MediaMetadataInfo? {
        val file = resolveFile(uriString) ?: return null
        
        val isVideo = uriString.contains("video", ignoreCase = true) || uriString.endsWith(".mp4", ignoreCase = true) || uriString.endsWith(".mov", ignoreCase = true)
        val durationMs = try {
            if (isVideo) Mp4DurationExtractor.getDurationMs(file) else 0L
        } catch (e: Exception) { 0L }

        var imgW = 1280
        var imgH = 720
        try {
            val bytes = file.readBytes()
            val img = Image.makeFromEncoded(bytes)
            if (img.width > 0 && img.height > 0) {
                imgW = img.width
                imgH = img.height
            }
        } catch (e: Exception) {
            // Ignore image decode error for video files
        }

        return MediaMetadataInfo(
            width = imgW,
            height = imgH,
            durationMs = if (durationMs > 0) durationMs else 5000L,
            rotation = 0,
            isVideo = isVideo
        )
    }

    override suspend fun copyMediaToLocalStorage(projectId: Long, uriString: String, isVideo: Boolean): String {
        return uriString
    }

    override suspend fun loadBitmap(uriString: String, isVideo: Boolean): PlatformBitmap? {
        if (bitmapCache.containsKey(uriString)) return bitmapCache[uriString]
        
        return try {
            val file = resolveFile(uriString) ?: return null
            val bytes = file.readBytes()
            val skiaImage = Image.makeFromEncoded(bytes)
            
            // Create a MUTABLE ImageBitmap and draw the loaded image into it
            val mutableBitmap = ImageBitmap(skiaImage.width, skiaImage.height)
            val canvas = Canvas(mutableBitmap)
            canvas.drawImage(skiaImage.toComposeImageBitmap(), androidx.compose.ui.geometry.Offset.Zero, Paint())
            
            val platformBitmap = DesktopPlatformBitmap(mutableBitmap)
            bitmapCache[uriString] = platformBitmap
            platformBitmap
        } catch (e: Exception) {
            println("DesktopMediaProvider: Error loading bitmap: ${e.message}")
            null
        }
    }

    override suspend fun loadFrame(uriString: String, timeSec: Float, targetW: Int, targetH: Int): PlatformBitmap? {
        return loadBitmap(uriString, isVideo = true)
    }

    override fun clearCache() {
        bitmapCache.clear()
    }
    
    override fun trimCache(level: Int) {
        if (bitmapCache.size > 20) bitmapCache.clear()
    }
}

class DesktopPreviewCache : PreviewCache {
    private val cache = mutableMapOf<String, PlatformBitmap>()

    override fun isFrameCached(projectId: Long, frameIndex: Int): Boolean {
        return cache.containsKey("$projectId@$frameIndex")
    }

    override fun getCachedFrame(projectId: Long, frameIndex: Int): PlatformBitmap? {
        return cache["$projectId@$frameIndex"]
    }

    override fun putCachedFrame(projectId: Long, frameIndex: Int, bitmap: PlatformBitmap) {
        cache["$projectId@$frameIndex"] = bitmap
        if (cache.size > 100) {
            cache.keys.firstOrNull()?.let { cache.remove(it) }
        }
    }

    override fun invalidateTimeRange(projectId: Long, startTime: Float, endTime: Float, fps: Int) {
        invalidate(projectId)
    }

    override fun invalidate(projectId: Long) {
        val toRemove = cache.keys.filter { it.startsWith("$projectId@") }
        toRemove.forEach { cache.remove(it) }
    }

    override fun clearProject(projectId: Long) {
        invalidate(projectId)
    }
}
