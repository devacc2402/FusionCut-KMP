package com.example.util

import com.example.model.Layer
import com.example.model.Project

expect object PlatformExporter {
    fun startExport(
        project: Project,
        layers: List<Layer>,
        width: Int,
        height: Int,
        fps: Int
    )
}
