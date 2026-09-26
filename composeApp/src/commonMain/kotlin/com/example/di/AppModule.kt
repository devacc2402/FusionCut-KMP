package com.example.di

import com.example.data.repository.ProjectRepository
import com.example.util.AudioPlayer
import com.example.util.MediaProvider
import com.example.util.PreviewCache

data class AppModule(
    val repository: ProjectRepository,
    val audioPlayer: AudioPlayer,
    val mediaProvider: MediaProvider,
    val previewCache: PreviewCache
)

expect fun createModule(context: Any): AppModule
