package com.example.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.model.ExportConfig
import com.example.model.Layer
import com.example.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object ExportManager {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    var currentRequest: ExportRequest? = null

    fun startExport(
        context: Context,
        project: Project,
        layers: List<Layer>,
        config: ExportConfig
    ) {
        currentRequest = ExportRequest(project, layers, config)
        _exportState.value = ExportState.Exporting(
            projectId = project.id,
            projectTitle = project.title,
            progress = 0f,
            currentFrame = 0,
            totalFrames = (project.durationSeconds * config.fps).toInt().coerceAtLeast(1)
        )

        try {
            // Use fully qualified name for the service to avoid ambiguity
            val intent = Intent().setClassName(context.packageName, "com.example.service.VideoExportService").apply {
                action = "com.example.service.action.START_EXPORT"
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _exportState.value = ExportState.Error(project.id, "Failed to start export service: ${e.message}")
        }
    }

    fun cancelExport(context: Context) {
        val intent = Intent().setClassName(context.packageName, "com.example.service.VideoExportService").apply {
            action = "com.example.service.action.CANCEL_EXPORT"
        }
        context.startService(intent)
        _exportState.value = ExportState.Idle
        currentRequest = null
    }

    fun updateProgress(projectId: Long, projectTitle: String, progress: Float, currentFrame: Int, totalFrames: Int) {
        _exportState.value = ExportState.Exporting(
            projectId = projectId,
            projectTitle = projectTitle,
            progress = progress,
            currentFrame = currentFrame,
            totalFrames = totalFrames
        )
    }

    fun notifyCompleted(projectId: Long, projectTitle: String, file: File, savedPath: String? = null) {
        _exportState.value = ExportState.Completed(
            projectId = projectId,
            projectTitle = projectTitle,
            filePath = file.absolutePath,
            fileName = file.name,
            savedToDownloadsPath = savedPath
        )
        currentRequest = null
    }

    fun notifyError(projectId: Long, error: String) {
        _exportState.value = ExportState.Error(
            projectId = projectId,
            message = error
        )
        currentRequest = null
    }

    fun clearState() {
        _exportState.value = ExportState.Idle
        currentRequest = null
    }
}
