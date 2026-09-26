package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

import kotlinx.serialization.Serializable

@Entity(tableName = "projects")
@Serializable
data class Project(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "Untitled Project",
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 60,
    val durationSeconds: Float = 5.0f,
    val backgroundColor: Long = 0xFF0A0D14, // Obsidian dark background
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val renderEngine: RenderEngineType = RenderEngineType.FUSION_2
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 1f

    val totalFrames: Int
        get() = (durationSeconds * fps).toInt()
}
