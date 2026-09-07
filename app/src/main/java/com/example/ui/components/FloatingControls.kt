package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioDark
import com.example.ui.theme.StudioRecordRed
import com.example.ui.theme.StudioTextPrimary
import com.example.viewmodel.StudioViewModel

@Composable
fun FloatingControls(
    isPlaying: Boolean,
    isRecording: Boolean,
    recordDurationMs: Long,
    isVisible: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Recording Duration pill if recording
            if (isRecording) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StudioDark.copy(alpha = 0.90f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, StudioRecordRed),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StudioRecordRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "REC ${StudioViewModel.formatTime(recordDurationMs)}",
                            color = StudioRecordRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Transport pill container
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = StudioDark.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Play / Pause FAB (48dp)
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .testTag("floating_play_pause_button")
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(StudioCyan.copy(alpha = 0.25f))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = StudioCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Stop FAB (48dp)
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .testTag("floating_stop_button")
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = StudioTextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Record FAB (56dp with pulsing red circle when active)
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_scale"
                    )

                    IconButton(
                        onClick = onRecord,
                        modifier = Modifier
                            .testTag("floating_record_button")
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecording) StudioRecordRed else StudioRecordRed.copy(alpha = 0.20f)
                            )
                            .then(
                                if (isRecording) {
                                    Modifier
                                        .scale(pulseScale)
                                        .border(2.dp, Color.White, CircleShape)
                                } else {
                                    Modifier.border(1.5.dp, StudioRecordRed, CircleShape)
                                }
                            )
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                            tint = if (isRecording) Color.White else StudioRecordRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
