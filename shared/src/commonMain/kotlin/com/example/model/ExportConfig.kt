package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportConfig(
    val width: Int = 720,
    val height: Int = 1280,
    val fps: Int = 30,
    val bitRate: Int = 6_000_000
)
