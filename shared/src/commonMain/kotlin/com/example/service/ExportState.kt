package com.example.service

import com.example.model.ExportConfig
import com.example.model.Layer
import com.example.model.Project

sealed interface ExportState {
    data object Idle : ExportState
    data class Exporting(
        val projectId: Long,
        val projectTitle: String,
        val progress: Float,
        val currentFrame: Int,
        val totalFrames: Int
    ) : ExportState
    data class Completed(
        val projectId: Long,
        val projectTitle: String,
        val filePath: String,
        val fileName: String,
        val savedToDownloadsPath: String? = null
    ) : ExportState
    data class Error(
        val projectId: Long,
        val message: String
    ) : ExportState
}

data class ExportRequest(
    val project: Project,
    val layers: List<Layer>,
    val config: ExportConfig
)
