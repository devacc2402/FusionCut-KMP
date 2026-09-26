package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet

/**
 * High-performance RAM + Disk Tiered Preview Cache Engine (After Effects / NodeVideo style).
 * 
 * Guarantees:
 * 1. The ENTIRE timeline rendered in preview stays rendered (in RAM + fast session disk cache)
 *    until the app or project is closed.
 * 2. When changes are made in a specific area (layer edit, trim, move), ONLY that specific
 *    time area is invalidated and becomes unrendered while the rest of the timeline stays rendered!
 * 3. Replaying or scrubbing rendered frames is instantaneous (60 FPS fluid replay).
 */
object PreviewCacheEngine {
    private var appContext: Context? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    // Allocate up to 30% of available JVM memory for hot RAM preview cache
    private val cacheSizeKb = (maxMemoryKb / 3).coerceIn(64 * 1024, 384 * 1024)

    private val compositedFrameCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    // Tracks cached frame indices per project: projectId -> Set of cached frame indices
    private val projectCachedFrames = ConcurrentHashMap<String, ConcurrentSkipListSet<Int>>()

    private val _cacheUpdateTrigger = MutableStateFlow(0L)
    val cacheUpdateTrigger: StateFlow<Long> = _cacheUpdateTrigger.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun getProjectDir(projectId: Any): File? {
        val ctx = appContext ?: return null
        val dir = File(ctx.cacheDir, "preview_ram_cache/${projectId}")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun makeKey(projectId: Any, frameIndex: Int): String {
        return "$projectId#$frameIndex"
    }

    private fun getFrameFile(projectId: Any, frameIndex: Int): File? {
        val dir = getProjectDir(projectId) ?: return null
        return File(dir, "f_$frameIndex.webp")
    }

    fun isFrameCached(projectId: Any, frameIndex: Int): Boolean {
        val key = makeKey(projectId, frameIndex)
        val inRam = compositedFrameCache.get(key)
        if (inRam != null && !inRam.isRecycled) return true

        val idStr = projectId.toString()
        val set = projectCachedFrames[idStr]
        if (set != null && set.contains(frameIndex)) return true

        val file = getFrameFile(projectId, frameIndex)
        if (file != null && file.exists() && file.length() > 0) {
            projectCachedFrames.getOrPut(idStr) { ConcurrentSkipListSet() }.add(frameIndex)
            return true
        }
        return false
    }

    fun getCachedFrame(projectId: Any, frameIndex: Int): Bitmap? {
        val key = makeKey(projectId, frameIndex)
        val ramBmp = compositedFrameCache.get(key)
        if (ramBmp != null && !ramBmp.isRecycled) {
            return ramBmp
        }

        // Check persistent session disk cache
        val file = getFrameFile(projectId, frameIndex)
        if (file != null && file.exists() && file.length() > 0) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = false
                }
                val diskBmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (diskBmp != null && !diskBmp.isRecycled) {
                    compositedFrameCache.put(key, diskBmp)
                    projectCachedFrames.getOrPut(projectId.toString()) { ConcurrentSkipListSet() }.add(frameIndex)
                    return diskBmp
                }
            } catch (e: Exception) {
                // Disk read error
            }
        }

        return null
    }

    fun putCachedFrame(projectId: Any, frameIndex: Int, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val key = makeKey(projectId, frameIndex)
        compositedFrameCache.put(key, bitmap)

        val idStr = projectId.toString()
        val set = projectCachedFrames.getOrPut(idStr) { ConcurrentSkipListSet() }
        set.add(frameIndex)
        _cacheUpdateTrigger.value = System.currentTimeMillis()

        // Persist frame to session disk cache asynchronously
        val file = getFrameFile(projectId, frameIndex) ?: return
        val bmpCopy = if (!bitmap.isRecycled) {
            try {
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (e: Exception) {
                null
            }
        } else null

        if (bmpCopy != null) {
            ioScope.launch {
                try {
                    FileOutputStream(file).use { out ->
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            bmpCopy.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, out)
                        } else {
                            @Suppress("DEPRECATION")
                            bmpCopy.compress(Bitmap.CompressFormat.WEBP, 90, out)
                        }
                    }
                } catch (ignored: Exception) {
                } finally {
                    try { bmpCopy.recycle() } catch (ignored: Exception) {}
                }
            }
        }
    }

    /**
     * Area-Specific Invalidation:
     * When changes are made in a specific time range, ONLY that area becomes unrendered.
     * All other rendered frames outside this range remain rendered and cached!
     */
    fun invalidateTimeRange(projectId: Any, startTime: Float, endTime: Float, fps: Int) {
        val idStr = projectId.toString()
        val effectiveFps = fps.coerceIn(15, 60)
        val startFrame = ((startTime - 0.05f) * effectiveFps).toInt().coerceAtLeast(0)
        val endFrame = ((endTime + 0.05f) * effectiveFps + 1).toInt()

        val set = projectCachedFrames[idStr]
        val dir = getProjectDir(projectId)

        for (f in startFrame..endFrame) {
            compositedFrameCache.remove(makeKey(projectId, f))
            set?.remove(f)
        }

        ioScope.launch {
            if (dir != null && dir.exists()) {
                for (f in startFrame..endFrame) {
                    try {
                        val file = File(dir, "f_$f.webp")
                        if (file.exists()) file.delete()
                    } catch (ignored: Exception) {}
                }
            }
        }

        _cacheUpdateTrigger.value = System.currentTimeMillis()
    }

    /**
     * Invalidate entire project (used on project global settings change e.g. background color or resolution)
     */
    fun invalidate(projectId: Any) {
        val idStr = projectId.toString()
        projectCachedFrames.remove(idStr)

        val snapshot = compositedFrameCache.snapshot()
        for (k in snapshot.keys) {
            if (k.startsWith("$idStr#")) {
                compositedFrameCache.remove(k)
            }
        }

        val dir = getProjectDir(projectId)
        ioScope.launch {
            try {
                dir?.deleteRecursively()
            } catch (ignored: Exception) {}
        }

        _cacheUpdateTrigger.value = System.currentTimeMillis()
    }

    /**
     * Clean up when a project is closed or the ViewModel is cleared.
     */
    fun clearProject(projectId: Any) {
        invalidate(projectId)
    }

    fun clear() {
        projectCachedFrames.clear()
        compositedFrameCache.evictAll()
        val ctx = appContext
        if (ctx != null) {
            ioScope.launch {
                try {
                    File(ctx.cacheDir, "preview_ram_cache").deleteRecursively()
                } catch (ignored: Exception) {}
            }
        }
        _cacheUpdateTrigger.value = System.currentTimeMillis()
    }

    fun getCachedTimeRanges(projectId: Any, fps: Int): List<Pair<Float, Float>> {
        val idStr = projectId.toString()
        val set = projectCachedFrames[idStr] ?: return emptyList()
        if (set.isEmpty()) return emptyList()

        val sortedIndices = set.filter { isFrameCached(projectId, it) }.sorted()
        if (sortedIndices.isEmpty()) return emptyList()

        val ranges = mutableListOf<Pair<Float, Float>>()
        val frameDuration = 1.0f / fps.coerceAtLeast(1)

        var startIdx = sortedIndices.first()
        var prevIdx = startIdx

        for (i in 1 until sortedIndices.size) {
            val curr = sortedIndices[i]
            if (curr == prevIdx + 1) {
                prevIdx = curr
            } else {
                ranges.add(Pair(startIdx * frameDuration, (prevIdx + 1) * frameDuration))
                startIdx = curr
                prevIdx = curr
            }
        }
        ranges.add(Pair(startIdx * frameDuration, (prevIdx + 1) * frameDuration))
        return ranges
    }
}
