package com.example.util

import android.content.Context
import com.example.model.Layer
import com.example.model.MediaMetadataInfo
import com.example.engine.AudioPreviewEngine
import com.example.engine.BitmapCache
import com.example.engine.PreviewCacheEngine
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object AndroidServices {
    var context: Context? = null
}

class AndroidPlatformBitmap(val bitmap: android.graphics.Bitmap) : PlatformBitmap {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override fun asImageBitmap(): ImageBitmap = bitmap.asImageBitmap()
    
    override fun getPixels(pixels: IntArray) {
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    }

    override fun setPixels(pixels: IntArray) {
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    override fun isRecycled(): Boolean = bitmap.isRecycled
}

class AndroidAudioPlayer(context: Context) : AudioPlayer {
    private val engine = AudioPreviewEngine(context)
    override fun onPlay(startTime: Float, layers: List<Layer>) = engine.onPlay(startTime, layers)
    override fun onPause() = engine.onPause()
    override fun onSeek(time: Float, layers: List<Layer>) = engine.onSeek(time, layers)
    override fun release() = engine.release()
}

class AndroidMediaProvider(val context: Context) : MediaProvider {
    override suspend fun getMediaMetadata(uriString: String): MediaMetadataInfo? {
        return BitmapCache.getMediaMetadata(context, uriString)
    }

    override suspend fun copyMediaToLocalStorage(projectId: Long, uriString: String, isVideo: Boolean): String {
        return BitmapCache.copyMediaToLocalStorage(context, projectId, uriString, isVideo)
    }

    override suspend fun loadBitmap(uriString: String, isVideo: Boolean): PlatformBitmap? {
        return BitmapCache.loadBitmap(context, uriString, isVideo)?.let { AndroidPlatformBitmap(it) }
    }

    override suspend fun loadFrame(uriString: String, timeSec: Float, targetW: Int, targetH: Int): PlatformBitmap? {
        return BitmapCache.loadFrame(context, uriString, timeSec, targetW, targetH)?.let { AndroidPlatformBitmap(it) }
    }

    override fun clearCache() = BitmapCache.clear()
    override fun trimCache(level: Int) = BitmapCache.trim(level)
}

class AndroidPreviewCache : PreviewCache {
    override fun isFrameCached(projectId: Long, frameIndex: Int): Boolean {
        return PreviewCacheEngine.isFrameCached(projectId, frameIndex)
    }

    override fun getCachedFrame(projectId: Long, frameIndex: Int): PlatformBitmap? {
        return PreviewCacheEngine.getCachedFrame(projectId, frameIndex)?.let { AndroidPlatformBitmap(it) }
    }

    override fun putCachedFrame(projectId: Long, frameIndex: Int, bitmap: PlatformBitmap) {
        if (bitmap is AndroidPlatformBitmap) {
            PreviewCacheEngine.putCachedFrame(projectId, frameIndex, bitmap.bitmap)
        }
    }

    override fun invalidateTimeRange(projectId: Long, startTime: Float, endTime: Float, fps: Int) {
        PreviewCacheEngine.invalidateTimeRange(projectId, startTime, endTime, fps)
    }

    override fun invalidate(projectId: Long) {
        PreviewCacheEngine.invalidate(projectId)
    }

    override fun clearProject(projectId: Long) {
        PreviewCacheEngine.clearProject(projectId)
    }
}
