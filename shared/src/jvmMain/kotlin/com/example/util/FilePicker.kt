package com.example.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberFilePicker(
    type: PickerType,
    onResult: (String?) -> Unit
): () -> Unit {
    return remember {
        {
            val dialog = FileDialog(null as Frame?, "Select File", FileDialog.LOAD)
            
            // Basic filter based on type
            when (type) {
                PickerType.IMAGE -> dialog.setFile("*.jpg;*.jpeg;*.png;*.webp")
                PickerType.VIDEO -> dialog.setFile("*.mp4;*.mkv;*.mov;*.avi")
                PickerType.AUDIO -> dialog.setFile("*.mp3;*.wav;*.aac;*.m4a")
            }
            
            dialog.isVisible = true
            
            val directory = dialog.directory
            val file = dialog.file
            
            if (directory != null && file != null) {
                onResult(File(directory, file).absolutePath)
            } else {
                onResult(null)
            }
        }
    }
}
