package com.example.di

import com.example.data.repository.createProjectRepository
import com.example.util.*

actual fun createModule(context: Any): AppModule {
    return AppModule(
        repository = createProjectRepository(),
        audioPlayer = DesktopAudioPlayer(),
        mediaProvider = DesktopMediaProvider(),
        previewCache = DesktopPreviewCache()
    )
}
