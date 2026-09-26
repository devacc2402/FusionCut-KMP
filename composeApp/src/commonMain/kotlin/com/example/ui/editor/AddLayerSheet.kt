package com.example.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.ShapeType
import com.example.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLayerSheet(
    onDismiss: () -> Unit,
    onAddShape: (ShapeType, Long) -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onExtractAudioFromVideo: () -> Unit,
    onAddSampleMedia: (String) -> Unit,
    onAddSampleAudio: (String, String) -> Unit,
    onAddText: (String) -> Unit,
    onAddSolid: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        tonalElevation = 12.dp
    ) {
        AddLayerPanel(
            onAddShape = onAddShape,
            onPickImage = {
                onPickImage()
                onDismiss()
            },
            onPickVideo = {
                onPickVideo()
                onDismiss()
            },
            onPickAudio = {
                onPickAudio()
                onDismiss()
            },
            onExtractAudioFromVideo = {
                onExtractAudioFromVideo()
                onDismiss()
            },
            onAddSampleMedia = {
                onAddSampleMedia(it)
                onDismiss()
            },
            onAddSampleAudio = { uri, name ->
                onAddSampleAudio(uri, name)
                onDismiss()
            },
            onAddText = {
                onAddText(it)
                onDismiss()
            },
            onAddSolid = {
                onAddSolid(it)
                onDismiss()
            },
            showHeader = true,
            onClose = onDismiss,
            modifier = Modifier.padding(bottom = 28.dp)
        )
    }
}
