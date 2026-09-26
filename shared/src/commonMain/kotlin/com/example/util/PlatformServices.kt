package com.example.util

import com.example.model.Layer
import com.example.model.MediaMetadataInfo
import com.example.model.Project
import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {
    fun onPlay(startTime: Float, layers: List<Layer>)
    fun onPause()
    fun onSeek(time: Float, layers: List<Layer>)
    fun release()
}

interface MediaProvider {
    suspend fun getMediaMetadata(uriString: String): MediaMetadataInfo?
    suspend fun copyMediaToLocalStorage(projectId: Long, uriString: String, isVideo: Boolean): String
    suspend fun loadBitmap(uriString: String, isVideo: Boolean = false): PlatformBitmap?
    suspend fun loadFrame(uriString: String, timeSec: Float, targetW: Int = 640, targetH: Int = 640): PlatformBitmap?
    fun clearCache()
    fun trimCache(level: Int)
}

interface PreviewCache {
    fun isFrameCached(projectId: Long, frameIndex: Int): Boolean
    fun getCachedFrame(projectId: Long, frameIndex: Int): PlatformBitmap?
    fun putCachedFrame(projectId: Long, frameIndex: Int, bitmap: PlatformBitmap)
    fun invalidateTimeRange(projectId: Long, startTime: Float, endTime: Float, fps: Int)
    fun invalidate(projectId: Long)
    fun clearProject(projectId: Long)
}
