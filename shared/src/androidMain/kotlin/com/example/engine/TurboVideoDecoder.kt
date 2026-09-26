package com.example.engine

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Fusion 2 Turbo: High-performance hardware video decoder.
 * Uses MediaCodec to decode frames directly into a SurfaceTexture (OES Texture).
 * This eliminates Bitmap allocations and CPU-to-GPU copies.
 */
class TurboVideoDecoder(
    private val context: Context,
    private val uriString: String
) {
    private var extractor: MediaExtractor? = null
    private var decoder: MediaCodec? = null
    private var surface: Surface? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var textureId: Int = 0
    
    private var outputFormat: MediaFormat? = null
    private var width: Int = 0
    private var height: Int = 0
    private var durationUs: Long = 0
    private var lastDecodedTimeUs: Long = -1

    private val bufferInfo = MediaCodec.BufferInfo()
    private var isEos = false

    private val frameAvailableSemaphore = Semaphore(0)

    fun init(oesTextureId: Int) {
        this.textureId = oesTextureId
        surfaceTexture = SurfaceTexture(oesTextureId)
        surfaceTexture?.setOnFrameAvailableListener {
            frameAvailableSemaphore.release()
        }
        surface = Surface(surfaceTexture)

        try {
            extractor = MediaExtractor()
            val uri = Uri.parse(uriString)
            if (uriString.startsWith("file://") || uriString.startsWith("/")) {
                val path = if (uriString.startsWith("file://")) uriString.removePrefix("file://") else uriString
                extractor?.setDataSource(path)
            } else {
                extractor?.setDataSource(context, uri, null)
            }

            val trackIndex = selectVideoTrack(extractor!!)
            if (trackIndex < 0) throw RuntimeException("No video track found in $uriString")
            
            extractor?.selectTrack(trackIndex)
            val format = extractor?.getTrackFormat(trackIndex)!!
            width = format.getInteger(MediaFormat.KEY_WIDTH)
            height = format.getInteger(MediaFormat.KEY_HEIGHT)
            durationUs = format.getLong(MediaFormat.KEY_DURATION)
            
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            decoder = MediaCodec.createDecoderByType(mime)
            decoder?.configure(format, surface, null, 0)
            decoder?.start()
            
            Log.d("TurboVideoDecoder", "Initialized for $width x $height, duration: ${durationUs/1000}ms")
        } catch (e: Exception) {
            Log.e("TurboVideoDecoder", "Failed to init decoder: ${e.message}")
            release()
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return i
        }
        return -1
    }

    /**
     * Seeks and decodes the frame closest to [timeUs].
     * Returns true if a new frame is available on the SurfaceTexture.
     */
    fun seekToFrame(timeUs: Long): Boolean {
        val decoder = decoder ?: return false
        val extractor = extractor ?: return false

        // Fusion 2 Ultra: Linear Decoding Optimization
        val isSequential = lastDecodedTimeUs != -1L && timeUs >= lastDecodedTimeUs && timeUs < lastDecodedTimeUs + 50000
        
        if (!isSequential) {
            extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            decoder.flush()
            isEos = false
            lastDecodedTimeUs = -1L
        }

        var tries = 0
        while (tries < 15) { // Reduced retries for faster feedback
            tries++
            
            if (!isEos) {
                val inputIndex = decoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEos = true
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIndex >= 0) {
                val presentationTimeUs = bufferInfo.presentationTimeUs
                
                // If this frame is before our target, drop it and continue decoding
                // Unless it's the last available frame
                if (presentationTimeUs < timeUs - 10000 && !isEos) {
                    decoder.releaseOutputBuffer(outputIndex, false)
                    continue
                }

                // Found a suitable frame!
                decoder.releaseOutputBuffer(outputIndex, true)
                lastDecodedTimeUs = presentationTimeUs
                
                // Fusion 2 Ultra: Wait for the frame to be latched by the GPU
                // Use a Semaphore instead of Thread.sleep for zero-latency sync.
                try {
                    val acquired = frameAvailableSemaphore.tryAcquire(250, TimeUnit.MILLISECONDS)
                    if (acquired) {
                        surfaceTexture?.updateTexImage()
                    } else {
                        Log.w("TurboVideoDecoder", "Timed out waiting for frame latch")
                    }
                } catch (e: Exception) {
                    Log.e("TurboVideoDecoder", "Sync error: ${e.message}")
                }
                return true
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputFormat = decoder.outputFormat
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (isEos) break
            }
        }
        return false
    }

    fun release() {
        try {
            decoder?.stop()
            decoder?.release()
        } catch (e: Exception) {}
        decoder = null

        extractor?.release()
        extractor = null

        surface?.release()
        surface = null
        
        surfaceTexture?.release()
        surfaceTexture = null
    }
}
