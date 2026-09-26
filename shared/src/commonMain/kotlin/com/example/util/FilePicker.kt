package com.example.util

import androidx.compose.runtime.Composable

enum class PickerType {
    IMAGE, VIDEO, AUDIO
}

@Composable
expect fun rememberFilePicker(
    type: PickerType,
    onResult: (String?) -> Unit
): () -> Unit
