package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import com.example.ui.util.withAlpha
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Layer
import com.example.model.LayerType
import com.example.model.MediaFitMode
import com.example.model.ShapeType
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.util.toComposeColor
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransformInspector(
    layer: Layer,
    currentTime: Float = 0f,
    onClose: () -> Unit,
    onResetTransform: () -> Unit,
    onCenterLayer: () -> Unit,
    onDeleteLayer: () -> Unit,
    onUpdateTransform: (
        positionX: Float?,
        positionY: Float?,
        scaleX: Float?,
        scaleY: Float?,
        rotation: Float?,
        opacity: Float?,
        anchorX: Float?,
        anchorY: Float?,
        blendMode: String?,
        isFlippedH: Boolean?,
        isFlippedV: Boolean?
    ) -> Unit,
    onUpdateShape: (
        shapeType: ShapeType?,
        fillColor: Long?,
        strokeColor: Long?,
        strokeWidth: Float?,
        cornerRadius: Float?
    ) -> Unit,
    onUpdateText: (
        text: String?,
        fontSize: Float?,
        textColor: Long?,
        isBold: Boolean?,
        isItalic: Boolean?,
        alignment: String?
    ) -> Unit,
    onUpdateMedia: (
        uri: String?,
        fitMode: MediaFitMode?
    ) -> Unit,
    onPickMedia: () -> Unit,
    onUpdateTiming: (Float, Float) -> Unit = { _, _ -> },
    onExtendDuration: (Float) -> Unit = {},
    onMatchProjectDuration: () -> Unit = {},
    onTrimStartToPlayhead: () -> Unit = {},
    onTrimEndToPlayhead: () -> Unit = {},
    onSplitAtPlayhead: () -> Unit = {},
    initialTab: Int = 0,
    isSidebar: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(layer.id, initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var isScaleLinked by remember { mutableStateOf(true) }

    Surface(
        modifier = if (isSidebar) modifier else modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        color = SurfaceDark,
        tonalElevation = 6.dp,
        shape = if (isSidebar) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = if (isSidebar) null else BorderStroke(1.dp, SurfaceBorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            // Drag handle pill at top (Mobile only)
            if (!isSidebar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF556075.toInt()))
                    )
                }
            }

            // 1. Compact Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariantDark)
                    .padding(horizontal = 12.dp, vertical = if (isSidebar) 8.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val badgeColor = when (layer.type) {
                        LayerType.SHAPE -> NeonCyan
                        LayerType.TEXT -> NeonAmber
                        LayerType.IMAGE -> NeonEmerald
                        LayerType.VIDEO -> NeonMagenta
                        LayerType.SOLID -> Color(0xFF9D65F5.toInt())
                        LayerType.AUDIO -> Color(0xFF00E5FF.toInt())
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.withAlpha(0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = layer.type.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = layer.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1
                    )
                }

                // Header Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (layer.type != LayerType.AUDIO) {
                        IconButton(
                            onClick = onCenterLayer,
                            modifier = Modifier.size(28.dp).testTag("center_layer_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CenterFocusStrong,
                                contentDescription = "Center",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onResetTransform,
                            modifier = Modifier.size(28.dp).testTag("reset_transform_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDeleteLayer,
                        modifier = Modifier.size(28.dp).testTag("delete_layer_header_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = NeonMagenta,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp).testTag("close_inspector_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 2. If Audio Layer: Show Dedicated Audio Controls
            if (layer.type == LayerType.AUDIO) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    AudioInspectorContent(
                        layer = layer,
                        onUpdateVolume = { vol ->
                            onUpdateTransform(null, null, null, null, null, vol, null, null, null, null, null)
                        },
                        onPickNewAudio = onPickMedia
                    )
                }
                return@Surface
            }

            // 3. Compact Tabs: Transform vs Style vs Timing
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceVariantDark,
                contentColor = NeonCyan,
                modifier = Modifier.height(32.dp),
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = NeonCyan
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Transform, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Transform", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Style", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Timing", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                )
            }

            // 4. Scrollable Controls Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        CompactTransformControls(
                            layer = layer,
                            isScaleLinked = isScaleLinked,
                            onToggleScaleLink = { isScaleLinked = !isScaleLinked },
                            onUpdateTransform = onUpdateTransform
                        )
                    }
                    1 -> {
                        CompactStyleControls(
                            layer = layer,
                            onUpdateShape = onUpdateShape,
                            onUpdateText = onUpdateText,
                            onUpdateMedia = onUpdateMedia,
                            onPickMedia = onPickMedia
                        )
                    }
                    2 -> {
                        CompactTimingControls(
                            layer = layer,
                            currentTime = currentTime,
                            onUpdateTiming = onUpdateTiming,
                            onExtendDuration = onExtendDuration,
                            onMatchProjectDuration = onMatchProjectDuration,
                            onTrimStartToPlayhead = onTrimStartToPlayhead,
                            onTrimEndToPlayhead = onTrimEndToPlayhead,
                            onSplitAtPlayhead = onSplitAtPlayhead
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTimingControls(
    layer: Layer,
    currentTime: Float,
    onUpdateTiming: (Float, Float) -> Unit,
    onExtendDuration: (Float) -> Unit,
    onMatchProjectDuration: () -> Unit,
    onTrimStartToPlayhead: () -> Unit,
    onTrimEndToPlayhead: () -> Unit,
    onSplitAtPlayhead: () -> Unit
) {
    val duration = (layer.endTime - layer.startTime).coerceAtLeast(0.05f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Duration Display and Quick Nudge Buttons
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceVariantDark,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.5.dp, SurfaceBorderDark)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Clip Duration", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary))
                    Text(
                        "${String.format(java.util.Locale.US, "%.2f", duration)}s",
                        style = MaterialTheme.typography.titleMedium.copy(color = NeonCyan, fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { onExtendDuration(-1f) },
                        modifier = Modifier.weight(1f).height(30.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B3D.toInt()), contentColor = Color.White),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("-1s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onExtendDuration(1f) },
                        modifier = Modifier.weight(1f).height(30.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B3D.toInt()), contentColor = Color.White),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("+1s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onExtendDuration(5f) },
                        modifier = Modifier.weight(1f).height(30.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B3D.toInt()), contentColor = NeonCyan),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("+5s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onExtendDuration(10f) },
                        modifier = Modifier.weight(1f).height(30.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B3D.toInt()), contentColor = NeonCyan),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("+10s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Start / End Points
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                color = SurfaceVariantDark,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, SurfaceBorderDark)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Start Time", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${String.format(java.util.Locale.US, "%.2f", layer.startTime)}s",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { onUpdateTiming((layer.startTime - 0.5f).coerceAtLeast(0f), layer.endTime) },
                            modifier = Modifier.weight(1f).height(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2232.toInt()), contentColor = Color.White),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("-0.5s", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { onUpdateTiming((layer.startTime + 0.5f).coerceAtMost(layer.endTime - 0.1f), layer.endTime) },
                            modifier = Modifier.weight(1f).height(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2232.toInt()), contentColor = Color.White),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("+0.5s", fontSize = 10.sp)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                color = SurfaceVariantDark,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, SurfaceBorderDark)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("End Time", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${String.format(java.util.Locale.US, "%.2f", layer.endTime)}s",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { onUpdateTiming(layer.startTime, (layer.endTime - 0.5f).coerceAtLeast(layer.startTime + 0.1f)) },
                            modifier = Modifier.weight(1f).height(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2232.toInt()), contentColor = Color.White),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("-0.5s", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { onUpdateTiming(layer.startTime, layer.endTime + 0.5f) },
                            modifier = Modifier.weight(1f).height(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2232.toInt()), contentColor = Color.White),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("+0.5s", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Quick Timeline Actions
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onMatchProjectDuration,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.withAlpha(0.2f), contentColor = NeonCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Match Entire Project Duration", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onTrimStartToPlayhead,
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B3D.toInt()), contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Trim Start to Playhead", fontSize = 10.5.sp, maxLines = 1)
                }

                Button(
                    onClick = onTrimEndToPlayhead,
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B3D.toInt()), contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Trim End to Playhead", fontSize = 10.5.sp, maxLines = 1)
                }
            }

            Button(
                onClick = onSplitAtPlayhead,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B203D.toInt()), contentColor = NeonMagenta),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Split Clip at Current Playhead", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AudioInspectorContent(
    layer: Layer,
    onUpdateVolume: (Float) -> Unit,
    onPickNewAudio: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUDIO VOLUME",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
            )
            Text(
                text = "${(layer.opacity * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(color = NeonCyan, fontWeight = FontWeight.Bold)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (layer.opacity <= 0.01f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = null,
                tint = if (layer.opacity <= 0.01f) NeonMagenta else NeonCyan,
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        onUpdateVolume(if (layer.opacity <= 0.01f) 1.0f else 0.0f)
                    }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = layer.opacity,
                onValueChange = onUpdateVolume,
                valueRange = 0.0f..1.5f,
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
                modifier = Modifier.weight(1f)
            )
        }

        // Timing Readout Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceContainerDark)
                .border(width = 0.5.dp, color = SurfaceBorderDark, shape = RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Start Time", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
                Text("${String.format(Locale.US, "%.2f", layer.startTime)}s", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
            }
            Column {
                Text("End Time", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
                Text("${String.format(Locale.US, "%.2f", layer.endTime)}s", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
            }
            Column {
                Text("Duration", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
                Text("${String.format(Locale.US, "%.2f", layer.endTime - layer.startTime)}s", style = MaterialTheme.typography.bodySmall.copy(color = NeonCyan, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun CompactTransformControls(
    layer: Layer,
    isScaleLinked: Boolean,
    onToggleScaleLink: () -> Unit,
    onUpdateTransform: (
        positionX: Float?,
        positionY: Float?,
        scaleX: Float?,
        scaleY: Float?,
        rotation: Float?,
        opacity: Float?,
        anchorX: Float?,
        anchorY: Float?,
        blendMode: String?,
        isFlippedH: Boolean?,
        isFlippedV: Boolean?
    ) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Position Section
        Text(
            text = "POSITION",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        )

        // X Slider
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("X", style = MaterialTheme.typography.labelSmall.copy(color = NeonCyan, fontWeight = FontWeight.Bold), modifier = Modifier.width(16.dp))
            Slider(
                value = layer.positionX,
                onValueChange = { onUpdateTransform(it, null, null, null, null, null, null, null, null, null, null) },
                valueRange = -1000f..1000f,
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
                modifier = Modifier.weight(1f).height(24.dp).testTag("position_x_slider")
            )
            Text(
                text = "${layer.positionX.toInt()}px",
                style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontSize = 10.sp),
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End
            )
        }

        // Y Slider
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Y", style = MaterialTheme.typography.labelSmall.copy(color = NeonCyan, fontWeight = FontWeight.Bold), modifier = Modifier.width(16.dp))
            Slider(
                value = layer.positionY,
                onValueChange = { onUpdateTransform(null, it, null, null, null, null, null, null, null, null, null) },
                valueRange = -1000f..1000f,
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
                modifier = Modifier.weight(1f).height(24.dp).testTag("position_y_slider")
            )
            Text(
                text = "${layer.positionY.toInt()}px",
                style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontSize = 10.sp),
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End
            )
        }

        // Scale Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val scaleLabel = if (isScaleLinked) {
                "SCALE (${(layer.scaleX * 100).toInt()}%)"
            } else {
                "SCALE (UNLINKED X / Y)"
            }
            Text(scaleLabel, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp))
            IconButton(onClick = onToggleScaleLink, modifier = Modifier.size(20.dp).testTag("scale_link_toggle")) {
                Icon(
                    if (isScaleLinked) Icons.Default.Link else Icons.Default.LinkOff,
                    contentDescription = "Toggle Scale Link",
                    tint = if (isScaleLinked) NeonEmerald else NeonAmber,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        if (isScaleLinked) {
            // Unified Scale Slider
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = layer.scaleX,
                    onValueChange = { newScale ->
                        onUpdateTransform(null, null, newScale, newScale, null, null, null, null, null, null, null)
                    },
                    valueRange = 0.05f..15.0f,
                    colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald),
                    modifier = Modifier.weight(1f).height(24.dp).testTag("scale_slider")
                )
                Text(
                    text = "${(layer.scaleX * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontSize = 10.sp),
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End
                )
            }
        } else {
            // Separate Scale X / Scale Y Sliders
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Scale X Bar
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("X", style = MaterialTheme.typography.labelSmall.copy(color = NeonEmerald, fontWeight = FontWeight.Bold), modifier = Modifier.width(16.dp))
                    Slider(
                        value = layer.scaleX,
                        onValueChange = { newScaleX ->
                            onUpdateTransform(null, null, newScaleX, null, null, null, null, null, null, null, null)
                        },
                        valueRange = 0.05f..15.0f,
                        colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald),
                        modifier = Modifier.weight(1f).height(24.dp).testTag("scale_x_slider")
                    )
                    Text(
                        text = "${(layer.scaleX * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontSize = 10.sp),
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End
                    )
                }

                // Scale Y Bar
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Y", style = MaterialTheme.typography.labelSmall.copy(color = NeonEmerald, fontWeight = FontWeight.Bold), modifier = Modifier.width(16.dp))
                    Slider(
                        value = layer.scaleY,
                        onValueChange = { newScaleY ->
                            onUpdateTransform(null, null, null, newScaleY, null, null, null, null, null, null, null)
                        },
                        valueRange = 0.05f..15.0f,
                        colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald),
                        modifier = Modifier.weight(1f).height(24.dp).testTag("scale_y_slider")
                    )
                    Text(
                        text = "${(layer.scaleY * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontSize = 10.sp),
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        // Rotation Slider & Flips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ROTATION (${layer.rotation.toInt()}°)", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onUpdateTransform(null, null, null, null, null, null, null, null, null, !layer.isFlippedH, null) },
                    modifier = Modifier.size(22.dp).testTag("flip_h_btn")
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Flip H", tint = if (layer.isFlippedH) NeonCyan else TextSecondary, modifier = Modifier.size(15.dp))
                }
                IconButton(
                    onClick = { onUpdateTransform(null, null, null, null, null, null, null, null, null, null, !layer.isFlippedV) },
                    modifier = Modifier.size(22.dp).testTag("flip_v_btn")
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = "Flip V", tint = if (layer.isFlippedV) NeonCyan else TextSecondary, modifier = Modifier.size(15.dp))
                }
            }
        }
        Slider(
            value = layer.rotation,
            onValueChange = { onUpdateTransform(null, null, null, null, it, null, null, null, null, null, null) },
            valueRange = -180f..180f,
            colors = SliderDefaults.colors(thumbColor = NeonAmber, activeTrackColor = NeonAmber),
            modifier = Modifier.fillMaxWidth().height(24.dp).testTag("rotation_slider")
        )

        // Opacity Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("OPACITY (${(layer.opacity * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp))
        }
        Slider(
            value = layer.opacity,
            onValueChange = { onUpdateTransform(null, null, null, null, null, it, null, null, null, null, null) },
            valueRange = 0.0f..1.0f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(24.dp).testTag("opacity_slider")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactStyleControls(
    layer: Layer,
    onUpdateShape: (
        shapeType: ShapeType?,
        fillColor: Long?,
        strokeColor: Long?,
        strokeWidth: Float?,
        cornerRadius: Float?
    ) -> Unit,
    onUpdateText: (
        text: String?,
        fontSize: Float?,
        textColor: Long?,
        isBold: Boolean?,
        isItalic: Boolean?,
        alignment: String?
    ) -> Unit,
    onUpdateMedia: (
        uri: String?,
        fitMode: MediaFitMode?
    ) -> Unit,
    onPickMedia: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (layer.type) {
            LayerType.SHAPE -> {
                Text("SHAPE GEOMETRY", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ShapeType.values().forEach { st ->
                        val isSelected = layer.shapeType == st
                        FilterChip(
                            selected = isSelected,
                            onClick = { onUpdateShape(st, null, null, null, null) },
                            label = { Text(st.name.replace("_", " "), fontSize = 10.sp) },
                            modifier = Modifier.height(26.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.withAlpha(0.25f),
                                selectedLabelColor = NeonCyan,
                                containerColor = SurfaceVariantDark,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                CompactColorPalette(
                    title = "FILL COLOR",
                    currentColor = layer.fillColor,
                    onColorSelect = { onUpdateShape(null, it, null, null, null) }
                )
            }

            LayerType.TEXT -> {
                Text("TEXT CONTENT", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                OutlinedTextField(
                    value = layer.text,
                    onValueChange = { onUpdateText(it, null, null, null, null, null) },
                    modifier = Modifier.fillMaxWidth().testTag("text_input_field"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SurfaceBorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceVariantDark,
                        unfocusedContainerColor = SurfaceVariantDark
                    ),
                    shape = RoundedCornerShape(6.dp)
                )

                Text("FONT SIZE (${layer.fontSize.toInt()}sp)", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                Slider(
                    value = layer.fontSize,
                    onValueChange = { onUpdateText(null, it, null, null, null, null) },
                    valueRange = 16f..120f,
                    colors = SliderDefaults.colors(thumbColor = NeonAmber, activeTrackColor = NeonAmber),
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )

                CompactColorPalette(
                    title = "TEXT COLOR",
                    currentColor = layer.textColor,
                    onColorSelect = { onUpdateText(null, null, it, null, null, null) }
                )
            }

            LayerType.SOLID -> {
                CompactColorPalette(
                    title = "SOLID PLATE COLOR",
                    currentColor = layer.fillColor,
                    onColorSelect = { onUpdateShape(null, it, null, null, null) }
                )
            }

            LayerType.IMAGE, LayerType.VIDEO -> {
                Text("MEDIA OPTIONS", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                Button(
                    onClick = onPickMedia,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark, contentColor = NeonCyan),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Replace Media File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            LayerType.AUDIO -> {
                // Handled in dedicated audio view
            }
        }
    }
}

@Composable
private fun CompactColorPalette(
    title: String,
    currentColor: Long,
    onColorSelect: (Long) -> Unit
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp))
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                0xFF00F0FF to "Cyan",
                0xFF00FF66 to "Neon Green",
                0xFFFF007F to "Magenta",
                0xFFFFB800 to "Amber",
                0xFF9D65F5 to "Violet",
                0xFFFFFFFF to "White",
                0xFF10121A to "Black"
            ).forEach { (colorVal, _) ->
                val isSelected = currentColor == colorVal
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colorVal.toComposeColor())
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.White.withAlpha(0.2f),
                            shape = CircleShape
                        )
                        .clickable { onColorSelect(colorVal) }
                )
            }
        }
    }
}
