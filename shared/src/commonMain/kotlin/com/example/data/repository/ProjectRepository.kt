package com.example.data.repository

import com.example.data.db.LayerDao
import com.example.data.db.ProjectDao
import com.example.model.Layer
import com.example.model.LayerType
import com.example.model.Project
import com.example.model.RenderEngineType
import com.example.model.ShapeType
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val layerDao: LayerDao
) {
    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()

    fun getProject(id: Long): Flow<Project?> = projectDao.getProjectById(id)

    suspend fun getProjectDirect(id: Long): Project? = projectDao.getProjectDirect(id)

    fun getLayers(projectId: Long): Flow<List<Layer>> = layerDao.getLayersForProject(projectId)

    suspend fun getLayersDirect(projectId: Long): List<Layer> = layerDao.getLayersDirect(projectId)

    suspend fun createProject(
        title: String,
        width: Int,
        height: Int,
        fps: Int = 60,
        durationSeconds: Float = 5.0f,
        backgroundColor: Long = 0xFF0A0D14,
        renderEngine: RenderEngineType = RenderEngineType.FUSION_2
    ): Long {
        return try {
            val project = Project(
                title = title,
                width = width,
                height = height,
                fps = fps,
                durationSeconds = durationSeconds,
                backgroundColor = backgroundColor,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                renderEngine = renderEngine
            )
            val projectId = projectDao.insertProject(project)

            // Seed a clean default shape & text layer for initial polish
            val shapeLayer = Layer(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                name = "Neon Diamond",
                type = LayerType.SHAPE,
                orderIndex = 0,
                startTime = 0.0f,
                endTime = durationSeconds,
                shapeType = ShapeType.ROUNDED_RECT,
                fillColor = 0xFF00F0FF,
                strokeColor = 0xFF00E599,
                strokeWidth = 4.0f,
                cornerRadius = 36.0f,
                baseWidth = 280.0f,
                baseHeight = 280.0f,
                rotation = 45.0f,
                positionX = 0f,
                positionY = -60f
            )

            val textLayer = Layer(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                name = "Title Typography",
                type = LayerType.TEXT,
                orderIndex = 1,
                startTime = 0.0f,
                endTime = durationSeconds,
                text = "Fusion Cut",
                fontSize = 48.0f,
                textColor = 0xFFFFFFFF,
                isBold = true,
                positionX = 0f,
                positionY = 160f
            )

            layerDao.insertLayers(listOf(shapeLayer, textLayer))
            projectId
        } catch (e: Throwable) {
            println("ProjectRepository.createProject ERROR: ${e.message}")
            e.printStackTrace()
            try {
                File("crash_stacktrace.txt").writeText("CREATE PROJECT ERROR:\n" + e.stackTraceToString())
            } catch (ex: Exception) {
                // Ignore
            }
            -1L
        }
    }

    suspend fun duplicateProject(projectId: Long): Long {
        val original = projectDao.getProjectDirect(projectId) ?: return -1
        val newProject = original.copy(
            id = 0,
            title = "${original.title} (Copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val newProjectId = projectDao.insertProject(newProject)
        val layers = layerDao.getLayersDirect(projectId)
        val duplicatedLayers = layers.map { layer ->
            layer.copy(
                id = UUID.randomUUID().toString(),
                projectId = newProjectId
            )
        }
        layerDao.insertLayers(duplicatedLayers)
        return newProjectId
    }

    suspend fun updateProject(project: Project) {
        projectDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(projectId: Long) {
        layerDao.deleteAllLayersForProject(projectId)
        projectDao.deleteProjectById(projectId)
    }

    suspend fun saveLayer(layer: Layer) {
        layerDao.insertLayer(layer)
        touchProject(layer.projectId)
    }

    suspend fun saveLayers(layers: List<Layer>) {
        layerDao.insertLayers(layers)
        if (layers.isNotEmpty()) {
            touchProject(layers.first().projectId)
        }
    }

    suspend fun replaceAllLayers(projectId: Long, layers: List<Layer>) {
        layerDao.replaceLayersForProject(projectId, layers)
        touchProject(projectId)
    }

    suspend fun deleteLayer(layerId: String, projectId: Long) {
        layerDao.deleteLayerById(layerId)
        touchProject(projectId)
    }

    private suspend fun touchProject(projectId: Long) {
        val project = projectDao.getProjectDirect(projectId)
        if (project != null) {
            projectDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}
