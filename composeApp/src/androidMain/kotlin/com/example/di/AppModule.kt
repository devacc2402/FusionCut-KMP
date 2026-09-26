package com.example.di

import android.content.Context
import com.example.data.repository.createProjectRepository
import com.example.util.*

actual fun createModule(context: Any): AppModule {
    val ctx = context as Context
    return AppModule(
        repository = createProjectRepository(),
        audioPlayer = AndroidAudioPlayer(ctx),
        mediaProvider = AndroidMediaProvider(ctx),
        previewCache = AndroidPreviewCache()
    )
}
