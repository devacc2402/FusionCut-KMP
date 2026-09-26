package com.example.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.FusionEngineProvider
import com.example.model.Layer
import com.example.model.Project
import com.example.ui.theme.*
import com.example.util.PlatformExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportDialog(
    project: Project,
    layers: List<Layer>,
    currentTime: Float,
    loadedBitmaps: Map<String, ImageBitmap>,
    onDismiss: () -> Unit
) {
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        containerColor = SurfaceDark,
        title = { Text("Export Project", color = TextPrimary) },
        text = {
            Column {
                Text("Render every frame to high-quality video.", color = TextSecondary)
                if (exporting) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = NeonCyan,
                        trackColor = SurfaceVariantDark
                    )
                    Text("${(progress * 100).toInt()}% complete", color = NeonCyan, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    exporting = true
                    // Fusion 2 Turbo: Trigger Platform-Specific Export Pipeline
                    PlatformExporter.startExport(
                        project = project,
                        layers = layers,
                        width = project.width,
                        height = project.height,
                        fps = project.fps
                    )
                    onDismiss()
                },
                enabled = !exporting,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = BackgroundDark)
            ) {
                Text(if (exporting) "Exporting..." else "Start Export")
            }
        },
        dismissButton = {
            if (!exporting) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
            }
        }
    )
}

suspend fun performExport(project: Project, layers: List<Layer>, bitmaps: Map<String, ImageBitmap>, onProgress: (Float) -> Unit) {
    withContext(Dispatchers.Default) {
        val totalFrames = project.totalFrames
        val engine = FusionEngineProvider.getEngine()
        val pixelArray = IntArray(project.width * project.height)

        for (i in 0 until totalFrames) {
            val timeSec = i.toFloat() / project.fps
            engine.renderFrame(
                project = project,
                layers = layers,
                timeSec = timeSec,
                targetWidth = project.width,
                targetHeight = project.height,
                outPixels = pixelArray
            )
            onProgress(i.toFloat() / totalFrames)
        }
    }
}
