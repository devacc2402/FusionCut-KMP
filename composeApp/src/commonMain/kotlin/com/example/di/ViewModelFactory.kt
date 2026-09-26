package com.example.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.data.repository.ProjectRepository
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.ProjectsViewModel
import kotlin.reflect.KClass

class KmpViewModelFactory(
    private val repository: ProjectRepository,
    private val audioPlayer: com.example.util.AudioPlayer? = null,
    private val mediaProvider: com.example.util.MediaProvider? = null,
    private val previewCache: com.example.util.PreviewCache? = null,
    private val projectId: Long = -1
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
        return when (modelClass) {
            ProjectsViewModel::class -> ProjectsViewModel(repository) as T
            EditorViewModel::class -> EditorViewModel(
                repository = repository,
                audioPlayer = audioPlayer!!,
                mediaProvider = mediaProvider!!,
                previewCache = previewCache!!,
                projectId = projectId
            ) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
