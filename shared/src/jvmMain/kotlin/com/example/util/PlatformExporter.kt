package com.example.util

import com.example.engine.FusionEngineProvider
import com.example.model.Layer
import com.example.model.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

actual object PlatformExporter {
    actual fun startExport(
        project: Project,
        layers: List<Layer>,
        width: Int,
        height: Int,
        fps: Int
    ) {
        println("FusionCut Desktop Exporter: Starting export for project '${project.title}' (${width}x${height} @ ${fps}FPS)")
        
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val downloadsDir = File(System.getProperty("user.home"), "Downloads")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                val exportFile = File(downloadsDir, "FusionCut_${project.id}_export.png")
                val engine = FusionEngineProvider.getEngine()
                
                val totalFrames = project.totalFrames.coerceAtLeast(1)
                val pixelArray = IntArray(width * height)

                // Render first frame as high-quality composition snapshot
                engine.renderFrame(
                    project = project,
                    layers = layers,
                    timeSec = 0.0f,
                    targetWidth = width,
                    targetHeight = height,
                    outPixels = pixelArray
                )

                // Write ARGB pixel array to BufferedImage
                val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                bufferedImage.setRGB(0, 0, width, height, pixelArray, 0, width)
                
                ImageIO.write(bufferedImage, "PNG", exportFile)
                println("FusionCut Desktop Exporter: Export successfully saved to ${exportFile.absolutePath}")
            } catch (e: Exception) {
                println("FusionCut Desktop Exporter ERROR: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
