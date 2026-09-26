package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.math.abs

import com.example.model.MediaMetadataInfo
import kotlin.math.abs

/*
data class MediaMetadataInfo(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val rotation: Int,
    val isVideo: Boolean
)
*/

object BitmapCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 4).coerceIn(64 * 1024, 300 * 1024)

    private val _frameUpdateTrigger = MutableStateFlow(0L)
    val frameUpdateTrigger: StateFlow<Long> = _frameUpdateTrigger.asStateFlow()

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            // Safe eviction without manual recycle to avoid race conditions with drawing pipeline
        }
    }

    // Tracks all loaded bucket indices per video URI for lightning-fast closest-neighbor lookup
    private val videoDecodedBuckets = ConcurrentHashMap<String, ConcurrentSkipListSet<Int>>()

    fun get(key: String): Bitmap? = memoryCache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
        if (key.contains("@")) {
            val parts = key.split("@")
            val uriPart = parts[0]
            val bucketPart = parts.getOrNull(1)?.toIntOrNull()
            if (bucketPart != null) {
                val set = videoDecodedBuckets.getOrPut(uriPart) { ConcurrentSkipListSet() }
                set.add(bucketPart)
            }
        }
    }

    fun notifyFrameLoaded() {
        _frameUpdateTrigger.value = System.currentTimeMillis()
    }

    /**
     * Retrieves video frame for [timeSec].
     * If exact frame is not yet rendered, returns the CLOSEST available decoded frame
     * so the preview NEVER flashes back to frame 0 (the first frame)!
     */
    fun getFrame(uriString: String, timeSec: Float): Bitmap? {
        val bucket = (timeSec * 30).toInt() // 30 FPS buckets
        val direct = get("$uriString@$bucket")
        if (direct != null && !direct.isRecycled) return direct

        val bucketSet = videoDecodedBuckets[uriString]
        if (bucketSet != null && bucketSet.isNotEmpty()) {
            // Find closest bucket that is BEFORE current time
            val floor = bucketSet.floor(bucket)
            if (floor != null) {
                val near = get("$uriString@$floor")
                if (near != null && !near.isRecycled) return near
            }
        }

        // search neighbors within +/- 30 frames
        for (offset in 1..30) {
            val prev = get("$uriString@${bucket - offset}")
            if (prev != null && !prev.isRecycled) return prev
            val next = get("$uriString@${bucket + offset}")
            if (next != null && !next.isRecycled) return next
        }

        // Only fallback to primary if no other frame is in memory
        return get(uriString)
    }

    suspend fun loadFrame(
        context: Context,
        uriString: String,
        timeSec: Float,
        targetW: Int = 480,
        targetH: Int = 480
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (uriString.isBlank() || uriString.startsWith("sample://")) return@withContext null
        val bucket = (timeSec * 30).toInt()
        val key = "$uriString@$bucket"
        val cached = get(key)
        if (cached != null && !cached.isRecycled) return@withContext cached

        val uri = try { Uri.parse(uriString) } catch (e: Exception) { return@withContext null }
        val timeUs = (timeSec * 1_000_000L).toLong().coerceAtLeast(0L)
        
        // Fast-path: Check if we have a retriever before attempting long decode
        val frame = extractVideoFrame(context, uri, timeUs, targetW, targetH)
        if (frame != null) {
            put(key, frame)
            // Store as primary thumbnail if none exists
            if (memoryCache.get(uriString) == null) {
                memoryCache.put(uriString, frame)
            }
            _frameUpdateTrigger.value = System.currentTimeMillis()
        }
        frame
    }

    fun clear() {
        videoDecodedBuckets.clear()
        memoryCache.evictAll()
        releaseRetrievers()
    }

    fun trim(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            memoryCache.trimToSize(cacheSize / 2)
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            videoDecodedBuckets.clear()
            memoryCache.evictAll()
            releaseRetrievers()
        }
    }

    suspend fun copyMediaToLocalStorage(context: Context, projectId: Any, uriString: String, isVideo: Boolean): String = withContext(Dispatchers.IO) {
        if (uriString.isBlank() || uriString.startsWith("sample://")) return@withContext uriString
        val uri = try { Uri.parse(uriString) } catch (e: Exception) { return@withContext uriString }

        // If it's already an internal file, reuse
        val filesDirPath = context.filesDir.absolutePath
        if (uri.path?.startsWith(filesDirPath) == true || uriString.startsWith(filesDirPath)) {
            return@withContext uriString
        }

        try {
            val projectMediaDir = File(context.filesDir, "projects/$projectId/media").apply { mkdirs() }
            val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
            val isVid = isVideo || mimeType?.startsWith("video/") == true || uriString.contains("video", ignoreCase = true) || uriString.endsWith(".mp4", ignoreCase = true)
            val ext = when {
                isVid -> "mp4"
                mimeType?.contains("png") == true -> "png"
                mimeType?.contains("jpeg") == true || mimeType?.contains("jpg") == true -> "jpg"
                mimeType?.contains("mp3") == true -> "mp3"
                mimeType?.contains("wav") == true -> "wav"
                mimeType?.contains("aac") == true -> "aac"
                mimeType?.contains("audio") == true -> "m4a"
                else -> if (isVideo) "mp4" else "jpg"
            }
            val destFile = File(projectMediaDir, "media_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0) {
                return@withContext Uri.fromFile(destFile).toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        uriString
    }

    suspend fun getMediaMetadata(context: Context, uriString: String?): MediaMetadataInfo? = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext null
        if (uriString.startsWith("sample://")) {
            return@withContext MediaMetadataInfo(600, 600, 5000L, 0, false)
        }

        val uri = try { Uri.parse(uriString) } catch (e: Exception) { return@withContext null }

        // 1. Try MediaStore Query for Duration if content URI
        if (uri.scheme == "content") {
            try {
                val proj = arrayOf(MediaStore.Video.Media.DURATION, MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT)
                context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                        val wCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                        val hCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                        val dur = if (durCol >= 0) cursor.getLong(durCol) else 0L
                        val w = if (wCol >= 0) cursor.getInt(wCol) else 1280
                        val h = if (hCol >= 0) cursor.getInt(hCol) else 720
                        if (dur > 0) {
                            return@withContext MediaMetadataInfo(
                                width = if (w > 0) w else 1280,
                                height = if (h > 0) h else 720,
                                durationMs = dur,
                                rotation = 0,
                                isVideo = true
                            )
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        // 2. Try MediaMetadataRetriever (video or audio)
        val retriever = MediaMetadataRetriever()
        var durMs = 0L
        var vidW = 0
        var vidH = 0
        var rot = 0
        var isVid = false

        try {
            val cleanPath = when {
                uriString.startsWith("file://") -> uriString.removePrefix("file://")
                uri.scheme == "file" -> uri.path
                uriString.startsWith("/") -> uriString
                else -> null
            }

            if (cleanPath != null && File(cleanPath).exists()) {
                retriever.setDataSource(cleanPath)
            } else if (uri.scheme == "content") {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                    }
                } catch (e1: Exception) {
                    retriever.setDataSource(context, uri)
                }
            } else {
                retriever.setDataSource(context, uri)
            }

            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val hasVideoStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)

            durMs = durStr?.toLongOrNull() ?: 0L
            vidW = widthStr?.toIntOrNull() ?: 0
            vidH = heightStr?.toIntOrNull() ?: 0
            rot = rotStr?.toIntOrNull() ?: 0
            isVid = hasVideoStr != null || vidW > 0

            if (rot == 90 || rot == 270) {
                val tmp = vidW
                vidW = vidH
                vidH = tmp
            }

            if (durMs <= 0L) {
                // Try MediaExtractor fallback for container duration
                val extractor = MediaExtractor()
                try {
                    if (cleanPath != null && File(cleanPath).exists()) {
                        extractor.setDataSource(cleanPath)
                    } else {
                        extractor.setDataSource(context, uri, null)
                    }
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        if (format.containsKey(MediaFormat.KEY_DURATION)) {
                            val d = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                            if (d > durMs) durMs = d
                        }
                    }
                } catch (ignored: Exception) {
                } finally {
                    extractor.release()
                }
            }

            if (durMs > 0L) {
                return@withContext MediaMetadataInfo(
                    width = if (vidW > 0) vidW else 1280,
                    height = if (vidH > 0) vidH else 720,
                    durationMs = durMs,
                    rotation = rot,
                    isVideo = isVid
                )
            }
        } catch (e: Exception) {
            // Fall through to MediaExtractor or Image bounds
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        // 3. Try MediaExtractor fallback for container duration (e.g. MP4, MKV, MP3, AAC)
        try {
            val extractor = android.media.MediaExtractor()
            try {
                val cleanPath = when {
                    uriString.startsWith("file://") -> uriString.removePrefix("file://")
                    uri.scheme == "file" -> uri.path
                    uriString.startsWith("/") -> uriString
                    else -> null
                }
                if (cleanPath != null && File(cleanPath).exists()) {
                    extractor.setDataSource(cleanPath)
                } else if (uri.scheme == "content") {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        extractor.setDataSource(pfd.fileDescriptor)
                    }
                } else {
                    extractor.setDataSource(context, uri, null)
                }

                var maxDurationUs = 0L
                var hasVideoTrack = false
                var trackW = 0
                var trackH = 0

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) hasVideoTrack = true
                    if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                        val d = format.getLong(android.media.MediaFormat.KEY_DURATION)
                        if (d > maxDurationUs) maxDurationUs = d
                    }
                    if (format.containsKey(android.media.MediaFormat.KEY_WIDTH)) {
                        trackW = format.getInteger(android.media.MediaFormat.KEY_WIDTH)
                    }
                    if (format.containsKey(android.media.MediaFormat.KEY_HEIGHT)) {
                        trackH = format.getInteger(android.media.MediaFormat.KEY_HEIGHT)
                    }
                }

                if (maxDurationUs > 0L) {
                    return@withContext MediaMetadataInfo(
                        width = if (trackW > 0) trackW else 1280,
                        height = if (trackH > 0) trackH else 720,
                        durationMs = maxDurationUs / 1000L,
                        rotation = 0,
                        isVideo = hasVideoTrack
                    )
                }
            } finally {
                try { extractor.release() } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {}

        // 4. Try Image Bounds
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    return@withContext MediaMetadataInfo(options.outWidth, options.outHeight, 0L, 0, false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun loadBitmap(context: Context, uriString: String?, isVideo: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext null

        val cached = get(uriString)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }

        if (uriString.startsWith("sample://")) {
            val sampleBitmap = generateSampleTexture(uriString)
            put(uriString, sampleBitmap)
            return@withContext sampleBitmap
        }

        val uri = try { Uri.parse(uriString) } catch (e: Exception) { return@withContext null }

        // Try Video Frame extraction first if isVideo is true or MIME type suggests video
        val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        val likelyVideo = isVideo || mimeType?.startsWith("video/") == true

        if (likelyVideo) {
            val videoBmp = extractVideoFrame(context, uri, 0L)
            if (videoBmp != null) {
                put(uriString, videoBmp)
                return@withContext videoBmp
            }
        }

        // Try standard Image Decoding
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)

                var inSampleSize = 1
                val maxDim = maxOf(options.outWidth, options.outHeight)
                if (maxDim > 1920) {
                    inSampleSize = maxDim / 1920
                }

                context.contentResolver.openInputStream(uri)?.use { stream2 ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inMutable = false
                    }
                    val bitmap = BitmapFactory.decodeStream(stream2, null, decodeOptions)
                    if (bitmap != null) {
                        put(uriString, bitmap)
                        return@withContext bitmap
                    }
                }
            }
        } catch (e: Exception) {
            // Image decode failed
        }

        // Fallback: Try video extraction in case it's a video file without mime header
        val fallbackVideoBmp = extractVideoFrame(context, uri, 0L)
        if (fallbackVideoBmp != null) {
            put(uriString, fallbackVideoBmp)
            return@withContext fallbackVideoBmp
        }

        null
    }

    private val retrieverPool = ConcurrentHashMap<String, java.util.concurrent.ConcurrentLinkedQueue<MediaMetadataRetriever>>()

    private fun acquireRetriever(context: Context, uriString: String, uri: Uri): MediaMetadataRetriever? {
        val queue = retrieverPool.getOrPut(uriString) { java.util.concurrent.ConcurrentLinkedQueue() }
        val pooled = queue.poll()
        if (pooled != null) return pooled

        try {
            val retriever = MediaMetadataRetriever()
            val cleanPath = when {
                uriString.startsWith("file://") -> uriString.removePrefix("file://")
                uri.scheme == "file" -> uri.path
                uriString.startsWith("/") -> uriString
                else -> null
            }

            if (cleanPath != null && File(cleanPath).exists()) {
                retriever.setDataSource(cleanPath)
            } else if (uri.scheme == "content") {
                try {
                    retriever.setDataSource(context, uri)
                } catch (eContent: Exception) {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        retriever.setDataSource(pfd.fileDescriptor)
                    }
                }
            } else {
                retriever.setDataSource(context, uri)
            }
            return retriever
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun releaseRetriever(uriString: String, retriever: MediaMetadataRetriever) {
        val queue = retrieverPool.getOrPut(uriString) { java.util.concurrent.ConcurrentLinkedQueue() }
        if (queue.size < 6) {
            queue.offer(retriever)
        } else {
            try { retriever.release() } catch (ignored: Exception) {}
        }
    }

    fun releaseRetrievers() {
        val keys = retrieverPool.keys().toList()
        for (k in keys) {
            val queue = retrieverPool.remove(k) ?: continue
            while (true) {
                val r = queue.poll() ?: break
                try { r.release() } catch (ignored: Exception) {}
            }
        }
    }

    fun extractVideoFrame(context: Context, uri: Uri, timeUs: Long = 0L, targetW: Int = 480, targetH: Int = 480): Bitmap? {
        val uriString = uri.toString()
        val retriever = acquireRetriever(context, uriString, uri) ?: return null
        try {
            var frame: Bitmap? = null
            
            // OPTIMIZATION: Start with fast PREVIOUS_SYNC keyframe search.
            // This is 10x-50x faster than CLOSEST for preview purposes.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    frame = retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC, targetW, targetH)
                } catch (ignored: Exception) {}
            }
            
            if (frame == null) {
                try {
                    frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)
                } catch (ignored: Exception) {}
            }

            if (frame == null) {
                frame = retriever.frameAtTime
            }
            return frame
        } catch (e: Exception) {
            Log.e("BitmapCache", "Extraction failed for $uriString: ${e.message}")
            return null
        } finally {
            releaseRetriever(uriString, retriever)
        }
    }

    private fun generateSampleTexture(key: String): Bitmap {
        val size = 600
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (key) {
            "sample://cyberpunk" -> {
                paint.shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    intArrayOf(Color.parseColor("#FF007F"), Color.parseColor("#7928CA"), Color.parseColor("#00F0FF")),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            }
            "sample://emerald" -> {
                paint.shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    intArrayOf(Color.parseColor("#00F5A0"), Color.parseColor("#00D9F5"), Color.parseColor("#0F2027")),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            }
            "sample://sunset" -> {
                paint.shader = LinearGradient(
                    0f, 0f, 0f, size.toFloat(),
                    intArrayOf(Color.parseColor("#FA709A"), Color.parseColor("#FEE140")),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            }
            else -> {
                paint.shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    intArrayOf(Color.parseColor("#4A00E0"), Color.parseColor("#8E2DE2")),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            }
        }
        return bitmap
    }
}
