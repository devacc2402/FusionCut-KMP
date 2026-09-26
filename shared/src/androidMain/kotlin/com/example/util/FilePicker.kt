package com.example.util

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberFilePicker(
    type: PickerType,
    onResult: (String?) -> Unit
): () -> Unit {
    val mimeType = when (type) {
        PickerType.IMAGE -> "image/*"
        PickerType.VIDEO -> "video/*"
        PickerType.AUDIO -> "audio/*"
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            onResult(uri?.toString())
        }
    )

    return remember {
        {
            launcher.launch(mimeType)
        }
    }
}
