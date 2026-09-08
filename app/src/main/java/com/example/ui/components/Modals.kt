package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.StudioViewModel

@Composable
fun AudioMixerDialog(
    project: Project,
    audioSettings: AudioSettings,
    onMasterVolumeChange: (Float) -> Unit,
    onMicGainChange: (Float) -> Unit,
    onLayerVolumeChange: (String, Float) -> Unit,
    onToggleLayerMute: (String) -> Unit,
    onToggleSolo: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = StudioCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Studio Audio Mixer", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Master Fader
                Text(
                    text = "Master Volume: ${(audioSettings.masterVolume * 100).toInt()}%",
                    color = StudioTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = audioSettings.masterVolume,
                    onValueChange = onMasterVolumeChange,
                    colors = SliderDefaults.colors(thumbColor = StudioCyan, activeTrackColor = StudioCyan)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Mic Gain Fader
                Text(
                    text = "Microphone Gain: ${(audioSettings.micGain * 100).toInt()}%",
                    color = StudioTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = audioSettings.micGain,
                    onValueChange = onMicGainChange,
                    valueRange = 0f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = StudioAmber, activeTrackColor = StudioAmber)
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Text(
                    text = "Track Channels",
                    color = StudioTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(project.layers) { layer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = layer.name.take(12),
                                color = StudioTextPrimary,
                                fontSize = 11.sp,
                                modifier = Modifier.width(80.dp)
                            )
                            Slider(
                                value = layer.volume,
                                onValueChange = { onLayerVolumeChange(layer.id, it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = StudioPurple,
                                    activeTrackColor = StudioPurple
                                )
                            )
                            IconButton(
                                onClick = { onToggleLayerMute(layer.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (layer.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = if (layer.isMuted) StudioAmber else StudioTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onToggleSolo(layer.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = "Solo",
                                    tint = if (audioSettings.soloLayerId == layer.id) StudioCyan else StudioTextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = StudioCyan)
            }
        },
        containerColor = StudioSurfaceElevated
    )
}

@Composable
fun ExportDialog(
    exportSettings: ExportSettings,
    destinationLabel: String = "Movies (default)",
    onChooseFolder: () -> Unit = {},
    onStartExport: (ExportSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRes by remember { mutableStateOf(exportSettings.resolution) }
    var selectedCodec by remember { mutableStateOf(exportSettings.codec) }
    var selectedFps by remember { mutableStateOf(exportSettings.fps) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    tint = StudioCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Video", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Resolution Preset", color = StudioTextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("720p (HD)", "1080p (FHD)", "4K (UHD)").forEach { res ->
                        FilterChip(
                            selected = selectedRes == res,
                            onClick = { selectedRes = res },
                            label = { Text(res, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioCyan,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Text("Framerate", color = StudioTextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(24, 30, 60).forEach { fps ->
                        FilterChip(
                            selected = selectedFps == fps,
                            onClick = { selectedFps = fps },
                            label = { Text("${fps} fps", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioCyan,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Text("Video Codec", color = StudioTextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("H.264 / AVC", "H.265 / HEVC").forEach { codec ->
                        FilterChip(
                            selected = selectedCodec == codec,
                            onClick = { selectedCodec = codec },
                            label = { Text(codec, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioCyan,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                // Real destination picker — writes the MP4 to the chosen SAF folder (or Movies by default).
                Text("Destination", color = StudioTextSecondary, fontSize = 12.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = StudioAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = destinationLabel,
                        color = StudioTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onChooseFolder,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Choose Folder…", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onStartExport(
                        exportSettings.copy(
                            resolution = selectedRes,
                            fps = selectedFps,
                            codec = selectedCodec
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color.Black)
            ) {
                Text("Start Export", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = StudioTextMuted)
            }
        },
        containerColor = StudioSurfaceElevated
    )
}

@Composable
fun ExportProgressDialog(
    progress: Float,
    isExporting: Boolean,
    isComplete: Boolean,
    successPath: String?,
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    val titleText = when {
        isComplete -> "Export Completed!"
        errorMessage != null -> "Export Failed"
        else -> "Rendering Video…"
    }
    AlertDialog(
        onDismissRequest = { if (isComplete || errorMessage != null) onDismiss() },
        title = {
            Text(text = titleText, color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    errorMessage != null -> {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = StudioRecordRed,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage,
                            color = StudioTextPrimary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No file was created. Nothing was reported as exported.",
                            color = StudioTextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    isComplete -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StudioGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Video successfully generated!",
                            color = StudioTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = successPath ?: "",
                            color = StudioTextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        // Real encoding progress. No fake timer: the exporter only reports after it
                        // has actually written each frame to the encoder.
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = StudioCyan,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Encoding frames: ${(progress * 100).toInt()}%",
                            color = StudioTextPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Compositing layers → H.264 → MP4…",
                            color = StudioTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isComplete || errorMessage != null) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (errorMessage != null) StudioRecordRed else StudioGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Text(if (errorMessage != null) "Close" else "Done")
                }
            }
        },
        containerColor = StudioSurfaceElevated
    )
}

@Composable
fun LayerPropertiesDialog(
    layer: Layer,
    onSave: (name: String, opacity: Float, volume: Float, speed: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(layer.name) }
    var opacity by remember { mutableStateOf(layer.opacity) }
    var volume by remember { mutableStateOf(layer.volume) }
    var speed by remember { mutableStateOf(layer.playbackSpeed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Layer Properties", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Layer Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = StudioCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Opacity: ${(opacity * 100).toInt()}%", color = StudioTextPrimary, fontSize = 12.sp)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    colors = SliderDefaults.colors(thumbColor = StudioCyan, activeTrackColor = StudioCyan)
                )

                Text("Volume: ${(volume * 100).toInt()}%", color = StudioTextPrimary, fontSize = 12.sp)
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    colors = SliderDefaults.colors(thumbColor = StudioPurple, activeTrackColor = StudioPurple)
                )

                Text("Playback Speed: ${"%.1f".format(speed)}x", color = StudioTextPrimary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { s ->
                        FilterChip(
                            selected = speed == s,
                            onClick = { speed = s },
                            label = { Text("${s}x", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioCyan,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, opacity, volume, speed) },
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color.Black)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = StudioTextMuted) }
        },
        containerColor = StudioSurfaceElevated
    )
}

@Composable
fun TextEditorDialog(
    initialText: String = "😱 UNBELIEVABLE TWIST!",
    onSave: (TextData) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var selectedColor by remember { mutableStateOf(0xFFFFD600) }
    var fontSize by remember { mutableStateOf(26f) }
    var hasShadow by remember { mutableStateOf(true) }

    val colorPalette = listOf(
        0xFFFFD600 to "Yellow",
        0xFFFFFFFF to "White",
        0xFF38BDF8 to "Cyan",
        0xFFEF4444 to "Red",
        0xFF10B981 to "Green",
        0xFFF59E0B to "Orange",
        0xFF818CF8 to "Purple"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text Overlay Editor", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text Content") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = StudioCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Font Size: ${fontSize.toInt()}sp", color = StudioTextPrimary, fontSize = 12.sp)
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 16f..42f,
                    colors = SliderDefaults.colors(thumbColor = StudioCyan, activeTrackColor = StudioCyan)
                )

                Text("Text Color", color = StudioTextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colorPalette.forEach { (colorHex, _) ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (selectedColor == colorHex) 2.5.dp else 1.dp,
                                    color = if (selectedColor == colorHex) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Drop Shadow", color = StudioTextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = hasShadow,
                        onCheckedChange = { hasShadow = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = StudioCyan)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        TextData(
                            text = text.ifBlank { "Reaction Text" },
                            colorHex = selectedColor,
                            fontSizeSp = fontSize,
                            hasShadow = hasShadow,
                            isBold = true
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color.Black)
            ) {
                Text("Apply Text")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = StudioTextMuted) }
        },
        containerColor = StudioSurfaceElevated
    )
}

@Composable
fun RenameProjectDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Project", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Project Title") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = StudioCyan
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color.Black)
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = StudioTextMuted) }
        },
        containerColor = StudioSurfaceElevated
    )
}

@Composable
fun DiagnosticsDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MedicalServices,
                    contentDescription = null,
                    tint = StudioCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Diagnostics & Codecs", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Hardware Video Decoders", color = StudioCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• c2.android.avc.decoder (HW H.264)\n• OMX.qcom.video.decoder.avc\n• c2.android.hevc.decoder (HW H.265)", color = StudioTextSecondary, fontSize = 11.sp)

                Text("Camera Subsystem", color = StudioCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Camera2 API Level: FULL\n• Lens Facing: FRONT & BACK available\n• Torch Controller: Hardware LED + Screen Light fallback", color = StudioTextSecondary, fontSize = 11.sp)

                Text("Compositor & Rendering", color = StudioCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Pipeline: EGL14 + GLES 2.0/3.0 PBO offscreen\n• Canvas Compositor: Deterministic Preview == Export\n• Audio Clock: HAL Monotonic Sample PTS", color = StudioTextSecondary, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = StudioCyan)
            }
        },
        containerColor = StudioSurfaceElevated
    )
}

@Composable
fun StartupAspectRatioDialog(
    currentAspectRatio: AspectRatio,
    onSelectAspectRatio: (AspectRatio) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRatio by remember { mutableStateOf(currentAspectRatio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = null,
                    tint = StudioCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Select Project Canvas Format", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Screen will automatically rotate to match", color = StudioTextSecondary, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AspectRatioOptionCard(
                    title = "16:9 Widescreen (Landscape)",
                    subtitle = "Auto-rotates to Landscape 🔄 • YouTube, Gaming, PC Streams",
                    aspectLabel = "1920 × 1080",
                    icon = Icons.Default.StayCurrentLandscape,
                    isSelected = selectedRatio == AspectRatio.SIXTEEN_NINE,
                    onClick = { selectedRatio = AspectRatio.SIXTEEN_NINE }
                )

                AspectRatioOptionCard(
                    title = "9:16 Vertical (Portrait)",
                    subtitle = "Auto-rotates to Portrait 📱 • Shorts, Reels, TikTok",
                    aspectLabel = "1080 × 1920",
                    icon = Icons.Default.StayCurrentPortrait,
                    isSelected = selectedRatio == AspectRatio.NINE_SIXTEEN,
                    onClick = { selectedRatio = AspectRatio.NINE_SIXTEEN }
                )

                AspectRatioOptionCard(
                    title = "1:1 Square Format",
                    subtitle = "Standard square • Instagram feed, Posts",
                    aspectLabel = "1080 × 1080",
                    icon = Icons.Default.CropSquare,
                    isSelected = selectedRatio == AspectRatio.ONE_ONE,
                    onClick = { selectedRatio = AspectRatio.ONE_ONE }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelectAspectRatio(selectedRatio)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Start Project", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = StudioSurfaceElevated
    )
}

@Composable
private fun AspectRatioOptionCard(
    title: String,
    subtitle: String,
    aspectLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) StudioCyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) StudioCyan else Color.White.copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) StudioCyan else Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.Black else StudioTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isSelected) StudioCyan else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = StudioTextSecondary,
                    fontSize = 10.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Text(
                    text = aspectLabel,
                    color = StudioTextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
