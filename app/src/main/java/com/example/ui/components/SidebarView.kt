package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.MediaHelper
import com.example.viewmodel.StudioViewModel

@Composable
fun SidebarView(
    project: Project,
    selectedLayerId: String?,
    audioSettings: AudioSettings,
    torchMode: TorchMode,
    isPlaying: Boolean,
    isRecording: Boolean,
    isFullCanvasMode: Boolean,
    showStatsOverlay: Boolean,
    expandedSectionIds: Set<String>,
    expandedItemIds: Set<String>,
    onToggleSection: (String) -> Unit,
    onToggleItem: (String) -> Unit,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val selectedLayer = project.layers.find { it.id == selectedLayerId }
    val context = LocalContext.current

    // Launchers for picking real media files from device
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = MediaHelper.getFileName(context, it)
            val durationMs = MediaHelper.getVideoDurationMs(context, it)
            viewModel.addRealMediaLayer(
                uri = it.toString(),
                fileName = fileName,
                isVideo = true,
                durationMs = durationMs
            )
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = MediaHelper.getFileName(context, it)
            viewModel.addRealMediaLayer(
                uri = it.toString(),
                fileName = fileName,
                isVideo = false
            )
        }
    }

    val replaceMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val selectedId = selectedLayerId ?: return@let
            val layer = project.layers.find { l -> l.id == selectedId } ?: return@let
            val fileName = MediaHelper.getFileName(context, it)
            val duration = if (layer.type == LayerType.VIDEO) {
                MediaHelper.getVideoDurationMs(context, it)
            } else null
            viewModel.attachRealMediaToLayer(
                layerId = selectedId,
                uri = it.toString(),
                fileName = fileName,
                durationMs = duration
            )
        }
    }

    Column(
        modifier = modifier
            .background(Color(0xFF0E1016))
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
            .testTag("sidebar_container")
    ) {
        // --- 📂 1. SOURCES SECTION ---
        SidebarSectionHeader(
            id = "sources",
            label = "SOURCES",
            icon = Icons.Default.Folder,
            badge = "${project.layers.size}",
            isExpanded = expandedSectionIds.contains("sources"),
            onToggle = { onToggleSection("sources") }
        )

        AnimatedVisibility(visible = expandedSectionIds.contains("sources")) {
            Column(modifier = Modifier.animateContentSize()) {
                // Layer list
                project.layers.reversed().forEach { layer ->
                    val isSelected = layer.id == selectedLayerId
                    LayerRowItem(
                        layer = layer,
                        isSelected = isSelected,
                        onSelect = { viewModel.selectLayer(layer.id) },
                        onToggleVisibility = { viewModel.toggleLayerVisibility(layer.id) },
                        onToggleMute = { viewModel.toggleLayerMute(layer.id) },
                        onMoveUp = { viewModel.moveLayerUp(layer.id) },
                        onMoveDown = { viewModel.moveLayerDown(layer.id) }
                    )
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                // ➕ Add Source (Sub-Menu)
                val isAddSourceExpanded = expandedItemIds.contains("sub_add_source")
                SidebarSubMenuHeader(
                    label = "Add Source",
                    icon = Icons.Default.AddCircleOutline,
                    isExpanded = isAddSourceExpanded,
                    onToggle = { onToggleItem("sub_add_source") }
                )

                AnimatedVisibility(visible = isAddSourceExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(start = 24.dp)
                    ) {
                        SidebarActionItem(
                            label = "Pick Video from Device",
                            icon = Icons.Default.FileOpen,
                            onClick = { pickVideoLauncher.launch("video/*") }
                        )
                        SidebarActionItem(
                            label = "Sample Video Clip",
                            icon = Icons.Default.Movie,
                            onClick = { viewModel.addSource(LayerType.VIDEO) }
                        )
                        SidebarActionItem(
                            label = "Pick Image from Device",
                            icon = Icons.Default.AddPhotoAlternate,
                            onClick = { pickImageLauncher.launch("image/*") }
                        )
                        SidebarActionItem(
                            label = "Sample Image Sticker",
                            icon = Icons.Default.Image,
                            onClick = { viewModel.addSource(LayerType.IMAGE) }
                        )
                        SidebarActionItem(
                            label = "Add Front Camera",
                            icon = Icons.Default.CameraFront,
                            onClick = { viewModel.addCameraSource(CameraFacing.FRONT) }
                        )
                        SidebarActionItem(
                            label = "Add Back Camera",
                            icon = Icons.Default.CameraAlt,
                            onClick = { viewModel.addCameraSource(CameraFacing.BACK) }
                        )
                        SidebarActionItem(
                            label = "Screen Record",
                            icon = Icons.Default.ScreenShare,
                            onClick = { viewModel.addSource(LayerType.SCREEN) }
                        )
                        SidebarActionItem(
                            label = "Text Overlay",
                            icon = Icons.Default.TextFields,
                            onClick = { viewModel.showTextEditorDialog(true) }
                        )
                    }
                }

                // ➖ Remove Selected
                SidebarActionItem(
                    label = "Remove Selected",
                    icon = Icons.Default.DeleteOutline,
                    danger = true,
                    enabled = selectedLayer != null,
                    onClick = { viewModel.removeSelectedLayer() }
                )

                // 📋 Duplicate Selected
                SidebarActionItem(
                    label = "Duplicate Selected",
                    icon = Icons.Default.ContentCopy,
                    enabled = selectedLayer != null,
                    onClick = { viewModel.duplicateSelectedLayer() }
                )
            }
        }

        // --- 🎬 2. SOURCE CONTROLS SECTION (Enabled when source selected) ---
        SidebarSectionHeader(
            id = "controls",
            label = "SOURCE CONTROLS",
            icon = Icons.Default.Tune,
            badge = selectedLayer?.name?.take(10),
            isExpanded = expandedSectionIds.contains("controls"),
            onToggle = { onToggleSection("controls") }
        )

        AnimatedVisibility(visible = expandedSectionIds.contains("controls")) {
            if (selectedLayer == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a source to adjust controls",
                        color = StudioTextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(modifier = Modifier.animateContentSize()) {
                    // Real File Card for Video/Image layers
                    if (selectedLayer.type == LayerType.VIDEO || selectedLayer.type == LayerType.IMAGE) {
                        Surface(
                            color = StudioCyan.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (selectedLayer.type == LayerType.VIDEO) Icons.Default.Movie else Icons.Default.Image,
                                        contentDescription = null,
                                        tint = StudioCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (selectedLayer.mediaUri != null) "Real Media File" else "Sample Template Media",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (selectedLayer.mediaUri != null) selectedLayer.name else "Using demo visual. Attach a real video or image from your device storage.",
                                    color = StudioTextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (selectedLayer.type == LayerType.VIDEO) {
                                            replaceMediaLauncher.launch("video/*")
                                        } else {
                                            replaceMediaLauncher.launch("image/*")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StudioCyan,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (selectedLayer.mediaUri != null) "Choose Different File" else "Select Real File from Device",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Hide / Show
                    SidebarActionItem(
                        label = if (selectedLayer.isVisible) "Hide Layer" else "Show Layer",
                        icon = if (selectedLayer.isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        active = selectedLayer.isVisible,
                        onClick = { viewModel.toggleLayerVisibility(selectedLayer.id) }
                    )

                    // Lock / Unlock
                    SidebarActionItem(
                        label = if (selectedLayer.isLocked) "Unlock Position" else "Lock Position",
                        icon = if (selectedLayer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        active = selectedLayer.isLocked,
                        onClick = { viewModel.toggleLayerLock(selectedLayer.id) }
                    )

                    // Pause / Resume Layer
                    SidebarActionItem(
                        label = if (selectedLayer.isPlaying) "Pause Layer" else "Resume Layer",
                        icon = if (selectedLayer.isPlaying) Icons.Default.PauseCircleOutline else Icons.Default.PlayCircleOutline,
                        onClick = { viewModel.toggleLayerPlaying(selectedLayer.id) }
                    )

                    // 🔦 Torch Toggle (Camera layers only)
                    if (selectedLayer.type == LayerType.CAMERA && selectedLayer.cameraFacing != null) {
                        SidebarActionItem(
                            label = if (selectedLayer.isTorchOn) "Torch OFF (${if (selectedLayer.cameraFacing == CameraFacing.FRONT) "Front" else "Back"})" else "Torch ON (${if (selectedLayer.cameraFacing == CameraFacing.FRONT) "Front" else "Back"})",
                            icon = if (selectedLayer.isTorchOn) Icons.Default.FlashOff else Icons.Default.FlashOn,
                            active = selectedLayer.isTorchOn,
                            onClick = { viewModel.toggleCameraTorch(selectedLayer.id) }
                        )
                    }

                    // 🔄 Fit Mode ▸ Sub-Menu
                    val isFitExpanded = expandedItemIds.contains("sub_fit_mode")
                    SidebarSubMenuHeader(
                        label = "Fit Mode (${selectedLayer.fitMode.name})",
                        icon = Icons.Default.CropFree,
                        isExpanded = isFitExpanded,
                        onToggle = { onToggleItem("sub_fit_mode") }
                    )
                    AnimatedVisibility(visible = isFitExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.25f))
                                .padding(start = 24.dp)
                        ) {
                            SidebarActionItem(
                                label = "Fill (Cover / Crop)",
                                icon = Icons.Default.Fullscreen,
                                active = selectedLayer.fitMode == FitMode.FILL,
                                onClick = { viewModel.setFitMode(FitMode.FILL) }
                            )
                            SidebarActionItem(
                                label = "Fit (Letterbox)",
                                icon = Icons.Default.FitScreen,
                                active = selectedLayer.fitMode == FitMode.FIT,
                                onClick = { viewModel.setFitMode(FitMode.FIT) }
                            )
                        }
                    }

                    // 📐 Transform ▸ Sub-Menu
                    val isTransformExpanded = expandedItemIds.contains("sub_transform")
                    SidebarSubMenuHeader(
                        label = "Transform & Sizing",
                        icon = Icons.Default.OpenWith,
                        isExpanded = isTransformExpanded,
                        onToggle = { onToggleItem("sub_transform") }
                    )
                    AnimatedVisibility(visible = isTransformExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.25f))
                                .padding(start = 24.dp)
                        ) {
                            // ⚡ Auto Fill Button (Highlighted)
                            SidebarActionItem(
                                label = "Auto Fill (100% Canvas)",
                                icon = Icons.Default.FitScreen,
                                active = true,
                                onClick = { viewModel.autoFillSelectedLayer() }
                            )

                            // 📐 Fit to Frame (90%)
                            SidebarActionItem(
                                label = "Fit Inside Frame (90%)",
                                icon = Icons.Default.AspectRatio,
                                onClick = { viewModel.fitSelectedToFrame() }
                            )

                            // 🎯 Center
                            SidebarActionItem(
                                label = "Center on Canvas",
                                icon = Icons.Default.FilterCenterFocus,
                                onClick = { viewModel.centerSelectedLayer() }
                            )

                            // ↔️ Stretch Width
                            SidebarActionItem(
                                label = "Stretch to 100% Width",
                                icon = Icons.Default.Straighten,
                                onClick = { viewModel.stretchSelectedWidth() }
                            )

                            // ↕️ Stretch Height
                            SidebarActionItem(
                                label = "Stretch to 100% Height",
                                icon = Icons.Default.Height,
                                onClick = { viewModel.stretchSelectedHeight() }
                            )

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            // Width & Height Sizing Sliders
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                // Width Slider
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Width Scale", color = StudioTextSecondary, fontSize = 11.sp)
                                    Text(
                                        "${(selectedLayer.transform.w * 100).toInt()}%",
                                        color = StudioCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Slider(
                                    value = selectedLayer.transform.w,
                                    onValueChange = { viewModel.setSelectedDimensions(it, selectedLayer.transform.h) },
                                    valueRange = 0.1f..1.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = StudioCyan,
                                        activeTrackColor = StudioCyan,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                    )
                                )

                                // Height Slider
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Height Scale", color = StudioTextSecondary, fontSize = 11.sp)
                                    Text(
                                        "${(selectedLayer.transform.h * 100).toInt()}%",
                                        color = StudioCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Slider(
                                    value = selectedLayer.transform.h,
                                    onValueChange = { viewModel.setSelectedDimensions(selectedLayer.transform.w, it) },
                                    valueRange = 0.1f..1.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = StudioCyan,
                                        activeTrackColor = StudioCyan,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                    )
                                )
                            }

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            SidebarActionItem(
                                label = "Corner: Top-Left",
                                icon = Icons.Default.NorthWest,
                                onClick = { viewModel.setCornerPosition(CornerPosition.TOP_LEFT) }
                            )
                            SidebarActionItem(
                                label = "Corner: Top-Right",
                                icon = Icons.Default.NorthEast,
                                onClick = { viewModel.setCornerPosition(CornerPosition.TOP_RIGHT) }
                            )
                            SidebarActionItem(
                                label = "Corner: Bottom-Left",
                                icon = Icons.Default.SouthWest,
                                onClick = { viewModel.setCornerPosition(CornerPosition.BOTTOM_LEFT) }
                            )
                            SidebarActionItem(
                                label = "Corner: Bottom-Right",
                                icon = Icons.Default.SouthEast,
                                onClick = { viewModel.setCornerPosition(CornerPosition.BOTTOM_RIGHT) }
                            )

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            // Rotation Control Panel
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.RotateRight,
                                            contentDescription = null,
                                            tint = StudioAmber,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Rotation Angle", color = StudioTextSecondary, fontSize = 11.sp)
                                    }
                                    Text(
                                        "${selectedLayer.transform.rotationDeg.toInt()}°",
                                        color = StudioAmber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Slider(
                                    value = selectedLayer.transform.rotationDeg,
                                    onValueChange = { viewModel.setLayerRotation(selectedLayer.id, it) },
                                    valueRange = -180f..180f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = StudioAmber,
                                        activeTrackColor = StudioAmber,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.rotateLayerBy(selectedLayer.id, -90f) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Text("↺ -90°", fontSize = 10.sp, color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.rotateLayerBy(selectedLayer.id, 90f) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Text("↻ +90°", fontSize = 10.sp, color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.resetLayerRotation(selectedLayer.id) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                        border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.4f))
                                    ) {
                                        Text("Reset 0°", fontSize = 10.sp, color = StudioAmber)
                                    }
                                }
                            }
                        }
                    }

                    // 🔲 Set as Background
                    SidebarActionItem(
                        label = "Set as Background",
                        icon = Icons.Default.Wallpaper,
                        onClick = { viewModel.setAsBackground() }
                    )

                    // ⚙ Advanced properties…
                    SidebarActionItem(
                        label = "Advanced Properties…",
                        icon = Icons.Default.SettingsSuggest,
                        onClick = { viewModel.showPropertiesDialog(true) }
                    )
                }
            }
        }

        // --- 🎵 3. AUDIO SECTION ---
        SidebarSectionHeader(
            id = "audio",
            label = "AUDIO",
            icon = Icons.Default.VolumeUp,
            isExpanded = expandedSectionIds.contains("audio"),
            onToggle = { onToggleSection("audio") }
        )

        AnimatedVisibility(visible = expandedSectionIds.contains("audio")) {
            Column(modifier = Modifier.animateContentSize()) {
                // 🎛 Mixer Panel
                SidebarActionItem(
                    label = "Mixer Panel…",
                    icon = Icons.Default.Equalizer,
                    onClick = { viewModel.showAudioMixer(true) }
                )

                // 🎤 Mic Gain ▸ Sub-Menu
                val isMicExpanded = expandedItemIds.contains("sub_mic_gain")
                SidebarSubMenuHeader(
                    label = "Mic Gain (${(audioSettings.micGain * 100).toInt()}%)",
                    icon = Icons.Default.Mic,
                    isExpanded = isMicExpanded,
                    onToggle = { onToggleItem("sub_mic_gain") }
                )
                AnimatedVisibility(visible = isMicExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(start = 24.dp)
                    ) {
                        SidebarActionItem(
                            label = "+10% Gain",
                            icon = Icons.Default.Add,
                            onClick = { viewModel.adjustMicGain(0.1f) }
                        )
                        SidebarActionItem(
                            label = "−10% Gain",
                            icon = Icons.Default.Remove,
                            onClick = { viewModel.adjustMicGain(-0.1f) }
                        )
                        SidebarActionItem(
                            label = "Reset 100%",
                            icon = Icons.Default.RestartAlt,
                            onClick = { viewModel.resetMicGain() }
                        )
                    }
                }

                // Per-source controls for selected layer
                if (selectedLayer != null) {
                    val isPerSourceExpanded = expandedItemIds.contains("sub_layer_audio")
                    SidebarSubMenuHeader(
                        label = "${selectedLayer.name.take(12)} Audio",
                        icon = Icons.Default.Headphones,
                        isExpanded = isPerSourceExpanded,
                        onToggle = { onToggleItem("sub_layer_audio") }
                    )
                    AnimatedVisibility(visible = isPerSourceExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.25f))
                                .padding(start = 24.dp)
                        ) {
                            SidebarActionItem(
                                label = "Volume +10%",
                                icon = Icons.Default.VolumeUp,
                                onClick = { viewModel.adjustSourceVolume(selectedLayer.id, 0.1f) }
                            )
                            SidebarActionItem(
                                label = "Volume −10%",
                                icon = Icons.Default.VolumeDown,
                                onClick = { viewModel.adjustSourceVolume(selectedLayer.id, -0.1f) }
                            )
                            SidebarActionItem(
                                label = if (selectedLayer.isMuted) "Unmute Layer" else "Mute Layer",
                                icon = if (selectedLayer.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                active = selectedLayer.isMuted,
                                onClick = { viewModel.toggleLayerMute(selectedLayer.id) }
                            )
                            SidebarActionItem(
                                label = if (audioSettings.soloLayerId == selectedLayer.id) "Un-Solo Track" else "Solo Track",
                                icon = Icons.Default.Stars,
                                active = audioSettings.soloLayerId == selectedLayer.id,
                                onClick = { viewModel.toggleSolo(selectedLayer.id) }
                            )
                        }
                    }
                }
            }
        }

        // --- ⏺ 4. RECORD SECTION ---
        SidebarSectionHeader(
            id = "record",
            label = "RECORD & TRANSPORT",
            icon = Icons.Default.FiberManualRecord,
            badge = if (isRecording) "REC" else null,
            isExpanded = expandedSectionIds.contains("record"),
            onToggle = { onToggleSection("record") }
        )

        AnimatedVisibility(visible = expandedSectionIds.contains("record")) {
            Column(modifier = Modifier.animateContentSize()) {
                // Start recording / Stop & save
                SidebarActionItem(
                    label = if (isRecording) "Stop & Save Recording" else "Start Recording",
                    icon = if (isRecording) Icons.Default.StopCircle else Icons.Default.FiberManualRecord,
                    danger = true,
                    active = isRecording,
                    onClick = { viewModel.toggleRecording() }
                )

                // Play / Pause
                SidebarActionItem(
                    label = if (isPlaying) "Pause Playback" else "Play",
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    onClick = { viewModel.togglePlayPause() }
                )

                // Stop
                SidebarActionItem(
                    label = "Stop",
                    icon = Icons.Default.Stop,
                    onClick = { viewModel.stop() }
                )

                // Camera Take
                SidebarActionItem(
                    label = "Add Live Camera Take (Front)",
                    icon = Icons.Default.CameraFront,
                    onClick = { viewModel.addCameraSource(CameraFacing.FRONT) }
                )

                // Screen Record
                SidebarActionItem(
                    label = "Screen Capture Source",
                    icon = Icons.Default.ScreenShare,
                    onClick = { viewModel.addSource(LayerType.SCREEN) }
                )

                // Snapshot Frame
                SidebarActionItem(
                    label = "Snapshot Frame",
                    icon = Icons.Default.CameraAlt,
                    onClick = { viewModel.snapshotFrame() }
                )

                // Restart
                SidebarActionItem(
                    label = "Restart Timeline",
                    icon = Icons.Default.Replay,
                    onClick = { viewModel.restart() }
                )

                // 💡 Light ▸ Sub-Menu
                val isLightExpanded = expandedItemIds.contains("sub_light")
                SidebarSubMenuHeader(
                    label = "Light (${torchMode.label})",
                    icon = Icons.Default.Lightbulb,
                    isExpanded = isLightExpanded,
                    onToggle = { onToggleItem("sub_light") }
                )
                AnimatedVisibility(visible = isLightExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(start = 24.dp)
                    ) {
                        SidebarActionItem(
                            label = "Turn Off Light",
                            icon = Icons.Default.FlashOff,
                            active = torchMode == TorchMode.OFF,
                            onClick = { viewModel.setTorchMode(TorchMode.OFF) }
                        )
                        SidebarActionItem(
                            label = "Front Torch",
                            icon = Icons.Default.FlashOn,
                            active = torchMode == TorchMode.FRONT,
                            onClick = { viewModel.setTorchMode(TorchMode.FRONT) }
                        )
                        SidebarActionItem(
                            label = "Back Torch",
                            icon = Icons.Default.FlashOn,
                            active = torchMode == TorchMode.BACK,
                            onClick = { viewModel.setTorchMode(TorchMode.BACK) }
                        )
                        SidebarActionItem(
                            label = "Both Torches",
                            icon = Icons.Default.Highlight,
                            active = torchMode == TorchMode.BOTH,
                            onClick = { viewModel.setTorchMode(TorchMode.BOTH) }
                        )
                        SidebarActionItem(
                            label = "Screen Light (Softbox)",
                            icon = Icons.Default.WbSunny,
                            active = torchMode == TorchMode.SCREEN_LIGHT,
                            onClick = { viewModel.setTorchMode(TorchMode.SCREEN_LIGHT) }
                        )
                    }
                }
            }
        }

        // --- 🖼 5. CANVAS SECTION ---
        SidebarSectionHeader(
            id = "canvas",
            label = "CANVAS",
            icon = Icons.Default.AspectRatio,
            badge = project.aspectRatio.label.take(4),
            isExpanded = expandedSectionIds.contains("canvas"),
            onToggle = { onToggleSection("canvas") }
        )

        AnimatedVisibility(visible = expandedSectionIds.contains("canvas")) {
            Column(modifier = Modifier.animateContentSize()) {
                // Aspect Ratio ▸ Sub-Menu
                val isAspectExpanded = expandedItemIds.contains("sub_aspect")
                SidebarSubMenuHeader(
                    label = "Aspect Ratio (${project.aspectRatio.label})",
                    icon = Icons.Default.CropOriginal,
                    isExpanded = isAspectExpanded,
                    onToggle = { onToggleItem("sub_aspect") }
                )
                AnimatedVisibility(visible = isAspectExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(start = 24.dp)
                    ) {
                        SidebarActionItem(
                            label = "16:9 Landscape (YouTube)",
                            icon = Icons.Default.Laptop,
                            active = project.aspectRatio == AspectRatio.SIXTEEN_NINE,
                            onClick = { viewModel.setAspectRatio(AspectRatio.SIXTEEN_NINE) }
                        )
                        SidebarActionItem(
                            label = "9:16 Portrait (TikTok/Shorts)",
                            icon = Icons.Default.Smartphone,
                            active = project.aspectRatio == AspectRatio.NINE_SIXTEEN,
                            onClick = { viewModel.setAspectRatio(AspectRatio.NINE_SIXTEEN) }
                        )
                        SidebarActionItem(
                            label = "1:1 Square (Instagram)",
                            icon = Icons.Default.CropSquare,
                            active = project.aspectRatio == AspectRatio.ONE_ONE,
                            onClick = { viewModel.setAspectRatio(AspectRatio.ONE_ONE) }
                        )
                        SidebarActionItem(
                            label = "Format & Auto-Rotate Dialog…",
                            icon = Icons.Default.ScreenRotation,
                            onClick = { viewModel.setShowStartupAspectRatioDialog(true) }
                        )
                    }
                }

                // Background ▸ Sub-Menu
                val isBgExpanded = expandedItemIds.contains("sub_bg")
                SidebarSubMenuHeader(
                    label = "Background (${project.background.label})",
                    icon = Icons.Default.Palette,
                    isExpanded = isBgExpanded,
                    onToggle = { onToggleItem("sub_bg") }
                )
                AnimatedVisibility(visible = isBgExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(start = 24.dp)
                    ) {
                        CanvasBackground.values().forEach { bg ->
                            SidebarActionItem(
                                label = bg.label,
                                icon = Icons.Default.Circle,
                                active = project.background == bg,
                                iconTint = Color(bg.colorLong),
                                onClick = { viewModel.setCanvasBackground(bg) }
                            )
                        }
                    }
                }

                // Full Canvas Mode
                SidebarActionItem(
                    label = if (isFullCanvasMode) "Exit Full Canvas" else "Full Canvas Mode",
                    icon = Icons.Default.Fullscreen,
                    active = isFullCanvasMode,
                    onClick = { viewModel.toggleFullCanvasMode() }
                )

                // Fit All Sources
                SidebarActionItem(
                    label = "Fit All Sources",
                    icon = Icons.Default.CenterFocusStrong,
                    onClick = { viewModel.fitAllSources() }
                )

                // Selection → background
                SidebarActionItem(
                    label = "Selection → Background",
                    icon = Icons.Default.Layers,
                    enabled = selectedLayer != null,
                    onClick = { viewModel.setAsBackground() }
                )
            }
        }

        // --- 📤 6. EXPORT SECTION ---
        SidebarSectionHeader(
            id = "export",
            label = "EXPORT",
            icon = Icons.Default.FileUpload,
            isExpanded = expandedSectionIds.contains("export"),
            onToggle = { onToggleSection("export") }
        )

        AnimatedVisibility(visible = expandedSectionIds.contains("export")) {
            Column(modifier = Modifier.animateContentSize()) {
                SidebarActionItem(
                    label = "Quick Export (720p30 MP4)",
                    icon = Icons.Default.Bolt,
                    onClick = { viewModel.quickExport() }
                )
                SidebarActionItem(
                    label = "Export Settings…",
                    icon = Icons.Default.Settings,
                    onClick = { viewModel.showExportDialog(true) }
                )
            }
        }

        // --- 📁 7. PROJECT SECTION ---
        SidebarSectionHeader(
            id = "project",
            label = "PROJECT",
            icon = Icons.Default.SnippetFolder,
            isExpanded = expandedSectionIds.contains("project"),
            onToggle = { onToggleSection("project") }
        )

        AnimatedVisibility(visible = expandedSectionIds.contains("project")) {
            Column(modifier = Modifier.animateContentSize()) {
                SidebarActionItem(
                    label = "Rename Project…",
                    icon = Icons.Default.Edit,
                    onClick = { viewModel.showRenameDialog(true) }
                )
                SidebarActionItem(
                    label = "Save Now",
                    icon = Icons.Default.Save,
                    onClick = { viewModel.saveProject() }
                )
                SidebarActionItem(
                    label = "Snapshot Frame",
                    icon = Icons.Default.PhotoCamera,
                    onClick = { viewModel.snapshotFrame() }
                )
                SidebarActionItem(
                    label = if (showStatsOverlay) "Hide Stats Overlay" else "Show Stats Overlay",
                    icon = Icons.Default.Speed,
                    active = showStatsOverlay,
                    onClick = { viewModel.toggleStatsOverlay() }
                )
                SidebarActionItem(
                    label = "Undo",
                    icon = Icons.AutoMirrored.Filled.Undo,
                    onClick = { viewModel.undo() }
                )
                SidebarActionItem(
                    label = "Redo",
                    icon = Icons.AutoMirrored.Filled.Redo,
                    onClick = { viewModel.redo() }
                )
                SidebarActionItem(
                    label = "Diagnostics",
                    icon = Icons.Default.MedicalServices,
                    onClick = { viewModel.showDiagnosticsDialog(true) }
                )
            }
        }

        // --- ⚙ 8. SETTINGS SECTION ---
        SidebarSectionHeader(
            id = "settings",
            label = "SETTINGS",
            icon = Icons.Default.Settings,
            isExpanded = expandedSectionIds.contains("settings"),
            onToggle = { onToggleSection("settings") }
        )

        AnimatedVisibility(visible = expandedSectionIds.contains("settings")) {
            Column(modifier = Modifier.animateContentSize()) {
                SidebarActionItem(
                    label = "Export Quality Settings…",
                    icon = Icons.Default.HighQuality,
                    onClick = { viewModel.showExportDialog(true) }
                )
                SidebarActionItem(
                    label = "Save Folder: /Movies",
                    icon = Icons.Default.FolderSpecial,
                    onClick = { viewModel.saveProject() }
                )
                SidebarActionItem(
                    label = "About Ahmed Reaction Studio",
                    icon = Icons.Default.Info,
                    onClick = { viewModel.showDiagnosticsDialog(true) }
                )
            }
        }
    }
}

@Composable
private fun SidebarSectionHeader(
    id: String,
    label: String,
    icon: ImageVector,
    badge: String? = null,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = if (isExpanded) StudioSurfaceElevated else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .testTag("section_$id")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isExpanded) StudioCyan else StudioTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = if (isExpanded) Color.White else StudioTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioCyan.copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = badge,
                        color = StudioCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = StudioTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
}

@Composable
private fun SidebarSubMenuHeader(
    label: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable { onToggle() }
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isExpanded) StudioPurple else StudioTextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = if (isExpanded) Color.White else StudioTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = StudioTextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SidebarActionItem(
    label: String,
    icon: ImageVector,
    active: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    iconTint: Color? = null,
    onClick: () -> Unit
) {
    val textColor = when {
        !enabled -> StudioTextMuted.copy(alpha = 0.4f)
        danger -> StudioRecordRed
        active -> StudioCyan
        else -> StudioTextPrimary
    }

    val tint = iconTint ?: when {
        !enabled -> StudioTextMuted.copy(alpha = 0.4f)
        danger -> StudioRecordRed
        active -> StudioCyan
        else -> StudioTextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Color(0xFF244878).copy(alpha = 0.4f) else Color.Transparent)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LayerRowItem(
    layer: Layer,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleMute: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val bg = if (isSelected) Color(0xFF244878) else StudioSurface.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onSelect() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Layer type icon
        val typeIcon = when {
            layer.type == LayerType.CAMERA && layer.cameraFacing == CameraFacing.FRONT -> Icons.Default.CameraFront
            layer.type == LayerType.CAMERA && layer.cameraFacing == CameraFacing.BACK -> Icons.Default.CameraAlt
            layer.type == LayerType.CAMERA -> Icons.Default.Videocam
            layer.type == LayerType.VIDEO -> Icons.Default.Movie
            layer.type == LayerType.IMAGE -> Icons.Default.Image
            layer.type == LayerType.SCREEN -> Icons.Default.ScreenShare
            layer.type == LayerType.TEXT -> Icons.Default.TextFields
            else -> Icons.Default.Videocam
        }
        Icon(
            imageVector = typeIcon,
            contentDescription = null,
            tint = Color(layer.accentColor),
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Layer name
        Text(
            text = layer.name,
            color = if (isSelected) Color.White else StudioTextPrimary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Inline Controls: 👁 Visibility
        IconButton(
            onClick = onToggleVisibility,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = "Toggle visibility",
                tint = if (layer.isVisible) StudioTextSecondary else StudioTextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
        }

        // Inline Controls: 🔇 Mute
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (layer.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "Toggle mute",
                tint = if (layer.isMuted) StudioAmber else StudioTextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }

        // Move Up / Down
        IconButton(
            onClick = onMoveUp,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "Move up",
                tint = StudioTextMuted,
                modifier = Modifier.size(12.dp)
            )
        }
        IconButton(
            onClick = onMoveDown,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Move down",
                tint = StudioTextMuted,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
