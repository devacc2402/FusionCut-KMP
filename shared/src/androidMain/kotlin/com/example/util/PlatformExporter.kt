package com.example.util

import com.example.model.ExportConfig
import com.example.model.Layer
import com.example.model.Project
import com.example.service.ExportManager

actual object PlatformExporter {
    actual fun startExport(
        project: Project,
        layers: List<Layer>,
        width: Int,
        height: Int,
        fps: Int
    ) {
        val context = AndroidServices.context ?: return
        val config = ExportConfig(
            width = width,
            height = height,
            fps = fps
        )
        ExportManager.startExport(context, project, layers, config)
    }
}
