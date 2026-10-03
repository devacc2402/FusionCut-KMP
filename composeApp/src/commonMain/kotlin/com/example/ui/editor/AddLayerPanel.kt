package com.example.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import com.example.ui.util.toComposeColor
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ShapeType
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddLayerPanel(
    onAddShape: (ShapeType, Long) -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onExtractAudioFromVideo: () -> Unit,
    onAddSampleMedia: (String) -> Unit,
    onAddSampleAudio: (String, String) -> Unit,
    onAddText: (String) -> Unit,
    onAddSolid: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = false,
    onClose: (() -> Unit)? = null
) {
    var selectedCategory by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Add Layer",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }
        }

        // Category Tab Row
        ScrollableTabRow(
            selectedTabIndex = selectedCategory,
            containerColor = SurfaceVariantDark,
            contentColor = NeonCyan,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                if (selectedCategory < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedCategory]),
                        color = NeonCyan
                    )
                }
            }
        ) {
            CategoryTab(0, "Media", Icons.Default.Image, selectedCategory) { selectedCategory = 0 }
            CategoryTab(1, "Audio", Icons.Default.GraphicEq, selectedCategory) { selectedCategory = 1 }
            CategoryTab(2, "Shapes", Icons.Default.Category, selectedCategory) { selectedCategory = 2 }
            CategoryTab(3, "Text", Icons.Default.TextFields, selectedCategory) { selectedCategory = 3 }
            CategoryTab(4, "Solid", Icons.Default.Wallpaper, selectedCategory) { selectedCategory = 4 }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Body content per category
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            when (selectedCategory) {
                0 -> {
                    // MEDIA \u0026 GALLERY
                    Text(
                        text = "IMPORT VISUAL MEDIA",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MediaSourceCard(
                            title = "Import Image",
                            subtitle = "Photo, PNG or sticker",
                            icon = Icons.Default.Image,
                            color = NeonCyan,
                            onClick = { onPickImage() },
                            modifier = Modifier.weight(1f)
                        )
                        MediaSourceCard(
                            title = "Import Video",
                            subtitle = "Video clip from gallery",
                            icon = Icons.Default.Videocam,
                            color = NeonEmerald,
                            onClick = { onPickVideo() },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Extract Audio Shortcut in Media Tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerDark)
                            .border(width = 1.dp, color = Color(0xFF9D65F5.toInt()).copy(alpha = 0.5f), shape = RoundedCornerShape(10.dp))
                            .clickable { onExtractAudioFromVideo() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF9D65F5.toInt()).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFB388FF.toInt()), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Extract Audio from Video",
                                style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Rip sound track from any video file",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "SAMPLE MOTION GRAPHIC TEXTURES",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SampleTextureCard(
                            title = "Cyberpunk",
                            gradient = listOf(Color(0xFFFF007F.toInt()), Color(0xFF7928CA.toInt()), Color(0xFF00F0FF.toInt())),
                            onClick = { onAddSampleMedia("sample://cyberpunk") },
                            modifier = Modifier.weight(1f)
                        )
                        SampleTextureCard(
                            title = "Emerald Glow",
                            gradient = listOf(Color(0xFF00F5A0.toInt()), Color(0xFF00D9F5.toInt()), Color(0xFF0F2027.toInt())),
                            onClick = { onAddSampleMedia("sample://emerald") },
                            modifier = Modifier.weight(1f)
                        )
                        SampleTextureCard(
                            title = "Sunset",
                            gradient = listOf(Color(0xFFFA709A.toInt()), Color(0xFFFEE140.toInt())),
                            onClick = { onAddSampleMedia("sample://sunset") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                1 -> {
                    // AUDIO TAB (Add Audio \u0026 Extract Audio)
                    Text(
                        text = "AUDIO TRACK OPTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MediaSourceCard(
                            title = "Add Audio",
                            subtitle = "Pick MP3, WAV or AAC file",
                            icon = Icons.Default.Audiotrack,
                            color = NeonCyan,
                            onClick = { onPickAudio() },
                            modifier = Modifier.weight(1f)
                        )
                        MediaSourceCard(
                            title = "Extract Audio",
                            subtitle = "Extract soundtrack from video",
                            icon = Icons.Default.GraphicEq,
                            color = Color(0xFFB388FF.toInt()),
                            onClick = { onExtractAudioFromVideo() },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "BUILT-IN MOTION SOUNDTRACKS \u0026 BEATS",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioPresetCard(
                            title = "Cyberpunk Synth Beat",
                            subtitle = "128 BPM \u2022 High Energy EDM Synth",
                            icon = Icons.Default.MusicNote,
                            color = NeonCyan,
                            onClick = { onAddSampleAudio("sample://audio_cyberpunk", "Cyberpunk Synth Beat") }
                        )
                        AudioPresetCard(
                            title = "Lo-Fi Lounge Chill",
                            subtitle = "85 BPM \u2022 Relaxed Ambient Waves",
                            icon = Icons.Default.GraphicEq,
                            color = NeonEmerald,
                            onClick = { onAddSampleAudio("sample://audio_lofi", "Lo-Fi Lounge Chill") }
                        )
                        AudioPresetCard(
                            title = "Cinematic Impact \u0026 Rise",
                            subtitle = "Orchestral Trailer Boom FX",
                            icon = Icons.Default.Audiotrack,
                            color = NeonAmber,
                            onClick = { onAddSampleAudio("sample://audio_cinematic", "Cinematic Impact \u0026 Rise") }
                        )
                        AudioPresetCard(
                            title = "Deep Sub Bass Pulse",
                            subtitle = "Modern Trap Bassline Beat",
                            icon = Icons.Default.PlayArrow,
                            color = NeonMagenta,
                            onClick = { onAddSampleAudio("sample://audio_trap", "Deep Sub Bass Pulse") }
                        )
                    }
                }

                2 -> {
                    // SHAPES
                    Text(
                        text = "VECTOR SHAPES",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            ShapeType.ROUNDED_RECT to "Rounded Box",
                            ShapeType.RECTANGLE to "Rectangle",
                            ShapeType.CIRCLE to "Circle",
                            ShapeType.STAR to "Star (5-Point)",
                            ShapeType.TRIANGLE to "Triangle",
                            ShapeType.HEART to "Heart",
                            ShapeType.POLYGON to "Hexagon",
                            ShapeType.CAPSULE to "Capsule"
                        ).forEach { (st, name) ->
                            ShapeCard(
                                shapeType = st,
                                label = name,
                                onClick = { onAddShape(st, 0xFF00F0FF) }
                            )
                        }
                    }
                }

                3 -> {
                    // TEXT
                    Text(
                        text = "TYPOGRAPHY PRESETS",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextPresetCard(
                            title = "Main Title Header",
                            sample = "Fusion Cut",
                            onClick = { onAddText("Fusion Cut") }
                        )
                        TextPresetCard(
                            title = "Subtitle / Tagline",
                            sample = "Motion Graphics \u0026 VFX Suite",
                            onClick = { onAddText("Motion Graphics \u0026 VFX Suite") }
                        )
                        TextPresetCard(
                            title = "Caption / Lower Third",
                            sample = "CREATED WITH FUSION CUT",
                            onClick = { onAddText("CREATED WITH FUSION CUT") }
                        )
                    }
                }

                4 -> {
                    // SOLID BACKGROUND
                    Text(
                        text = "SOLID COLOR PLATES",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            0xFF0A0D14 to "Obsidian Black",
                            0xFF141A29 to "Deep Navy",
                            0xFF00384D to "Cyan Dark",
                            0xFF0D3322 to "Emerald Deep",
                            0xFF3D0026 to "Magenta Deep",
                            0xFF1E1E1E to "Dark Charcoal",
                            0xFFFFFFFF to "Pure White"
                        ).forEach { (colorVal, name) ->
                            SolidPlateCard(
                                color = colorVal.toComposeColor(),
                                name = name,
                                onClick = { onAddSolid(colorVal) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTab(
    index: Int,
    title: String,
    icon: ImageVector,
    selectedIndex: Int,
    onClick: () -> Unit
) {
    Tab(
        selected = selectedIndex == index,
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    )
}

@Composable
private fun ShapeCard(
    shapeType: ShapeType,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContainerDark)
            .border(width = 1.dp, color = SurfaceBorderDark, shape = RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(NeonCyan.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Category, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun MediaSourceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceContainerDark)
            .border(width = 1.dp, color = color.copy(alpha = 0.5f), shape = RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
    }
}

@Composable
private fun AudioPresetCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceContainerDark)
            .border(width = 1.dp, color = SurfaceBorderDark, shape = RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
        }
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SampleTextureCard(
    title: String,
    gradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContainerDark)
            .border(width = 1.dp, color = SurfaceBorderDark, shape = RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(gradient))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontSize = 10.sp))
    }
}

@Composable
private fun TextPresetCard(
    title: String,
    sample: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContainerDark)
            .border(width = 1.dp, color = SurfaceBorderDark, shape = RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.labelSmall.copy(color = NeonCyan, fontWeight = FontWeight.Bold))
            Text(sample, style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
        }
        Icon(Icons.Default.TextFields, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun SolidPlateCard(
    color: Color,
    name: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContainerDark)
            .border(width = 1.dp, color = SurfaceBorderDark, shape = RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(color)
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.3f), shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontSize = 11.sp))
    }
}
