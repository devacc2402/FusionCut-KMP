package com.example.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Layer
import com.example.model.Project
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun TransportBar(
    project: Project,
    currentTime: Float,
    isPlaying: Boolean,
    selectedLayer: Layer?,
    onTogglePlay: () -> Unit,
    onStepFrame: (Boolean) -> Unit,
    onSplitLayer: () -> Unit,
    onOpenAddLayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSeconds = project.durationSeconds
    val fps = project.fps.coerceAtLeast(1)

    val currentMinutes = (currentTime / 60).toInt()
    val currentSecs = (currentTime % 60).toInt()
    val currentFrame = ((currentTime % 1.0f) * fps).toInt()

    val totalMinutes = (totalSeconds / 60).toInt()
    val totalSecs = (totalSeconds % 60).toInt()

    val formattedCurrentTime = String.format(Locale.US, "%02d:%02d.%02d", currentMinutes, currentSecs, currentFrame)
    val formattedTotalTime = String.format(Locale.US, "%02d:%02d", totalMinutes, totalSecs)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF141722.toInt()),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Left: Timecode Display Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1C2130.toInt()))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = formattedCurrentTime,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = " / $formattedTotalTime",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                )
            }

            // 2. Center: Playback Controls (Step Back, Play/Pause, Step Forward, Quick Split)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quick Split Button (Active when a layer is selected)
                if (selectedLayer != null) {
                    IconButton(
                        onClick = onSplitLayer,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C2130.toInt()))
                            .testTag("transport_quick_split_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallSplit,
                            contentDescription = "Split Clip",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Step -1 Frame
                IconButton(
                    onClick = { onStepFrame(false) },
                    modifier = Modifier.size(32.dp).testTag("transport_step_back")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Step -1 Frame",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Play / Pause Button
                val playBgColor by animateColorAsState(
                    targetValue = if (isPlaying) NeonMagenta else NeonCyan,
                    label = "play_btn_bg"
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(6.dp, CircleShape, spotColor = playBgColor)
                        .clip(CircleShape)
                        .background(playBgColor)
                        .clickable { onTogglePlay() }
                        .testTag("transport_play_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF090B10.toInt()),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Step +1 Frame
                IconButton(
                    onClick = { onStepFrame(true) },
                    modifier = Modifier.size(32.dp).testTag("transport_step_forward")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Step +1 Frame",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 3. Right: "+ Add" shortcut
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1C2130.toInt()))
                    .clickable { onOpenAddLayer() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("transport_add_layer_pill"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    tint = NeonCyan,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Add",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}
