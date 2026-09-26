package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class VideoExportService : Service() {

    companion object {
        const val ACTION_START_EXPORT = "com.example.service.action.START_EXPORT"
        const val ACTION_CANCEL_EXPORT = "com.example.service.action.CANCEL_EXPORT"
        const val NOTIFICATION_ID_PROGRESS = 1001
        const val NOTIFICATION_ID_COMPLETE = 1002

        const val CHANNEL_PROGRESS_ID = "video_export_progress"
        const val CHANNEL_COMPLETE_ID = "video_export_complete"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var exportJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FusionCut:VideoExportWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EXPORT -> {
                val request = ExportManager.currentRequest
                if (request != null) {
                    startForegroundExport(request)
                } else {
                    stopSelf()
                }
            }
            ACTION_CANCEL_EXPORT -> {
                cancelExport()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundExport(request: ExportRequest) {
        wakeLock?.acquire(30 * 60 * 1000L) // 30 mins max timeout

        val initialNotification = buildProgressNotification(
            projectTitle = request.project.title,
            progressPercent = 0,
            currentFrame = 0,
            totalFrames = (request.project.durationSeconds * request.config.fps).toInt().coerceAtLeast(1)
        )
        startForeground(NOTIFICATION_ID_PROGRESS, initialNotification)

        exportJob = serviceScope.launch {
            var lastUpdateMs = 0L
            var lastPercent = -1
            
            // Fusion 2 Ultra: Smooth Progress Interpolator
            var targetProgress = 0f
            var displayedProgress = 0f
            
            val progressJob = launch {
                while (isActive) {
                    if (displayedProgress < targetProgress) {
                        displayedProgress += (targetProgress - displayedProgress) * 0.1f // Ease towards target
                        if (targetProgress - displayedProgress < 0.001f) displayedProgress = targetProgress
                    }
                    
                    val percent = (displayedProgress * 100).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        ExportManager.updateProgress(
                            projectId = request.project.id,
                            projectTitle = request.project.title,
                            progress = displayedProgress,
                            currentFrame = (displayedProgress * (request.project.durationSeconds * request.config.fps)).toInt(),
                            totalFrames = (request.project.durationSeconds * request.config.fps).toInt()
                        )
                    }
                    delay(16) // 60fps UI updates
                }
            }

            try {
                // Stub export file creation until Step 2 connects new render engine
                val exportDir = File(applicationContext.filesDir, "exports").apply { mkdirs() }
                val outputFile = File(exportDir, "export_${request.project.id}_${System.currentTimeMillis()}.mp4").apply { createNewFile() }
                targetProgress = 1.0f

                progressJob.cancel()
                // Ensure 100% at end
                ExportManager.updateProgress(request.project.id, request.project.title, 1.0f, 0, 0)

                // Save to downloads
                val savedDownloadPath = saveVideoToPublicStorage(applicationContext, outputFile)

                ExportManager.notifyCompleted(
                    projectId = request.project.id,
                    projectTitle = request.project.title,
                    file = outputFile,
                    savedPath = savedDownloadPath
                )

                // Show completed notification
                val completeNotification = buildCompletedNotification(
                    projectTitle = request.project.title,
                    filePath = outputFile.absolutePath,
                    fileName = outputFile.name
                )
                notificationManager.notify(NOTIFICATION_ID_COMPLETE, completeNotification)

            } catch (e: Exception) {
                e.printStackTrace()
                com.example.service.ExportManager.notifyError(
                    projectId = request.project.id,
                    error = e.localizedMessage ?: "Export failed"
                )

                val errorNotification = NotificationCompat.Builder(applicationContext, CHANNEL_COMPLETE_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("Video Export Failed")
                    .setContentText(e.localizedMessage ?: "Unknown rendering error occurred")
                    .setAutoCancel(true)
                    .build()
                notificationManager.notify(NOTIFICATION_ID_COMPLETE, errorNotification)
            } finally {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun cancelExport() {
        exportJob?.cancel()
        com.example.service.ExportManager.clearState()
        notificationManager.cancel(NOTIFICATION_ID_PROGRESS)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildProgressNotification(
        projectTitle: String,
        progressPercent: Int,
        currentFrame: Int,
        totalFrames: Int
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, VideoExportService::class.java).apply {
            action = ACTION_CANCEL_EXPORT
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_PROGRESS_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Exporting Video • $projectTitle")
            .setContentText("$progressPercent% completed (Frame $currentFrame / $totalFrames)")
            .setSubText("$progressPercent%")
            .setProgress(100, progressPercent, false)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun buildCompletedNotification(
        projectTitle: String,
        filePath: String,
        fileName: String
    ): Notification {
        val file = File(filePath)
        val uri = try {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            if (uri != null) {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val viewPendingIntent = PendingIntent.getActivity(
            this,
            2,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val sharePendingIntent = PendingIntent.getActivity(
            this,
            3,
            Intent.createChooser(shareIntent, "Share Video"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_COMPLETE_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Video Export Complete!")
            .setContentText("$fileName is ready • Tap to play")
            .setAutoCancel(true)
            .setContentIntent(viewPendingIntent)
            .addAction(android.R.drawable.ic_menu_share, "Share", sharePendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    private fun saveVideoToPublicStorage(context: Context, sourceFile: File): String? {
        try {
            val fileName = "FusionCut_${System.currentTimeMillis()}.mp4"
            val resolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FusionCut")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                var uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) {
                    val movieValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FusionCut")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, movieValues)
                }

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    return "Saved to Downloads/FusionCut/$fileName"
                }
            } else {
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "FusionCut"
                ).apply { mkdirs() }
                val destination = File(downloadsDir, fileName)
                sourceFile.copyTo(destination, overwrite = true)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destination.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                return destination.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Video Export Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time video export progress and frame encoding"
                setShowBadge(false)
            }

            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE_ID,
                "Video Export Completed",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when video rendering and export finishes"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(completeChannel)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (ignored: Exception) {}
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
