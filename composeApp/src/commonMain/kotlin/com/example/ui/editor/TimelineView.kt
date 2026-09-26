package com.example.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
// import com.example.engine.PreviewCacheEngine
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Layer
import com.example.model.LayerType
import com.example.model.Project
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

private enum class TrackGestureMode {
    NONE,
    DRAG_BODY,
    TRIM_LEFT,
    TRIM_RIGHT,
    SCRUB_TIMELINE
}

/**
 * CapCut-Style Multi-Track Timeline with Fixed Center-Playhead:
 * - Playhead line is strictly FIXED in the exact horizontal center of the screen.
 * - Touching empty track space or the ruler pans the timeline smoothly under the center playhead.
 * - Touching clip handles trims start/end with zero jitter.
 * - Touching clip body moves the clip along the track.
 */
@Composable
fun TimelineView(
    project: Project,
    layers: List<Layer>,
    currentTime: Float,
    selectedLayerId: String?,
    onSelectLayer: (String?) -> Unit,
    onSeek: (Float) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onUpdateTiming: (String, Float, Float) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onOpenAddLayer: () -> Unit = {},
    onCommitTiming: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(80f) }
    var activeSnapTime by remember { mutableStateOf<Float?>(null) }
    val maxLayerEnd = layers.maxOfOrNull { it.endTime } ?: 0f
    val totalTimelineDuration = maxOf(project.durationSeconds, maxLayerEnd + 30f, 60f)

    val currentSeek = rememberUpdatedState(onSeek)
    val currentTimeState = rememberUpdatedState(currentTime)
    val currentSelect = rememberUpdatedState(onSelectLayer)
    val zoomScaleState = rememberUpdatedState(zoomScale)
    val totalDurationState = rememberUpdatedState(totalTimelineDuration)
    // val cacheTrigger by PreviewCacheEngine.cacheUpdateTrigger.collectAsStateWithLifecycle()
    val cacheTrigger = 0L

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E14))
    ) {
        val availableWidthPx = constraints.maxWidth.toFloat()
        val availableHeightPx = constraints.maxHeight.toFloat()
        if (availableWidthPx <= 0 || availableHeightPx <= 0) return@BoxWithConstraints

        val centerX = availableWidthPx / 2f
        var isPinching by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. CapCut Time Ruler with Center Notch & Tick Marks + Quick Zoom Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Color(0xFF141722)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(availableWidthPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                var lastTouchX = down.position.x
                                var prevDist = 0f
                                var prevCentroidX: Float? = null

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedList = event.changes.filter { it.pressed }
                                    if (pressedList.isEmpty()) break

                                    if (pressedList.size >= 2) {
                                        isPinching = true
                                        val p0 = pressedList[0].position
                                        val p1 = pressedList[1].position
                                        val curDist = kotlin.math.hypot(p0.x - p1.x, p0.y - p1.y).coerceAtLeast(1f)
                                        val curCentroidX = (p0.x + p1.x) / 2f

                                        if (prevDist > 0f) {
                                            val factor = (curDist / prevDist).coerceIn(0.7f, 1.3f)
                                            zoomScale = (zoomScale * factor).coerceIn(8f, 350f)
                                        }
                                        if (prevCentroidX != null) {
                                            val panX = curCentroidX - prevCentroidX!!
                                            val pps = zoomScaleState.value.coerceAtLeast(8f)
                                            val deltaSec = -panX / pps
                                            val newTime = (currentTimeState.value + deltaSec).coerceIn(0f, totalDurationState.value)
                                            currentSeek.value(newTime)
                                        }
                                        prevDist = curDist
                                        prevCentroidX = curCentroidX
                                        for (c in pressedList) c.consume()
                                    } else {
                                        isPinching = false
                                        prevDist = 0f
                                        prevCentroidX = null
                                        val change = pressedList[0]
                                        val deltaX = change.position.x - lastTouchX
                                        lastTouchX = change.position.x
                                        change.consume()
                                        val pps = zoomScaleState.value.coerceAtLeast(8f)
                                        val deltaSec = -deltaX / pps
                                        val newTime = (currentTimeState.value + deltaSec).coerceIn(0f, totalDurationState.value)
                                        currentSeek.value(newTime)
                                    }
                                }
                                isPinching = false
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val _trigger = cacheTrigger // Re-draw ruler when cache updates
                        drawCapCutRuler(
                            drawScope = this,
                            centerX = centerX,
                            currentTime = currentTime,
                            pixelsPerSecond = zoomScale,
                            totalDuration = totalTimelineDuration,
                            projectId = project.id,
                            fps = project.fps
                        )
                    }
                }

                // Compact Zoom Controls & Zoom Badge
                Row(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E2333))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Fit",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier
                            .clickable {
                                val target = (availableWidthPx * 0.75f / maxOf(project.durationSeconds, 1f)).coerceIn(10f, 300f)
                                zoomScale = target
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "−",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier
                            .clickable { zoomScale = (zoomScale / 1.35f).coerceIn(8f, 350f) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier
                            .clickable { zoomScale = (zoomScale * 1.35f).coerceIn(8f, 350f) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // 2. CapCut Multi-Track Canvas Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (layers.isEmpty()) {
                    // Empty Timeline State with "+ Add Track" Button
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(SurfaceVariantDark)
                                .clickable { onOpenAddLayer() }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                                .testTag("timeline_add_first_track_btn"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Track",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Add Track / Overlay",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Drag the timeline left & right to scrub frames",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                        )
                    }
                } else {
                    // Scrollable Tracks Column
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = layers.sortedByDescending { it.orderIndex },
                            key = { it.id }
                        ) { layer ->
                            CapCutCenterTrackRow(
                                layer = layer,
                                allLayers = layers,
                                isSelected = layer.id == selectedLayerId,
                                currentTime = currentTime,
                                centerX = centerX,
                                pixelsPerSecond = zoomScale,
                                totalDuration = totalTimelineDuration,
                                projectDuration = project.durationSeconds,
                                onSelect = { onSelectLayer(layer.id) },
                                onDeselect = { onSelectLayer(null) },
                                onSeek = onSeek,
                                onZoomChange = { factor ->
                                    zoomScale = (zoomScale * factor).coerceIn(8f, 350f)
                                },
                                onUpdateTiming = { start, end -> onUpdateTiming(layer.id, start, end) },
                                onCommitTiming = onCommitTiming,
                                onSnapChange = { snapTime -> activeSnapTime = snapTime }
                            )
                        }

                        // Add Track button row at bottom of tracks
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceVariantDark)
                                        .clickable { onOpenAddLayer() }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("timeline_add_track_bottom_btn"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Track",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "+ Add Track",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = NeonCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Clean CapCut FIXED Center Playhead Overlay + Magnetic Snapping Guide
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Vertical white playhead line at centerX
                    drawLine(
                        color = Color.White,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, size.height),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Draw Magnetic Snapping Line when active
                    activeSnapTime?.let { snapT ->
                        val snapPx = centerX + (snapT - currentTime) * zoomScale
                        if (snapPx in 0f..size.width) {
                            drawLine(
                                color = Color(0xFF00E5FF),
                                start = Offset(snapPx, 0f),
                                end = Offset(snapPx, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                            // Top snapping diamond indicator
                            drawCircle(
                                color = Color(0xFF00E5FF),
                                radius = 4.dp.toPx(),
                                center = Offset(snapPx, 4.dp.toPx())
                            )
                        }
                    }

                    // Top triangular playhead marker
                    val markerPath = Path().apply {
                        moveTo(centerX - 6.dp.toPx(), 0f)
                        lineTo(centerX + 6.dp.toPx(), 0f)
                        lineTo(centerX, 7.dp.toPx())
                        close()
                    }
                    drawPath(path = markerPath, color = Color.White)
                }
            }
        }
    }
}

/**
 * High-Stability Track Row anchored to Center-Playhead:
 * - Direct hit-testing on the static track level determines whether the user is trimming left, trimming right, moving the clip, or scrubbing empty track space.
 * - Magnetic snapping to playhead, other layers' boundaries, 0s, and project duration.
 */
@Composable
private fun CapCutCenterTrackRow(
    layer: Layer,
    allLayers: List<Layer>,
    isSelected: Boolean,
    currentTime: Float,
    centerX: Float,
    pixelsPerSecond: Float,
    totalDuration: Float,
    projectDuration: Float,
    onSelect: () -> Unit,
    onDeselect: () -> Unit,
    onSeek: (Float) -> Unit,
    onZoomChange: (Float) -> Unit,
    onUpdateTiming: (Float, Float) -> Unit,
    onCommitTiming: ((String) -> Unit)? = null,
    onSnapChange: (Float?) -> Unit = {}
) {
    val density = LocalDensity.current
    val accentColor = when (layer.type) {
        LayerType.SHAPE -> NeonCyan
        LayerType.TEXT -> NeonAmber
        LayerType.IMAGE -> NeonEmerald
        LayerType.VIDEO -> NeonMagenta
        LayerType.SOLID -> Color(0xFF9D65F5)
        LayerType.AUDIO -> Color(0xFF00E5FF)
    }

    val typeIcon: ImageVector = when (layer.type) {
        LayerType.SHAPE -> Icons.Default.Category
        LayerType.TEXT -> Icons.Default.TextFields
        LayerType.IMAGE -> Icons.Default.Image
        LayerType.VIDEO -> Icons.Default.Videocam
        LayerType.SOLID -> Icons.Default.Wallpaper
        LayerType.AUDIO -> Icons.Default.GraphicEq
    }

    val currentLayer = rememberUpdatedState(layer)
    val allLayersState = rememberUpdatedState(allLayers)
    val currentTimeState = rememberUpdatedState(currentTime)
    val centerXState = rememberUpdatedState(centerX)
    val pixelsPerSecondState = rememberUpdatedState(pixelsPerSecond)
    val totalDurationState = rememberUpdatedState(totalDuration)
    val projectDurationState = rememberUpdatedState(projectDuration)

    val currentTimingCallback = rememberUpdatedState(onUpdateTiming)
    val currentSelectCallback = rememberUpdatedState(onSelect)
    val currentDeselectCallback = rememberUpdatedState(onDeselect)
    val currentSeekCallback = rememberUpdatedState(onSeek)
    val currentZoomCallback = rememberUpdatedState(onZoomChange)
    val currentCommitCallback = rememberUpdatedState(onCommitTiming)
    val currentSnapCallback = rememberUpdatedState(onSnapChange)

    // Clip position calculation in screen pixels
    val clipStartX = centerX + (layer.startTime - currentTime) * pixelsPerSecond
    val clipEndX = centerX + (layer.endTime - currentTime) * pixelsPerSecond
    val clipWidthPx = (clipEndX - clipStartX).coerceAtLeast(40f)
    val clipWidthDp = with(density) { clipWidthPx.toDp() }
    val durationSec = (layer.endTime - layer.startTime).coerceAtLeast(0.05f)

    var activeDragMode by remember { mutableStateOf(TrackGestureMode.NONE) }
    val handleHitPx = with(density) { 32.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(if (isSelected) Color(0xFF141824) else Color.Transparent)
            .border(width = 0.5.dp, color = Color(0xFF181C28))
            // Static track-level gesture detector (Keyed ONLY by layer.id so it is never interrupted)
            .pointerInput(layer.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val touchX = down.position.x

                    val curLayer = currentLayer.value
                    val curTime = currentTimeState.value
                    val curCenterX = centerXState.value
                    val curPps = pixelsPerSecondState.value.coerceAtLeast(10f)

                    val startPx = curCenterX + (curLayer.startTime - curTime) * curPps
                    val endPx = curCenterX + (curLayer.endTime - curTime) * curPps

                    val distToStart = kotlin.math.abs(touchX - startPx)
                    val distToEnd = kotlin.math.abs(touchX - endPx)
                    val handleHit = with(density) { 26.dp.toPx() }

                    val gestureMode = when {
                        distToStart <= handleHit && distToStart <= distToEnd -> TrackGestureMode.TRIM_LEFT
                        distToEnd <= handleHit -> TrackGestureMode.TRIM_RIGHT
                        touchX in startPx..endPx -> TrackGestureMode.DRAG_BODY
                        else -> TrackGestureMode.SCRUB_TIMELINE
                    }

                    down.consume()
                    activeDragMode = gestureMode

                    if (gestureMode != TrackGestureMode.SCRUB_TIMELINE) {
                        currentSelectCallback.value()
                    }

                    val initialTouchX = touchX
                    val initialStartTime = curLayer.startTime
                    val initialEndTime = curLayer.endTime
                    val initialCurTime = curTime
                    var totalMoved = 0f
                    var prevDist = 0f
                    var prevCentroidX: Float? = null

                    // Prepare snap targets
                    val otherLayers = allLayersState.value.filter { it.id != curLayer.id }
                    val snapTargets = mutableListOf<Float>().apply {
                        add(0f)
                        add(curTime) // Center playhead
                        add(projectDurationState.value)
                        for (other in otherLayers) {
                            add(other.startTime)
                            add(other.endTime)
                        }
                    }
                    val snapThresholdSec = (14f / curPps).coerceIn(0.06f, 0.25f)

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }
                        if (pressedPointers.isEmpty()) break

                        if (pressedPointers.size >= 2) {
                            // Multi-touch Pinch-to-Zoom and Pan
                            activeDragMode = TrackGestureMode.NONE
                            currentSnapCallback.value(null)
                            val p0 = pressedPointers[0].position
                            val p1 = pressedPointers[1].position
                            val curDist = kotlin.math.hypot(p0.x - p1.x, p0.y - p1.y).coerceAtLeast(1f)
                            val curCentroidX = (p0.x + p1.x) / 2f

                            if (prevDist > 0f) {
                                val ratio = (curDist / prevDist).coerceIn(0.7f, 1.3f)
                                currentZoomCallback.value(ratio)
                            }
                            if (prevCentroidX != null) {
                                val panX = curCentroidX - prevCentroidX!!
                                val pps = pixelsPerSecondState.value.coerceAtLeast(8f)
                                val deltaSec = -panX / pps
                                val newTime = (currentTimeState.value + deltaSec).coerceIn(0f, totalDurationState.value)
                                currentSeekCallback.value(newTime)
                            }
                            prevDist = curDist
                            prevCentroidX = curCentroidX
                            for (c in pressedPointers) c.consume()
                            continue
                        }

                        prevDist = 0f
                        prevCentroidX = null
                        val change = pressedPointers.firstOrNull { it.id == down.id } ?: pressedPointers[0]
                        val deltaX = change.position.x - initialTouchX
                        totalMoved += abs(change.position.x - change.previousPosition.x)
                        change.consume()

                        val deltaSec = deltaX / curPps

                        when (gestureMode) {
                            TrackGestureMode.TRIM_LEFT -> {
                                val rawStart = initialStartTime + deltaSec
                                val closest = snapTargets.minByOrNull { abs(it - rawStart) }
                                val (snappedStart, snapGuide) = if (closest != null && abs(closest - rawStart) < snapThresholdSec) {
                                    Pair(closest, closest)
                                } else {
                                    Pair(rawStart, null)
                                }
                                currentSnapCallback.value(snapGuide)
                                val newStart = snappedStart.coerceIn(0f, initialEndTime - 0.05f)
                                currentTimingCallback.value(newStart, initialEndTime)
                            }
                            TrackGestureMode.TRIM_RIGHT -> {
                                val rawEnd = initialEndTime + deltaSec
                                val closest = snapTargets.minByOrNull { abs(it - rawEnd) }
                                val (snappedEnd, snapGuide) = if (closest != null && abs(closest - rawEnd) < snapThresholdSec) {
                                    Pair(closest, closest)
                                } else {
                                    Pair(rawEnd, null)
                                }
                                currentSnapCallback.value(snapGuide)
                                val newEnd = snappedEnd.coerceAtLeast(initialStartTime + 0.05f)
                                currentTimingCallback.value(initialStartTime, newEnd)
                            }
                            TrackGestureMode.DRAG_BODY -> {
                                val dur = initialEndTime - initialStartTime
                                val rawStart = (initialStartTime + deltaSec).coerceAtLeast(0f)
                                val rawEnd = rawStart + dur

                                val closestStart = snapTargets.minByOrNull { abs(it - rawStart) }
                                val closestEnd = snapTargets.minByOrNull { abs(it - rawEnd) }

                                val diffStart = closestStart?.let { abs(it - rawStart) } ?: Float.MAX_VALUE
                                val diffEnd = closestEnd?.let { abs(it - rawEnd) } ?: Float.MAX_VALUE

                                val (newStart, newEnd, snapGuide) = when {
                                    diffStart < snapThresholdSec && diffStart <= diffEnd -> {
                                        val s = (closestStart ?: rawStart).coerceAtLeast(0f)
                                        Triple(s, s + dur, closestStart)
                                    }
                                    diffEnd < snapThresholdSec -> {
                                        val e = closestEnd ?: rawEnd
                                        val s = (e - dur).coerceAtLeast(0f)
                                        Triple(s, s + dur, closestEnd)
                                    }
                                    else -> Triple(rawStart, rawEnd, null)
                                }
                                currentSnapCallback.value(snapGuide)
                                currentTimingCallback.value(newStart, newEnd)
                            }
                            TrackGestureMode.SCRUB_TIMELINE -> {
                                val newTime = (initialCurTime - deltaSec).coerceIn(0f, totalDurationState.value)
                                currentSeekCallback.value(newTime)
                            }
                            TrackGestureMode.NONE -> {}
                        }
                    }

                    currentSnapCallback.value(null)

                    if (gestureMode == TrackGestureMode.SCRUB_TIMELINE && totalMoved < 4f) {
                        // Tapped on empty track space -> deselect layer
                        currentDeselectCallback.value()
                    }

                    activeDragMode = TrackGestureMode.NONE
                    if (gestureMode != TrackGestureMode.SCRUB_TIMELINE) {
                        currentCommitCallback.value?.invoke(layer.id)
                    }
                }
            }
    ) {
        // CapCut Clip Card (Positioned dynamically under Center Playhead)
        Box(
            modifier = Modifier
                .offset { IntOffset(clipStartX.toInt(), 7) }
                .width(clipWidthDp)
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected || activeDragMode == TrackGestureMode.DRAG_BODY || activeDragMode == TrackGestureMode.TRIM_LEFT || activeDragMode == TrackGestureMode.TRIM_RIGHT)
                        accentColor.copy(alpha = 0.95f)
                    else accentColor.copy(alpha = 0.65f)
                )
                .border(
                    width = if (isSelected || activeDragMode != TrackGestureMode.NONE && activeDragMode != TrackGestureMode.SCRUB_TIMELINE) 2.dp else 1.dp,
                    color = if (isSelected || activeDragMode != TrackGestureMode.NONE && activeDragMode != TrackGestureMode.SCRUB_TIMELINE) Color.White else accentColor.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            // Clip Body Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = layer.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "${String.format(Locale.US, "%.1f", durationSec)}s",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }

            // Left CapCut Trim Handle (Glow & High Visibility)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(
                        if (activeDragMode == TrackGestureMode.TRIM_LEFT) Color.White.copy(alpha = 0.60f)
                        else if (isSelected) Color.White.copy(alpha = 0.35f)
                        else Color.White.copy(alpha = 0.18f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(if (activeDragMode == TrackGestureMode.TRIM_LEFT) NeonCyan else Color.White)
                )
            }

            // Right CapCut Trim Handle (Glow & High Visibility)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(
                        if (activeDragMode == TrackGestureMode.TRIM_RIGHT) Color.White.copy(alpha = 0.60f)
                        else if (isSelected) Color.White.copy(alpha = 0.35f)
                        else Color.White.copy(alpha = 0.18f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(if (activeDragMode == TrackGestureMode.TRIM_RIGHT) NeonCyan else Color.White)
                )
            }
        }
    }
}

/**
 * Draws the high-performance CapCut Time Ruler with major/minor tick marks
 * and green RAM Preview Cached indicators (NodeVideo / After Effects style)
 */
private fun drawCapCutRuler(
    drawScope: DrawScope,
    centerX: Float,
    currentTime: Float,
    pixelsPerSecond: Float,
    totalDuration: Float,
    projectId: Long = 0L,
    fps: Int = 30
) {
    val size = drawScope.size
    val stepSeconds = when {
        pixelsPerSecond >= 220f -> 0.25f
        pixelsPerSecond >= 130f -> 0.5f
        pixelsPerSecond >= 70f -> 1.0f
        pixelsPerSecond >= 35f -> 2.0f
        pixelsPerSecond >= 18f -> 5.0f
        pixelsPerSecond >= 8f -> 10.0f
        else -> 30.0f
    }

    val minVisibleTime = (currentTime - (centerX / pixelsPerSecond)).coerceAtLeast(0f)
    val maxVisibleTime = (currentTime + ((size.width - centerX) / pixelsPerSecond)).coerceAtMost(totalDuration + 30f)

    // 1. Draw RAM Preview Cached Line (Green bar showing ONLY pre-rendered composited frames)
    if (projectId > 0L) {
        val effectiveFps = fps.coerceIn(15, 60)
        val startFrame = (minVisibleTime * effectiveFps).toInt().coerceAtLeast(0)
        val endFrame = (maxVisibleTime * effectiveFps).toInt().coerceAtLeast(startFrame)
        val barHeight = 4.5f

        // Group consecutive cached frames into contiguous segments for pixel-exact rendering
        var runStartFrame: Int? = null

        for (f in startFrame..endFrame) {
            // val cached = PreviewCacheEngine.isFrameCached(projectId, f)
            val cached = false
            if (cached) {
                if (runStartFrame == null) {
                    runStartFrame = f
                }
            } else {
                if (runStartFrame != null) {
                    val sTime = runStartFrame.toFloat() / effectiveFps
                    val eTime = f.toFloat() / effectiveFps
                    val x1 = centerX + (sTime - currentTime) * pixelsPerSecond
                    val x2 = centerX + (eTime - currentTime) * pixelsPerSecond
                    val w = (x2 - x1).coerceAtLeast(1f)
                    drawScope.drawRect(
                        color = Color(0xFF00E676),
                        topLeft = Offset(x1, size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size(w, barHeight)
                    )
                    runStartFrame = null
                }
            }
        }
        if (runStartFrame != null) {
            val sTime = runStartFrame.toFloat() / effectiveFps
            val eTime = (endFrame + 1).toFloat() / effectiveFps
            val x1 = centerX + (sTime - currentTime) * pixelsPerSecond
            val x2 = centerX + (eTime - currentTime) * pixelsPerSecond
            val w = (x2 - x1).coerceAtLeast(1f)
            drawScope.drawRect(
                color = Color(0xFF00E676),
                topLeft = Offset(x1, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(w, barHeight)
            )
        }
    }

    // 2. Draw Ruler Ticks
    var t = floor(minVisibleTime / stepSeconds) * stepSeconds
    while (t <= maxVisibleTime) {
        val x = centerX + (t - currentTime) * pixelsPerSecond
        val isMajor = if (stepSeconds < 1f) (t % 1.0f) == 0.0f else (t % (stepSeconds * 2)) == 0.0f
        val tickHeight = if (isMajor) size.height * 0.70f else size.height * 0.35f
        val tickColor = if (isMajor) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.28f)

        drawScope.drawLine(
            color = tickColor,
            start = Offset(x, size.height - tickHeight),
            end = Offset(x, size.height),
            strokeWidth = if (isMajor) 1.5f else 1.0f
        )
        t += stepSeconds
    }

    drawScope.drawLine(
        color = Color(0xFF262D3D),
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = 1f
    )
}
