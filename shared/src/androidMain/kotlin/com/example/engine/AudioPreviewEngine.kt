package com.example.engine

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.example.model.Layer
import com.example.model.LayerType
import java.io.File

class AudioPreviewEngine(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun onPlay(startTime: Float, layers: List<Layer>) {
        val audioLayer = layers.firstOrNull { 
            (it.type == LayerType.AUDIO || it.type == LayerType.VIDEO) && 
            !it.mediaUri.isNullOrBlank() && 
            it.isActiveAt(startTime) 
        } ?: return

        try {
            release()
            mediaPlayer = MediaPlayer().apply {
                val uriStr = audioLayer.mediaUri!!
                if (uriStr.startsWith("/") || uriStr.startsWith("file://")) {
                    val path = if (uriStr.startsWith("file://")) uriStr.removePrefix("file://") else uriStr
                    setDataSource(path)
                } else {
                    setDataSource(context, Uri.parse(uriStr))
                }
                prepare()
                seekTo(((startTime - audioLayer.startTime) * 1000).toInt().coerceAtLeast(0))
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onPause() {
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {}
    }

    fun onSeek(time: Float, layers: List<Layer>) {
        if (mediaPlayer?.isPlaying == true) {
            onPlay(time, layers)
        } else {
            val audioLayer = layers.firstOrNull { 
                (it.type == LayerType.AUDIO || it.type == LayerType.VIDEO) && 
                !it.mediaUri.isNullOrBlank() && 
                it.isActiveAt(time) 
            }
            if (audioLayer != null) {
                try {
                    mediaPlayer?.seekTo(((time - audioLayer.startTime) * 1000).toInt().coerceAtLeast(0))
                } catch (e: Exception) {}
            }
        }
    }

    fun release() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
    }
}
