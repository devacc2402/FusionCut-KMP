package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// import com.example.data.db.AppDatabase
import com.example.data.repository.ProjectRepository
import com.example.model.Project
import com.example.model.ProjectAspectRatio
import com.example.model.RenderEngineType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(private val repository: ProjectRepository) : ViewModel() {

    val allProjects: StateFlow<List<Project>>

    private val _selectedFilter = MutableStateFlow("ALL")
    val selectedFilter = _selectedFilter.asStateFlow()

    init {
        // val db = AppDatabase.getDatabase(application)
        // repository = ProjectRepository(db.projectDao(), db.layerDao())
        allProjects = repository.allProjects.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun createProject(
        title: String,
        aspectRatio: ProjectAspectRatio,
        fps: Int = 60,
        durationSeconds: Float = 5.0f,
        backgroundColor: Long = 0xFF0A0D14,
        renderEngine: RenderEngineType = RenderEngineType.FUSION_2,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val finalTitle = if (title.isBlank()) "Project ${System.currentTimeMillis() % 1000}" else title.trim()
            val id = repository.createProject(
                title = finalTitle,
                width = aspectRatio.defaultWidth,
                height = aspectRatio.defaultHeight,
                fps = fps,
                durationSeconds = durationSeconds,
                backgroundColor = backgroundColor,
                renderEngine = renderEngine
            )
            onCreated(id)
        }
    }

    fun duplicateProject(projectId: Long) {
        viewModelScope.launch {
            repository.duplicateProject(projectId)
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }

    fun renameProject(project: Project, newTitle: String) {
        viewModelScope.launch {
            if (newTitle.isNotBlank()) {
                repository.updateProject(project.copy(title = newTitle.trim()))
            }
        }
    }
}
