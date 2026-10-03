package com.example.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.FusionEngineProvider
import com.example.model.Layer
import com.example.ui.util.toComposeColor
import com.example.ui.util.withAlpha
import com.example.util.PlatformBitmapFactory
import com.example.util.PreviewCache
import com.example.model.LayerType
import com.example.model.Project
import com.example.ui.theme.NeonCyan
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CanvasViewport(
    project: Project,
    layers: List<Layer>,
    currentTime: Float,
    selectedLayerId: String?,
    onSelectLayer: (String?) -> Unit,
    bitmaps: Map<String, ImageBitmap> = emptyMap(),
    previewCache: PreviewCache? = null,
    frameVersion: Long = 0L,
    modifier: Modifier = Modifier
) {
    val currentLayers = rememberUpdatedState(layers)
    val currentTimeVal = rememberUpdatedState(currentTime)
    val currentSelectCallback = rememberUpdatedState(onSelectLayer)
    val currentBitmaps = rememberUpdatedState(bitmaps)
    val currentFrameVersion = rememberUpdatedState(frameVersion)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(0xFF090C11L.toComposeColor())
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        val availableWidth = constraints.maxWidth.toFloat()
        val availableHeight = constraints.maxHeight.toFloat()

        if (availableWidth <= 0 || availableHeight <= 0) return@BoxWithConstraints

        // Calculate aspect-ratio fitted display dimensions
        val projW = project.width.toFloat().coerceAtLeast(100f)
        val projH = project.height.toFloat().coerceAtLeast(100f)
        val projectAspect = projW / projH
        val containerAspect = availableWidth / availableHeight

        var targetWPx: Float
        var targetHPx: Float

        if (projectAspect > containerAspect) {
            targetWPx = availableWidth * 0.96f
            targetHPx = targetWPx / projectAspect
        } else {
            targetHPx = availableHeight * 0.96f
            targetWPx = targetHPx * projectAspect
        }
        
        // Fusion 2 Ultra: Cap preview resolution to 720p (1280px long side) 
        // to guarantee 60fps performance on high-DPI tablets/phones.
        val maxSide = 1280f
        if (targetWPx > maxSide || targetHPx > maxSide) {
            if (targetWPx > targetHPx) {
                targetWPx = maxSide
                targetHPx = targetWPx / projectAspect
            } else {
                targetHPx = maxSide
                targetWPx = targetHPx * projectAspect
            }
        }

        val displayWidthPx = targetWPx.coerceAtLeast(1f)
        val displayHeightPx = targetHPx.coerceAtLeast(1f)

        val scaleFactor = displayWidthPx / projW
        val localDensity = LocalDensity.current
        val displayWidthDp = with(localDensity) { displayWidthPx.toDp() }
        val displayHeightDp = with(localDensity) { displayHeightPx.toDp() }

        // Centered Canvas container
        Box(
            modifier = Modifier
                .size(displayWidthDp, displayHeightDp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(6.dp), spotColor = NeonCyan.withAlpha(0.25f))
                .clip(RoundedCornerShape(6.dp))
                .background(0xFF10141EL.toComposeColor())
                .pointerInput(displayWidthPx, displayHeightPx, scaleFactor) {
                    detectTapGestures { tapOffset ->
                        val time = currentTimeVal.value
                        val currentList = currentLayers.value
                        val sf = scaleFactor.coerceAtLeast(0.001f)
                        val center = Offset(displayWidthPx / 2f, displayHeightPx / 2f)

                        val tapped = findTappedLayer(
                            tapOffset = tapOffset,
                            layers = currentList.filter { it.isVisible && it.isActiveAt(time) },
                            currentTime = time,
                            canvasCenter = center,
                            scaleFactor = sf
                        )
                        currentSelectCallback.value(tapped?.id)
                    }
                }
        ) {
            // Native C++ Video Engine Real-Time Canvas Rendering Pipeline
            val version = currentFrameVersion.value
            val engine = remember { FusionEngineProvider.getEngine() }

            val targetW = displayWidthPx.toInt().coerceAtLeast(1)
            val targetH = displayHeightPx.toInt().coerceAtLeast(1)

            val framePixels = remember(targetW, targetH) { IntArray(targetW * targetH) }
            val frameBitmap = remember(targetW, targetH) { PlatformBitmapFactory.create(targetW, targetH) }

            Canvas(modifier = Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE")
                val v = version

                // Render frame through C++ Native Engine
                engine.renderFrame(
                    project = project,
                    layers = layers,
                    timeSec = currentTime,
                    targetWidth = targetW,
                    targetHeight = targetH,
                    outPixels = framePixels
                )

                // Update bitmap pixels and draw onto canvas
                frameBitmap.setPixels(framePixels)
                drawImage(
                    image = frameBitmap.asImageBitmap(),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )

                // Cache strictly rendered frame in PreviewCache for render status bar
                if (previewCache != null) {
                    val currentFps = project.fps.coerceAtLeast(1)
                    val frameIdx = (currentTime * currentFps).toInt()
                    previewCache.putCachedFrame(project.id, frameIdx, frameBitmap)
                }

                // 3. Subtle Outer Border
                drawRect(
                    color = 0xFF263045L.toComposeColor(),
                    topLeft = Offset.Zero,
                    size = size,
                    style = Stroke(width = 1.5f)
                )
            }

            // Big Red Warning Banner displayed when Native C++ Engine is unavailable/failed
            if (engine.isFallbackActive()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(0xFFDC2626L.toComposeColor())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Engine Warning",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Render engine could not be loaded. You are now using a slower fallback option, please reload the app.",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

private fun findTappedLayer(
    tapOffset: Offset,
    layers: List<Layer>,
    currentTime: Float,
    canvasCenter: Offset,
    scaleFactor: Float
): Layer? {
    for (layer in layers.sortedByDescending { it.orderIndex }) {
        val anim = layer.getAnimatedTransformAt(currentTime)
        val layerCenterX = canvasCenter.x + (anim.positionX * scaleFactor)
        val layerCenterY = canvasCenter.y + (anim.positionY * scaleFactor)

        val baseW = if (layer.type == LayerType.SOLID) 1920f else layer.baseWidth
        val baseH = if (layer.type == LayerType.SOLID) 1920f else layer.baseHeight

        val w = (baseW * scaleFactor * anim.scaleX).coerceAtLeast(40f)
        val h = (baseH * scaleFactor * anim.scaleY).coerceAtLeast(40f)

        val dx = tapOffset.x - layerCenterX
        val dy = tapOffset.y - layerCenterY

        val rad = (-anim.rotation * PI / 180.0).toFloat()
        val localX = dx * cos(rad) - dy * sin(rad)
        val localY = dx * sin(rad) + dy * cos(rad)

        val halfW = w / 2f
        val halfH = h / 2f

        if (localX in -halfW..halfW && localY in -halfH..halfH) {
            return layer
        }
    }
    return null
}
