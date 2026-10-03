package com.example.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.FusionEngineProvider
import com.example.model.Layer
import com.example.model.Project
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun ExportDialog(
    project: Project,
    layers: List<Layer>,
    currentTime: Float,
    loadedBitmaps: Map<String, ImageBitmap>,
    onDismiss: () -> Unit
) {
    var exporting by remember { mutableStateOf(false) }
    var exportCompleted by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var renderedFrameCount by remember { mutableIntStateOf(0) }
    var totalProjectFrames by remember { mutableIntStateOf(0) }
    var currentRenderedTimecode by remember { mutableStateOf("00:00.00") }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var exportFileName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val formattedTotalTime = remember(project.durationSeconds) {
        val totalSec = project.durationSeconds
        val mins = (totalSec / 60).toInt()
        val secs = (totalSec % 60).toInt()
        val millis = ((totalSec - totalSec.toInt()) * 100).toInt()
        String.format(Locale.US, "%02d:%02d.%02d", mins, secs, millis)
    }

    AlertDialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        containerColor = SurfaceDark,
        title = { Text(if (exportCompleted) "Export Complete!" else "Export Project", color = TextPrimary) },
        text = {
            Column {
                if (exportCompleted) {
                    Text(
                        "Your rendered video ($exportFileName) has been saved to your Downloads folder.",
                        color = NeonEmerald,
                        fontWeight = FontWeight.Bold
                    )
                } else if (exporting) {
                    Text("Rendering composition into high-quality MP4 video...", color = TextSecondary)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = NeonCyan,
                        trackColor = SurfaceVariantDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Rendered $renderedFrameCount / $totalProjectFrames frames (${(progress * 100).toInt()}%)",
                            color = NeonCyan,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "$currentRenderedTimecode / $formattedTotalTime",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Text("Render every frame into a high-quality H.264 MP4 video file.", color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• Resolution: ${project.width}x${project.height}", color = TextMuted, fontSize = 12.sp)
                    Text("• Frame Rate: ${project.fps} FPS", color = TextMuted, fontSize = 12.sp)
                    Text("• Duration: ${formattedTotalTime}", color = TextMuted, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (exportCompleted) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = BackgroundDark)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            } else if (!exporting) {
                Button(
                    onClick = {
                        exporting = true
                        exportJob = scope.launch {
                            val downloadsDir = File(System.getProperty("user.home"), "Downloads")
                            if (!downloadsDir.exists()) downloadsDir.mkdirs()

                            val cleanTitle = project.title.replace(Regex("[^a-zA-Z0-9_]"), "_")
                            val mp4File = File(downloadsDir, "FusionCut_${cleanTitle}_${project.id}.mp4")
                            exportFileName = mp4File.name

                            performVideoMP4Export(
                                project = project,
                                layers = layers,
                                mp4File = mp4File,
                                onFrameProgress = { frame, total, timeSec, p ->
                                    renderedFrameCount = frame
                                    totalProjectFrames = total
                                    progress = p

                                    val mins = (timeSec / 60).toInt()
                                    val secs = (timeSec % 60).toInt()
                                    val millis = ((timeSec - timeSec.toInt()) * 100).toInt()
                                    currentRenderedTimecode = String.format(Locale.US, "%02d:%02d.%02d", mins, secs, millis)
                                }
                            )

                            progress = 1.0f
                            exporting = false
                            exportCompleted = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = BackgroundDark)
                ) {
                    Text("Start Export", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (exporting) {
                Button(
                    onClick = {
                        exportJob?.cancel()
                        exporting = false
                        progress = 0f
                        renderedFrameCount = 0
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626.toInt()), contentColor = Color.White)
                ) {
                    Text("Cancel Export", fontWeight = FontWeight.Bold)
                }
            } else if (!exportCompleted) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
            }
        }
    )
}

suspend fun performVideoMP4Export(
    project: Project,
    layers: List<Layer>,
    mp4File: File,
    onFrameProgress: (frame: Int, total: Int, timeSec: Float, progress: Float) -> Unit
) {
    withContext(Dispatchers.Default) {
        val totalFrames = (project.durationSeconds * project.fps).toInt().coerceAtLeast(1)
        val engine = FusionEngineProvider.getEngine()
        val pixelArray = IntArray(project.width * project.height)
        val pathStr = mp4File.absolutePath

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

            val isFinal = (i == totalFrames - 1)
            engine.encodeVideoFrame(
                outputPath = pathStr,
                width = project.width,
                height = project.height,
                fps = project.fps,
                frameIndex = i,
                pixels = pixelArray,
                isFinalFrame = isFinal
            )

            onFrameProgress(i + 1, totalFrames, timeSec, (i + 1).toFloat() / totalFrames)
        }
    }
}
