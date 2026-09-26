package com.example.util

import com.example.model.Layer
import com.example.model.Project

actual object PlatformExporter {
    actual fun startExport(
        project: Project,
        layers: List<Layer>,
        width: Int,
        height: Int,
        fps: Int
    ) {
        println("Export triggered on Desktop for project: ${project.title}")
        // Desktop export integration could go here
    }
}
