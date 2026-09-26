package com.example.ui.main

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProjectAspectRatio
import com.example.model.RenderEngineType
import com.example.ui.theme.NeonCyan
import com.example.ui.util.toComposeColor
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateProjectSheet(
    onDismiss: () -> Unit,
    onCreate: (
        title: String,
        aspectRatio: ProjectAspectRatio,
        fps: Int,
        backgroundColor: Long,
        renderEngine: RenderEngineType
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf("New Composition") }
    var selectedRatio by remember { mutableStateOf(ProjectAspectRatio.RATIO_9_16) }
    var fps by remember { mutableIntStateOf(60) }
    var bgColor by remember { mutableLongStateOf(0xFF0A0D14) }
    var selectedEngine by remember { mutableStateOf(RenderEngineType.FUSION_2) }

    val bgColors = listOf(
        0xFF0A0D14 to "Obsidian",
        0xFF121826 to "Navy",
        0xFF1C1C1C to "Charcoal",
        0xFF00382B to "Emerald",
        0xFFFFFFFF to "White"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        tonalElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "New Project",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // Project Name Input
                Text("PROJECT TITLE", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().testTag("new_project_title_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SurfaceBorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Aspect Ratio Selector with visual ratio cards
                Text("ASPECT RATIO", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProjectAspectRatio.entries.forEach { ratio ->
                        val isSel = selectedRatio == ratio
                        RatioSelectorCard(
                            ratio = ratio,
                            isSelected = isSel,
                            onClick = { selectedRatio = ratio }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Frame Rate
                Text("FRAME RATE", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(24, 30, 60).forEach { rate ->
                        val isSel = fps == rate
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) NeonCyan else SurfaceContainerDark)
                                .clickable { fps = rate }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${rate} FPS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (isSel) Color(0xFF090B10) else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Background Color
                Text("CANVAS BACKGROUND", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    bgColors.forEach { (colorVal, name) ->
                        val isSel = bgColor == colorVal
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorVal.toComposeColor())
                                .border(
                                    width = if (isSel) 3.dp else 1.dp,
                                    color = if (isSel) NeonCyan else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable { bgColor = colorVal }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Render Engine Selection
                Text("RENDERING ENGINE", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RenderEngineType.entries.forEach { engine ->
                        val isSel = selectedEngine == engine
                        val engineName = "Fusion Engine"
                        val engineColor = NeonEmerald
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) engineColor.copy(alpha = 0.2f) else SurfaceContainerDark)
                                .border(
                                    width = if (isSel) 1.5.dp else 1.dp,
                                    color = if (isSel) engineColor else SurfaceBorderDark,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedEngine = engine }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = engineName,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (isSel) engineColor else TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (engine == RenderEngineType.FUSION_2) {
                                    Text(
                                        text = "Highly Optimized",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isSel) engineColor.copy(alpha = 0.7f) else TextSecondary,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Create Project CTA Button
                Button(
                    onClick = {
                        onCreate(title, selectedRatio, fps, bgColor, selectedEngine)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_create_project_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Create, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create Composition (${selectedRatio.defaultWidth}x${selectedRatio.defaultHeight})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.Black)
                    )
                }
            }
        }
    }
}

@Composable
private fun RatioSelectorCard(
    ratio: ProjectAspectRatio,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) NeonCyan.copy(alpha = 0.15f) else SurfaceContainerDark)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) NeonCyan else SurfaceBorderDark,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Visual Aspect Ratio Frame Preview
        val previewMax = 32f
        val w: Float
        val h: Float
        if (ratio.ratio > 1f) {
            w = previewMax
            h = (previewMax / ratio.ratio).coerceAtLeast(12f)
        } else {
            h = previewMax
            w = (previewMax * ratio.ratio).coerceAtLeast(12f)
        }

        Box(
            modifier = Modifier
                .size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = w.dp, height = h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isSelected) NeonCyan else TextSecondary.copy(alpha = 0.3f))
                    .border(width = 1.dp, color = if (isSelected) NeonCyan else TextSecondary, shape = RoundedCornerShape(2.dp))
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${ratio.widthRatio}:${ratio.heightRatio}",
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isSelected) NeonCyan else TextPrimary,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = "${ratio.defaultWidth}p",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 9.sp)
        )
    }
}
