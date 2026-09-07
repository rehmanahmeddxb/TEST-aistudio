package com.example.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*
import kotlin.math.sin

@Composable
fun StageView(
    project: Project,
    selectedLayerId: String?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    isRecording: Boolean,
    showStatsOverlay: Boolean,
    stats: StatsInfo,
    isFullCanvasMode: Boolean = false,
    onSelectLayer: (String?) -> Unit,
    onUpdateTransform: (String, LayerTransform) -> Unit,
    onDoubleTapText: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLayerVisibility: (String) -> Unit = {},
    onToggleLayerPlaying: (String) -> Unit = {},
    onStopLayer: (String) -> Unit = {},
    onToggleFullCanvas: () -> Unit = {},
    onAutoFillSelected: () -> Unit = {},
    onFitFrameSelected: () -> Unit = {},
    onCenterSelected: () -> Unit = {},
    onStretchWidthSelected: () -> Unit = {},
    onStretchHeightSelected: () -> Unit = {},
    onResetRotationSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Live frame ticker animation for simulated video playback motion
    val infiniteTransition = rememberInfiniteTransition(label = "video_motion")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    val selectedLayer = project.layers.find { it.id == selectedLayerId }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080B))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // Tapping background deselects layer
                        onSelectLayer(null)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Compute fitted canvas dimensions preserving project aspect ratio.
        // The stage is always rendered edge-to-edge full screen; the menu
        // chrome (top strip / sidebar) floats on top instead of resizing it.
        val targetRatio = project.aspectRatio.ratio
        val availW = maxWidth
        val availH = maxHeight

        val (canvasWidthDp, canvasHeightDp) = remember(availW, availH, targetRatio) {
            val availRatio = availW.value / availH.value
            if (availRatio > targetRatio) {
                val h = availH
                val w = (h.value * targetRatio).dp
                Pair(w, h)
            } else {
                val w = availW
                val h = (w.value / targetRatio).dp
                Pair(w, h)
            }
        }

        val density = LocalDensity.current
        val canvasWidthPx = with(density) { canvasWidthDp.toPx() }
        val canvasHeightPx = with(density) { canvasHeightDp.toPx() }

        // --- STAGE CANVAS ---
        Box(
            modifier = Modifier
                .size(width = canvasWidthDp, height = canvasHeightDp)
                .shadow(16.dp, RoundedCornerShape(6.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .background(Color(project.background.colorLong))
                .testTag("stage_canvas")
        ) {
            // Render every layer (even hidden ones, dimmed) so the fast quick-control
            // overlay (eye / play / stop) is always reachable directly on its frame.
            project.layers.forEach { layer ->
                LayerItemRenderer(
                    layer = layer,
                    isSelected = layer.id == selectedLayerId,
                    isPlaying = isPlaying,
                    wavePhase = wavePhase,
                    canvasWidthDp = canvasWidthDp,
                    canvasHeightDp = canvasHeightDp,
                    canvasWidthPx = canvasWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    onSelect = { onSelectLayer(layer.id) },
                    onDoubleTap = {
                        if (layer.type == LayerType.TEXT) {
                            onDoubleTapText(layer.id)
                        }
                    },
                    onUpdateTransform = { newTransform ->
                        onUpdateTransform(layer.id, newTransform)
                    },
                    onToggleVisibility = { onToggleLayerVisibility(layer.id) },
                    onTogglePlay = { onToggleLayerPlaying(layer.id) },
                    onStopLayer = { onStopLayer(layer.id) }
                )
            }

            // Snap Guidelines & Center Hairlines when dragging
            if (selectedLayer != null && !selectedLayer.isLocked) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cxPx = selectedLayer.transform.cx * size.width
                    val cyPx = selectedLayer.transform.cy * size.height

                    // Draw subtle snap guide when near center
                    if (kotlin.math.abs(cxPx - size.width / 2f) < 8f) {
                        drawLine(
                            color = StudioCyan.copy(alpha = 0.6f),
                            start = Offset(size.width / 2f, 0f),
                            end = Offset(size.width / 2f, size.height),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    }
                    if (kotlin.math.abs(cyPx - size.height / 2f) < 8f) {
                        drawLine(
                            color = StudioCyan.copy(alpha = 0.6f),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    }
                }
            }

            // Selection Chrome Bounding Box & Handles (rendered strictly on top of selected layer)
            if (selectedLayer != null) {
                SelectionChrome(
                    layer = selectedLayer,
                    canvasWidthDp = canvasWidthDp,
                    canvasHeightDp = canvasHeightDp,
                    canvasWidthPx = canvasWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    onUpdateTransform = { onUpdateTransform(selectedLayer.id, it) }
                )
            }
        }

        // --- FLOATING SELECTION QUICK ACTIONS BAR (Auto Fill, Fit, Center, Stretch, 0°, Delete) ---
        if (selectedLayer != null) {
            Surface(
                color = Color(0xF0111520),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.6f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .testTag("canvas_quick_action_bar")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // ⚡ Auto Fill 100% Button
                    Button(
                        onClick = onAutoFillSelected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("auto_fill_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Auto Fill",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Fit Frame
                    OutlinedButton(
                        onClick = onFitFrameSelected,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("fit_frame_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = StudioCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fit", fontSize = 10.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Center
                    OutlinedButton(
                        onClick = onCenterSelected,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("center_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterCenterFocus,
                            contentDescription = null,
                            tint = StudioCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Center", fontSize = 10.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Stretch Width
                    IconButton(
                        onClick = onStretchWidthSelected,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Straighten,
                            contentDescription = "Stretch to Canvas Width",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Stretch Height
                    IconButton(
                        onClick = onStretchHeightSelected,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Height,
                            contentDescription = "Stretch to Canvas Height",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Reset 0°
                    IconButton(
                        onClick = onResetRotationSelected,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateLeft,
                            contentDescription = "Reset Angle 0°",
                            tint = StudioAmber,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Delete Layer
                    IconButton(
                        onClick = onDeleteSelected,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Selected Layer",
                            tint = StudioRecordRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // --- FLOATING MENU CHROME TOGGLE BUTTON (Top-Right) ---
        // The canvas is always full screen; this shows/hides the floating
        // Top Strip + Sidebar overlay on top of it.
        Surface(
            color = if (isFullCanvasMode) Color.Black.copy(alpha = 0.75f) else StudioCyan,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, if (isFullCanvasMode) Color.White.copy(alpha = 0.2f) else Color.Transparent),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable { onToggleFullCanvas() }
                .testTag("stage_full_canvas_toggle")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isFullCanvasMode) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                    contentDescription = if (isFullCanvasMode) "Show Menu" else "Hide Menu",
                    tint = if (isFullCanvasMode) Color.White else Color.Black,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isFullCanvasMode) "Menu" else "Hide Menu",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFullCanvasMode) Color.White else Color.Black
                )
            }
        }

        // --- BOTTOM TIMELINE SCRUB BAR ---
        Surface(
            color = Color(0xD90E1016),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .fillMaxWidth(0.85f)
                .height(44.dp)
                .testTag("timeline_bar")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
            ) {
                // Play / Pause Icon Button
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isPlaying) StudioCyan else Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { /* Controlled via floating or sidebar */ }
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Current Time Display
                val currentSec = currentPositionMs / 1000
                val totalSec = project.durationMs / 1000
                Text(
                    text = String.format("%02d:%02d / %02d:%02d", currentSec / 60, currentSec % 60, totalSec / 60, totalSec % 60),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Interactive Timeline Slider
                val progress = if (project.durationMs > 0) {
                    (currentPositionMs.toFloat() / project.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Slider(
                    value = progress,
                    onValueChange = { frac ->
                        onSeek((frac * project.durationMs).toLong())
                    },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = StudioCyan,
                        activeTrackColor = StudioCyan,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )

                // Recording indicator pill if active
                if (isRecording) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(StudioRecordRed)
                    )
                }
            }
        }

        // --- HUD DIAGNOSTIC OVERLAY (Top-Left corner) ---
        if (showStatsOverlay) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.4f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "STUDIO ENGINE HUD",
                        color = StudioCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "FPS: ${stats.fps} · Res: ${stats.canvasResolution}",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "FrameTime: ${stats.frameTimeMs}ms · Decoder: ${stats.decoderType}",
                        color = StudioTextSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "Layers: ${project.layers.size} active · Latency: ${stats.latencyMs}ms",
                        color = StudioTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.LayerItemRenderer(
    layer: Layer,
    isSelected: Boolean,
    isPlaying: Boolean,
    wavePhase: Float,
    canvasWidthDp: Dp,
    canvasHeightDp: Dp,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onSelect: () -> Unit,
    onDoubleTap: () -> Unit,
    onUpdateTransform: (LayerTransform) -> Unit,
    onToggleVisibility: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onStopLayer: () -> Unit = {}
) {
    val t = layer.transform
    val layerWidth = canvasWidthDp * t.w
    val layerHeight = canvasHeightDp * t.h
    val layerLeft = canvasWidthDp * (t.cx - t.w / 2f)
    val layerTop = canvasHeightDp * (t.cy - t.h / 2f)
    // Media source frames (camera/video/screen) get the fast play/stop/eye handles.
    val isMediaSource = layer.type == LayerType.CAMERA || layer.type == LayerType.VIDEO || layer.type == LayerType.SCREEN

    Box(
        modifier = Modifier
            .offset(x = layerLeft, y = layerTop)
            .size(width = layerWidth, height = layerHeight)
            .graphicsLayer {
                rotationZ = t.rotationDeg
                alpha = if (layer.isVisible) layer.opacity else layer.opacity * 0.35f
            }
            .pointerInput(layer.id, layer.isLocked) {
                detectTapGestures(
                    onTap = { onSelect() },
                    onDoubleTap = { onDoubleTap() }
                )
            }
            .pointerInput(layer.id, layer.isLocked) {
                if (!layer.isLocked) {
                    detectDragGestures(
                        onDragStart = { onSelect() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaX = if (canvasWidthPx > 0) dragAmount.x / canvasWidthPx else 0f
                            val deltaY = if (canvasHeightPx > 0) dragAmount.y / canvasHeightPx else 0f
                            val newCx = (t.cx + deltaX).coerceIn(0f, 1f)
                            val newCy = (t.cy + deltaY).coerceIn(0f, 1f)
                            onUpdateTransform(t.copy(cx = newCx, cy = newCy))
                        }
                    )
                }
            }
            .clip(if (layer.type == LayerType.CAMERA) RoundedCornerShape(10.dp) else RoundedCornerShape(0.dp))
    ) {
        if (layer.isVisible) {
            when (layer.type) {
                LayerType.VIDEO -> VideoLayerVisual(layer, isPlaying, wavePhase)
                LayerType.CAMERA -> CameraPiPLayerVisual(layer, isPlaying, wavePhase)
                LayerType.IMAGE -> ImageOverlayVisual(layer)
                LayerType.SCREEN -> ScreenRecordVisual(layer, isPlaying, wavePhase)
                LayerType.TEXT -> TextOverlayVisual(layer)
            }
        } else {
            // Hidden placeholder so the frame stays reachable via its quick controls
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = StudioTextMuted,
                    modifier = Modifier.size((minOf(layerWidth, layerHeight).value * 0.22f).dp.coerceIn(14.dp, 28.dp))
                )
            }
        }

        // --- FAST SOURCE HANDLES: Play/Pause, Stop, Show/Hide ---
        // Always available directly on the frame so sources can be controlled
        // instantly, without opening the sidebar.
        if (isMediaSource) {
            LayerQuickControls(
                layer = layer,
                isSelected = isSelected,
                onToggleVisibility = onToggleVisibility,
                onTogglePlay = onTogglePlay,
                onStopLayer = onStopLayer,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
            )
        } else {
            // Non-media sources (image/text) still get a quick eye toggle.
            LayerVisibilityHandle(
                isVisible = layer.isVisible,
                onToggleVisibility = onToggleVisibility,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
            )
        }
    }
}

/**
 * Floating pill of quick-access handles rendered directly on top of a
 * camera/video/screen source frame: Play/Pause, Stop and Show/Hide.
 * Designed to be reachable & tappable instantly without opening the sidebar.
 */
@Composable
private fun LayerQuickControls(
    layer: Layer,
    isSelected: Boolean,
    onToggleVisibility: () -> Unit,
    onTogglePlay: () -> Unit,
    onStopLayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.62f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isSelected) StudioCyan.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f)),
        modifier = modifier.testTag("layer_quick_controls_${layer.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp)
        ) {
            QuickHandleButton(
                icon = if (layer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                tint = StudioCyan,
                contentDescription = if (layer.isPlaying) "Pause ${layer.name}" else "Play ${layer.name}",
                onClick = onTogglePlay,
                testTag = "layer_play_toggle_${layer.id}"
            )
            QuickHandleButton(
                icon = Icons.Default.Stop,
                tint = StudioRecordRed,
                contentDescription = "Stop ${layer.name}",
                onClick = onStopLayer,
                testTag = "layer_stop_button_${layer.id}"
            )
            QuickHandleButton(
                icon = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                tint = if (layer.isVisible) StudioTextPrimary else StudioTextMuted,
                contentDescription = if (layer.isVisible) "Hide ${layer.name}" else "Show ${layer.name}",
                onClick = onToggleVisibility,
                testTag = "layer_visibility_toggle_${layer.id}"
            )
        }
    }
}

@Composable
private fun LayerVisibilityHandle(
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.62f),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = modifier
    ) {
        IconButton(onClick = onToggleVisibility, modifier = Modifier.size(26.dp)) {
            Icon(
                imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (isVisible) "Hide layer" else "Show layer",
                tint = if (isVisible) StudioTextPrimary else StudioTextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun QuickHandleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .testTag(testTag)
            .size(26.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun RealVideoPlayer(
    uriString: String,
    isPlaying: Boolean,
    volume: Float,
    isMuted: Boolean,
    playbackSpeed: Float,
    fitMode: FitMode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var hasError by remember { mutableStateOf(false) }

    DisposableEffect(uriString) {
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            null
        }

        val player = MediaPlayer().apply {
            try {
                if (uri != null) {
                    setDataSource(context, uri)
                    isLooping = true
                    prepareAsync()
                }
            } catch (e: Exception) {
                hasError = true
            }
        }
        mediaPlayer = player

        onDispose {
            try {
                player.stop()
                player.release()
            } catch (e: Exception) {
                // ignore
            }
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer) {
        mediaPlayer?.let { player ->
            try {
                if (isPlaying && !player.isPlaying) {
                    player.start()
                } else if (!isPlaying && player.isPlaying) {
                    player.pause()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    LaunchedEffect(volume, isMuted, mediaPlayer) {
        mediaPlayer?.let { player ->
            try {
                val vol = if (isMuted) 0f else volume
                player.setVolume(vol, vol)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    LaunchedEffect(playbackSpeed, mediaPlayer) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            mediaPlayer?.let { player ->
                try {
                    player.playbackParams = player.playbackParams.setSpeed(playbackSpeed.coerceIn(0.25f, 3.0f))
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    if (hasError) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            Text("Unable to open media file", color = StudioTextMuted, fontSize = 11.sp)
        }
    } else {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            try {
                                mediaPlayer?.setSurface(Surface(surface))
                                if (isPlaying) {
                                    mediaPlayer?.start()
                                }
                            } catch (e: Exception) {
                                // ignore
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            try {
                                mediaPlayer?.setSurface(null)
                            } catch (e: Exception) {
                                // ignore
                            }
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        )
    }
}

@Composable
private fun VideoLayerVisual(layer: Layer, isPlaying: Boolean, wavePhase: Float) {
    if (layer.mediaUri != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            RealVideoPlayer(
                uriString = layer.mediaUri,
                isPlaying = isPlaying,
                volume = layer.volume,
                isMuted = layer.isMuted,
                playbackSpeed = layer.playbackSpeed,
                fitMode = layer.fitMode
            )
            // Play/Pause badge & clip name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = StudioCyan,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = layer.name,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        // Fallback demo clip graphics
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF131A2A))
        ) {
            // Animated game/reaction background graphics
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Background geometric lines & game scene representation
                drawRect(
                    color = Color(0xFF0D1424),
                    size = size
                )

                // Neon grid lines
                val spacing = w / 8f
                for (i in 0..8) {
                    drawLine(
                        color = Color(0xFF1E293B).copy(alpha = 0.5f),
                        start = Offset(i * spacing, 0f),
                        end = Offset(i * spacing, h),
                        strokeWidth = 1f
                    )
                }

                // Animated waveform when playing
                if (isPlaying) {
                    val waveOffset = (wavePhase * 0.05f) % 20f
                    for (x in 0 until w.toInt() step 6) {
                        val rad = (x + wavePhase * 2f) * 0.03f
                        val y = h * 0.5f + (sin(rad.toDouble()) * 20.0).toFloat()
                        drawCircle(
                            color = StudioCyan.copy(alpha = 0.4f),
                            radius = 1.5f,
                            center = Offset(x.toFloat(), y)
                        )
                    }
                }
            }

            // Play/Pause badge & clip name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = StudioCyan,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = layer.name,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CameraPiPLayerVisual(layer: Layer, isPlaying: Boolean, wavePhase: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(2.dp, StudioAmber, RoundedCornerShape(10.dp))
            .background(Color(0xFF181C24))
    ) {
        // Face silhouette / Live camera preview representation
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Studio backdrop glow
            drawCircle(
                color = Color(0xFF262D3D),
                radius = size.width * 0.45f,
                center = Offset(cx, cy)
            )

            // Head silhouette
            drawCircle(
                color = Color(0xFF384358),
                radius = size.width * 0.22f,
                center = Offset(cx, cy - size.height * 0.08f)
            )

            // Shoulders
            drawOval(
                color = Color(0xFF384358),
                topLeft = Offset(cx - size.width * 0.35f, cy + size.height * 0.12f),
                size = Size(size.width * 0.70f, size.height * 0.40f)
            )

            // Live audio bars indicator on camera
            if (isPlaying && !layer.isMuted) {
                val barW = 4f
                val spacing = 3f
                for (i in 0..4) {
                    val barH = (10f + (sin((wavePhase + i * 40f) * 0.1f) * 8f)).coerceAtLeast(4f)
                    drawRect(
                        color = StudioGreen,
                        topLeft = Offset(size.width - 24f + i * (barW + spacing), size.height - 18f - barH),
                        size = Size(barW, barH)
                    )
                }
            }
        }

        // "LIVE CAM" pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(StudioAmber)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "CAM 1",
                color = StudioAmber,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ImageOverlayVisual(layer: Layer) {
    if (layer.mediaUri != null) {
        AsyncImage(
            model = layer.mediaUri,
            contentDescription = layer.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Surface(
            color = Color(0xFF10B981).copy(alpha = 0.15f),
            border = BorderStroke(1.5.dp, StudioGreen),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = null,
                        tint = StudioGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "REACTION",
                        color = StudioGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenRecordVisual(layer: Layer, isPlaying: Boolean, wavePhase: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1B2F))
            .border(1.dp, StudioPurple, RoundedCornerShape(4.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = StudioPurple,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "Display Screen Stream",
                color = StudioPurple,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TextOverlayVisual(layer: Layer) {
    val textData = layer.textData ?: TextData()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = textData.text,
            color = Color(textData.colorHex),
            fontSize = textData.fontSizeSp.sp,
            fontWeight = if (textData.isBold) FontWeight.Black else FontWeight.Normal,
            textAlign = TextAlign.Center,
            style = LocalTextStyle.current.copy(
                shadow = if (textData.hasShadow) {
                    androidx.compose.ui.graphics.Shadow(
                        color = Color.Black,
                        offset = Offset(3f, 3f),
                        blurRadius = 6f
                    )
                } else null
            )
        )
    }
}

@Composable
private fun BoxScope.SelectionChrome(
    layer: Layer,
    canvasWidthDp: Dp,
    canvasHeightDp: Dp,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onUpdateTransform: (LayerTransform) -> Unit
) {
    val t = layer.transform
    val layerWidth = canvasWidthDp * t.w
    val layerHeight = canvasHeightDp * t.h
    val layerLeft = canvasWidthDp * (t.cx - t.w / 2f)
    val layerTop = canvasHeightDp * (t.cy - t.h / 2f)

    Box(
        modifier = Modifier
            .offset(x = layerLeft, y = layerTop)
            .size(width = layerWidth, height = layerHeight)
            .graphicsLayer {
                rotationZ = t.rotationDeg
            }
            .border(2.dp, StudioCyan, RoundedCornerShape(2.dp))
    ) {
        // Label Pill at top edge
        Surface(
            color = StudioCyan,
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 6.dp, bottomStart = 0.dp, bottomEnd = 6.dp),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = "${layer.type.name} • ${layer.name.take(14)}",
                color = Color.Black,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }

        // Center Move Handle (Explicit crosshair button for moving)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .pointerInput(layer.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaX = if (canvasWidthPx > 0) dragAmount.x / canvasWidthPx else 0f
                        val deltaY = if (canvasHeightPx > 0) dragAmount.y / canvasHeightPx else 0f
                        onUpdateTransform(
                            t.copy(
                                cx = (t.cx + deltaX).coerceIn(0f, 1f),
                                cy = (t.cy + deltaY).coerceIn(0f, 1f)
                            )
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = CircleShape,
                border = BorderStroke(1.dp, StudioCyan)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenWith,
                    contentDescription = "Move Layer",
                    tint = StudioCyan,
                    modifier = Modifier
                        .padding(5.dp)
                        .size(16.dp)
                )
            }
        }

        // --- 4 CORNER RESIZE HANDLES (Each anchors the opposite corner) ---

        // 1. Bottom-End (Bottom-Right): Anchors Top-Left
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(8.dp, 8.dp)
                .size(44.dp)
                .pointerInput(layer.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaW = if (canvasWidthPx > 0) dragAmount.x / canvasWidthPx else 0f
                        val deltaH = if (canvasHeightPx > 0) dragAmount.y / canvasHeightPx else 0f
                        val newW = (t.w + deltaW).coerceIn(0.08f, 2.0f)
                        val newH = (t.h + deltaH).coerceIn(0.08f, 2.0f)
                        val diffW = newW - t.w
                        val diffH = newH - t.h
                        val newCx = (t.cx + diffW / 2f).coerceIn(0f, 1f)
                        val newCy = (t.cy + diffH / 2f).coerceIn(0f, 1f)
                        onUpdateTransform(t.copy(w = newW, h = newH, cx = newCx, cy = newCy))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .background(StudioCyan, CircleShape)
                    .border(2.dp, Color.Black, CircleShape)
            )
        }

        // 2. Top-Start (Top-Left): Anchors Bottom-Right
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset((-8).dp, (-8).dp)
                .size(44.dp)
                .pointerInput(layer.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaW = if (canvasWidthPx > 0) -dragAmount.x / canvasWidthPx else 0f
                        val deltaH = if (canvasHeightPx > 0) -dragAmount.y / canvasHeightPx else 0f
                        val newW = (t.w + deltaW).coerceIn(0.08f, 2.0f)
                        val newH = (t.h + deltaH).coerceIn(0.08f, 2.0f)
                        val diffW = newW - t.w
                        val diffH = newH - t.h
                        val newCx = (t.cx - diffW / 2f).coerceIn(0f, 1f)
                        val newCy = (t.cy - diffH / 2f).coerceIn(0f, 1f)
                        onUpdateTransform(t.copy(w = newW, h = newH, cx = newCx, cy = newCy))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .background(StudioCyan, CircleShape)
                    .border(2.dp, Color.Black, CircleShape)
            )
        }

        // 3. Top-End (Top-Right): Anchors Bottom-Left
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(8.dp, (-8).dp)
                .size(44.dp)
                .pointerInput(layer.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaW = if (canvasWidthPx > 0) dragAmount.x / canvasWidthPx else 0f
                        val deltaH = if (canvasHeightPx > 0) -dragAmount.y / canvasHeightPx else 0f
                        val newW = (t.w + deltaW).coerceIn(0.08f, 2.0f)
                        val newH = (t.h + deltaH).coerceIn(0.08f, 2.0f)
                        val diffW = newW - t.w
                        val diffH = newH - t.h
                        val newCx = (t.cx + diffW / 2f).coerceIn(0f, 1f)
                        val newCy = (t.cy - diffH / 2f).coerceIn(0f, 1f)
                        onUpdateTransform(t.copy(w = newW, h = newH, cx = newCx, cy = newCy))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .background(StudioCyan, CircleShape)
                    .border(2.dp, Color.Black, CircleShape)
            )
        }

        // 4. Bottom-Start (Bottom-Left): Anchors Top-Right
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset((-8).dp, 8.dp)
                .size(44.dp)
                .pointerInput(layer.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaW = if (canvasWidthPx > 0) -dragAmount.x / canvasWidthPx else 0f
                        val deltaH = if (canvasHeightPx > 0) dragAmount.y / canvasHeightPx else 0f
                        val newW = (t.w + deltaW).coerceIn(0.08f, 2.0f)
                        val newH = (t.h + deltaH).coerceIn(0.08f, 2.0f)
                        val diffW = newW - t.w
                        val diffH = newH - t.h
                        val newCx = (t.cx - diffW / 2f).coerceIn(0f, 1f)
                        val newCy = (t.cy + diffH / 2f).coerceIn(0f, 1f)
                        onUpdateTransform(t.copy(w = newW, h = newH, cx = newCx, cy = newCy))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .background(StudioCyan, CircleShape)
                    .border(2.dp, Color.Black, CircleShape)
            )
        }

        // --- 4 EDGE STRETCH HANDLES (For 1-Dimensional horizontal/vertical sizing) ---

        // Center-End (Right Edge): Stretches Width
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(6.dp, 0.dp)
                .size(36.dp)
                .pointerInput(layer.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaW = if (canvasWidthPx > 0) dragAmount.x / canvasWidthPx else 0f
                        val newW = (t.w + deltaW).coerceIn(0.08f, 2.0f)
                        val diffW = newW - t.w
                        val newCx = (t.cx + diffW / 2f).coerceIn(0f, 1f)
                        onUpdateTransform(t.copy(w = newW, cx = newCx))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(18.dp)
                    .background(Color.White, RoundedCornerShape(3.dp))
                    .border(1.dp, Color.Black, RoundedCornerShape(3.dp))
            )
        }

        // Center-Start (Left Edge): Stretches Width
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset((-6).dp, 0.dp)
                .size(36.dp)
                .pointerInput(layer.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaW = if (canvasWidthPx > 0) -dragAmount.x / canvasWidthPx else 0f
                        val newW = (t.w + deltaW).coerceIn(0.08f, 2.0f)
                        val diffW = newW - t.w
                        val newCx = (t.cx - diffW / 2f).coerceIn(0f, 1f)
                        onUpdateTransform(t.copy(w = newW, cx = newCx))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(18.dp)
                    .background(Color.White, RoundedCornerShape(3.dp))
                    .border(1.dp, Color.Black, RoundedCornerShape(3.dp))
            )
        }

        // Bottom-Center (Bottom Edge): Stretches Height
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(0.dp, 6.dp)
                .size(36.dp)
                .pointerInput(layer.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaH = if (canvasHeightPx > 0) dragAmount.y / canvasHeightPx else 0f
                        val newH = (t.h + deltaH).coerceIn(0.08f, 2.0f)
                        val diffH = newH - t.h
                        val newCy = (t.cy + diffH / 2f).coerceIn(0f, 1f)
                        onUpdateTransform(t.copy(h = newH, cy = newCy))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(6.dp)
                    .background(Color.White, RoundedCornerShape(3.dp))
                    .border(1.dp, Color.Black, RoundedCornerShape(3.dp))
            )
        }

        // Top-Center (Top Edge): Stretches Height
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(0.dp, (-6).dp)
                .size(36.dp)
                .pointerInput(layer.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaH = if (canvasHeightPx > 0) -dragAmount.y / canvasHeightPx else 0f
                        val newH = (t.h + deltaH).coerceIn(0.08f, 2.0f)
                        val diffH = newH - t.h
                        val newCy = (t.cy - diffH / 2f).coerceIn(0f, 1f)
                        onUpdateTransform(t.copy(h = newH, cy = newCy))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(6.dp)
                    .background(Color.White, RoundedCornerShape(3.dp))
                    .border(1.dp, Color.Black, RoundedCornerShape(3.dp))
            )
        }

        // Interactive Amber Rotation Knob at top center (44dp touch target)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-40).dp)
                .size(44.dp)
                .pointerInput(layer.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = dragAmount.x * 0.9f
                        val newRot = (t.rotationDeg + delta) % 360f
                        val normalizedRot = if (newRot < 0f) newRot + 360f else newRot
                        onUpdateTransform(t.copy(rotationDeg = normalizedRot))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, StudioAmber)
                ) {
                    Text(
                        text = "${t.rotationDeg.toInt()}°",
                        color = StudioAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(StudioAmber, CircleShape)
                        .border(2.dp, Color.Black, CircleShape)
                )
            }
        }
    }
}
