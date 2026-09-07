package com.example.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.camera.view.PreviewView
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
import com.example.camera.CameraManager
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private enum class GestureMode { MOVE, RESIZE_CORNER, RESIZE_EDGE_H, RESIZE_EDGE_V, ROTATE }

/**
 * Holds the in-progress ("live") transform while the user drags / resizes / rotates a layer.
 * Frame-by-frame pointer deltas mutate this local state only, so the canvas updates smoothly
 * without repeatedly round-tripping through the ViewModel. The final transform is committed to
 * the ViewModel once at gesture end via [commit].
 */
private class CanvasGestureState {
    var activeLayerId by mutableStateOf<String?>(null)
    var liveTransform by mutableStateOf<LayerTransform?>(null)
    var mode by mutableStateOf(GestureMode.MOVE)
    var corner by mutableStateOf(CornerPosition.BOTTOM_RIGHT)
    var anchorX by mutableFloatStateOf(0f)
    var anchorY by mutableFloatStateOf(0f)
    var cornerX by mutableFloatStateOf(0f)
    var cornerY by mutableFloatStateOf(0f)

    fun beginMove(layer: Layer) {
        if (layer.isLocked) return
        activeLayerId = layer.id
        liveTransform = layer.transform
        mode = GestureMode.MOVE
    }

    fun beginResizeCorner(layer: Layer, corner: CornerPosition) {
        if (layer.isLocked) return
        val t = layer.transform
        activeLayerId = layer.id
        mode = GestureMode.RESIZE_CORNER
        this.corner = corner
        val sx = if (corner == CornerPosition.TOP_LEFT || corner == CornerPosition.BOTTOM_LEFT) -1f else 1f
        val sy = if (corner == CornerPosition.TOP_LEFT || corner == CornerPosition.TOP_RIGHT) -1f else 1f
        cornerX = t.cx + sx * t.w / 2f
        cornerY = t.cy + sy * t.h / 2f
        anchorX = t.cx - sx * t.w / 2f
        anchorY = t.cy - sy * t.h / 2f
        liveTransform = t
    }

    fun beginResizeEdgeH(layer: Layer) {
        if (layer.isLocked) return
        activeLayerId = layer.id
        liveTransform = layer.transform
        mode = GestureMode.RESIZE_EDGE_H
    }

    fun beginResizeEdgeV(layer: Layer) {
        if (layer.isLocked) return
        activeLayerId = layer.id
        liveTransform = layer.transform
        mode = GestureMode.RESIZE_EDGE_V
    }

    fun beginRotate(layer: Layer) {
        if (layer.isLocked) return
        activeLayerId = layer.id
        liveTransform = layer.transform
        mode = GestureMode.ROTATE
    }

    fun dragMove(dx: Float, dy: Float) {
        val t = liveTransform ?: return
        liveTransform = t.copy(
            cx = (t.cx + dx).coerceIn(0f, 1f),
            cy = (t.cy + dy).coerceIn(0f, 1f)
        )
    }

    fun dragResizeCorner(dx: Float, dy: Float) {
        val t = liveTransform ?: return
        val sx = if (corner == CornerPosition.TOP_LEFT || corner == CornerPosition.BOTTOM_LEFT) -1f else 1f
        val sy = if (corner == CornerPosition.TOP_LEFT || corner == CornerPosition.TOP_RIGHT) -1f else 1f
        var newX = cornerX + dx
        var newY = cornerY + dy
        // Keep the dragged corner on the correct side of the anchored corner (no flip) with a min size.
        newX = if (sx > 0f) newX.coerceAtLeast(anchorX + MIN_LAYER_SIZE) else newX.coerceAtMost(anchorX - MIN_LAYER_SIZE)
        newY = if (sy > 0f) newY.coerceAtLeast(anchorY + MIN_LAYER_SIZE) else newY.coerceAtMost(anchorY - MIN_LAYER_SIZE)
        // Keep the layer within the canvas bounds.
        newX = newX.coerceIn(0f, 1f)
        newY = newY.coerceIn(0f, 1f)
        cornerX = newX
        cornerY = newY
        // Anchor the opposite corner and derive center/size from the dragged corner.
        liveTransform = t.copy(
            w = abs(newX - anchorX),
            h = abs(newY - anchorY),
            cx = (newX + anchorX) / 2f,
            cy = (newY + anchorY) / 2f
        )
    }

    fun dragResizeEdgeH(deltaW: Float) {
        val t = liveTransform ?: return
        val newW = (t.w + deltaW).coerceIn(MIN_LAYER_SIZE, 2.0f)
        val diff = newW - t.w
        liveTransform = t.copy(w = newW, cx = (t.cx + diff / 2f).coerceIn(0f, 1f))
    }

    fun dragResizeEdgeV(deltaH: Float) {
        val t = liveTransform ?: return
        val newH = (t.h + deltaH).coerceIn(MIN_LAYER_SIZE, 2.0f)
        val diff = newH - t.h
        liveTransform = t.copy(h = newH, cy = (t.cy + diff / 2f).coerceIn(0f, 1f))
    }

    fun dragRotate(delta: Float) {
        val t = liveTransform ?: return
        var rot = (t.rotationDeg + delta) % 360f
        if (rot < 0f) rot += 360f
        liveTransform = t.copy(rotationDeg = rot)
    }

    fun commit(onUpdateTransform: (String, LayerTransform) -> Unit) {
        val id = activeLayerId
        val t = liveTransform
        if (id != null && t != null) {
            onUpdateTransform(id, t)
        }
        reset()
    }

    fun cancel() = reset()

    private fun reset() {
        activeLayerId = null
        liveTransform = null
    }
}

private const val MIN_LAYER_SIZE = 0.08f

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
    onToggleFullCanvas: () -> Unit = {},
    onAutoFillSelected: () -> Unit = {},
    onFitFrameSelected: () -> Unit = {},
    onCenterSelected: () -> Unit = {},
    onStretchWidthSelected: () -> Unit = {},
    onStretchHeightSelected: () -> Unit = {},
    onResetRotationSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onToggleLayerVisibility: (String) -> Unit = {},
    onToggleLayerPlaying: (String) -> Unit = {},
    onPlayPause: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Live frame ticker animation for simulated video playback motion.
    // Deliberately kept as a State<Float> that is only read *inside the Canvas draw scopes*
    // of the layer visuals (never unwrapped here in the StageView body). Reading the value
    // at this level used to recompose the whole stage tree on every animation frame — even
    // while idle and during drags — which made canvas interaction feel heavy/stuttery.
    // Draw-scope reads invalidate only the affected layers' redraw instead.
    val waveTransition = rememberInfiniteTransition(label = "video_motion")
    val wavePhase = waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    // Live gesture state: frame-by-frame drag/resize/rotate updates mutate this local state so the
    // canvas stays smooth; the final transform is committed to the ViewModel on gesture end.
    val gestureState = remember { CanvasGestureState() }

    // Layers rendered with the in-progress ("live") transform during a gesture.
    val displayLayers = project.layers.map { layer ->
        if (gestureState.activeLayerId == layer.id && gestureState.liveTransform != null) {
            layer.copy(transform = gestureState.liveTransform!!)
        } else {
            layer
        }
    }
    val selectedLayer = displayLayers.find { it.id == selectedLayerId }

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
        // Compute fitted canvas dimensions preserving project aspect ratio
        val targetRatio = project.aspectRatio.ratio
        val availW = if (isFullCanvasMode) maxWidth else (maxWidth - 8.dp).coerceAtLeast(100.dp)
        val availH = if (isFullCanvasMode) maxHeight else (maxHeight - 48.dp).coerceAtLeast(100.dp)

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
            // Render all visible layers from bottom to top
            val visibleLayers = displayLayers.filter { it.isVisible }

            // Empty canvas state (a visual hint only — never a layer)
            if (visibleLayers.isEmpty()) {
                EmptyCanvasState()
            }

            visibleLayers.forEach { layer ->
                LayerItemRenderer(
                    layer = layer,
                    isSelected = layer.id == selectedLayerId,
                    isPlaying = isPlaying,
                    wavePhase = wavePhase,
                    canvasWidthDp = canvasWidthDp,
                    canvasHeightDp = canvasHeightDp,
                    canvasWidthPx = canvasWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    gestureState = gestureState,
                    onSelect = { onSelectLayer(layer.id) },
                    onDoubleTap = {
                        if (layer.type == LayerType.TEXT) {
                            onDoubleTapText(layer.id)
                        }
                    },
                    onUpdateTransform = onUpdateTransform
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
                    gestureState = gestureState,
                    onUpdateTransform = onUpdateTransform,
                    onToggleVisibility = { onToggleLayerVisibility(selectedLayer.id) },
                    onTogglePlayPause = { onToggleLayerPlaying(selectedLayer.id) }
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

        // --- FLOATING FULL CANVAS TOGGLE BUTTON (Top-Right) ---
        Surface(
            color = if (isFullCanvasMode) StudioCyan else Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, if (isFullCanvasMode) Color.Transparent else Color.White.copy(alpha = 0.2f)),
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
                    imageVector = if (isFullCanvasMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullCanvasMode) "Exit Full Canvas" else "Full Canvas Mode",
                    tint = if (isFullCanvasMode) Color.Black else Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isFullCanvasMode) "Exit Full" else "Full Canvas",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFullCanvasMode) Color.Black else Color.White
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
                        .clickable { onPlayPause() }
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
private fun EmptyCanvasState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.06f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = StudioCyan.copy(alpha = 0.9f),
                modifier = Modifier
                    .padding(14.dp)
                    .size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Your canvas is ready",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Add a camera, video, image or text to begin.",
            color = StudioTextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SelectionQuickControls(
    layer: Layer,
    onToggleVisibility: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlayable = layer.type == LayerType.VIDEO ||
        layer.type == LayerType.CAMERA ||
        layer.type == LayerType.SCREEN

    Surface(
        color = Color(0xF0111520),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.5f)),
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            IconButton(
                onClick = onToggleVisibility,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("canvas_visibility_button")
            ) {
                Icon(
                    imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (layer.isVisible) "Hide source" else "Show source",
                    tint = if (layer.isVisible) Color.White else StudioTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (isPlayable) {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("canvas_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (layer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (layer.isPlaying) "Pause source" else "Play source",
                        tint = StudioCyan,
                        modifier = Modifier.size(16.dp)
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
    wavePhase: State<Float>,
    canvasWidthDp: Dp,
    canvasHeightDp: Dp,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    gestureState: CanvasGestureState,
    onSelect: () -> Unit,
    onDoubleTap: () -> Unit,
    onUpdateTransform: (String, LayerTransform) -> Unit
) {
    val t = layer.transform
    val currentLayer by rememberUpdatedState(layer)
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
                alpha = layer.opacity
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
                        onDragStart = {
                            onSelect()
                            gestureState.beginMove(currentLayer)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaX = if (canvasWidthPx > 0) dragAmount.x / canvasWidthPx else 0f
                            val deltaY = if (canvasHeightPx > 0) dragAmount.y / canvasHeightPx else 0f
                            gestureState.dragMove(deltaX, deltaY)
                        },
                        onDragEnd = { gestureState.commit(onUpdateTransform) },
                        onDragCancel = { gestureState.cancel() }
                    )
                }
            }
            .clip(if (layer.type == LayerType.CAMERA) RoundedCornerShape(10.dp) else RoundedCornerShape(0.dp))
    ) {
        when (layer.type) {
            LayerType.VIDEO -> VideoLayerVisual(layer, isPlaying, wavePhase)
            LayerType.CAMERA -> CameraPiPLayerVisual(layer, isPlaying, wavePhase)
            LayerType.IMAGE -> ImageOverlayVisual(layer)
            LayerType.SCREEN -> ScreenRecordVisual(layer)
            LayerType.TEXT -> TextOverlayVisual(layer)
        }
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
private fun VideoLayerVisual(layer: Layer, isPlaying: Boolean, wavePhase: State<Float>) {
    // A source only plays while the timeline transport plays AND the source itself is not
    // paused via its contextual Play/Pause control (layer.isPlaying). The quick controls on
    // the selection chrome toggle layer.isPlaying through toggleLayerPlaying — this is the
    // actual rendering gate so that control has a real, visible effect.
    val sourceActive = isPlaying && layer.isPlaying
    if (layer.mediaUri != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            RealVideoPlayer(
                uriString = layer.mediaUri,
                isPlaying = sourceActive,
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
                    imageVector = if (sourceActive) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = if (sourceActive) StudioCyan else StudioTextSecondary,
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
            // Animated game/reaction background graphics. wavePhase is read inside this draw
            // scope only, so the continuous tick redraws this layer without recomposing it.
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
                if (sourceActive) {
                    val phase = wavePhase.value
                    for (x in 0 until w.toInt() step 6) {
                        val rad = (x + phase * 2f) * 0.03f
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
                    imageVector = if (sourceActive) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = if (sourceActive) StudioCyan else StudioTextSecondary,
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
private fun CameraPiPLayerVisual(layer: Layer, isPlaying: Boolean, wavePhase: State<Float>) {
    // Live audio bars only animate when the timeline plays, the source is not paused through
    // its contextual Play/Pause control, and the mic is not muted.
    val sourceActive = isPlaying && layer.isPlaying
    val facing = layer.cameraFacing
    val facingLabel = when (facing) {
        CameraFacing.FRONT -> "FRONT"
        CameraFacing.BACK -> "BACK"
        null -> "CAM"
    }
    val facingColor = when (facing) {
        CameraFacing.FRONT -> StudioAmber
        CameraFacing.BACK -> Color(0xFFEF4444)
        null -> StudioAmber
    }
    // Track whether camera is actually bound and producing frames
    var cameraBound by remember(layer.id) { mutableStateOf(CameraManager.isCameraActive(layer.id)) }
    var cameraFailed by remember(layer.id) { mutableStateOf(false) }

    // Periodically retry binding if camera isn't bound yet (handles async CameraManager init)
    LaunchedEffect(layer.id, facing) {
        if (facing == null || cameraFailed) return@LaunchedEffect
        // Try up to 10 times over 5 seconds to bind the camera
        for (attempt in 0..9) {
            if (CameraManager.isCameraActive(layer.id)) {
                cameraBound = true
                return@LaunchedEffect
            }
            if (CameraManager.isInitialized) {
                // CameraManager is ready but camera isn't bound - the AndroidView's factory/update
                // will handle the actual binding since it has the PreviewView reference
                cameraBound = true // Optimistic - the AndroidView will bind on next recompose
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(500L)
        }
        // After 10 attempts, mark as failed
        cameraFailed = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(2.dp, facingColor, RoundedCornerShape(10.dp))
            .background(Color(0xFF181C24))
    ) {
        if (facing != null && !cameraFailed) {
            // Real camera preview via CameraX PreviewView
            AndroidView(
                factory = { context ->
                    val previewView = PreviewView(context).apply {
                        this.scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    }
                    // Bind camera immediately when PreviewView is created
                    if (CameraManager.isInitialized) {
                        val success = CameraManager.bindCamera(layer.id, facing, previewView)
                        cameraBound = success
                        if (!success) cameraFailed = true
                    }
                    previewView
                },
                update = { previewView ->
                    // If camera wasn't bound initially (CameraManager wasn't ready), try again
                    if (!CameraManager.isCameraActive(layer.id) && CameraManager.isInitialized && !cameraFailed) {
                        val success = CameraManager.bindCamera(layer.id, facing, previewView)
                        cameraBound = success
                        if (!success) cameraFailed = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback: Face silhouette / placeholder when camera not available or failed
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

                // Live audio bars indicator on camera (wavePhase read in draw scope only)
                if (sourceActive && !layer.isMuted) {
                    val phase = wavePhase.value
                    val barW = 4f
                    val spacing = 3f
                    for (i in 0..4) {
                        val barH = (10f + (sin((phase + i * 40f) * 0.1f) * 8f)).coerceAtLeast(4f)
                        drawRect(
                            color = StudioGreen,
                            topLeft = Offset(size.width - 24f + i * (barW + spacing), size.height - 18f - barH),
                            size = Size(barW, barH)
                        )
                    }
                }
            }
        }

        // Torch indicator
        if (layer.isTorchOn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.Yellow)
            )
        }

        // Camera facing label pill
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
                    .background(if (cameraBound) StudioGreen else facingColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = facingLabel,
                color = facingColor,
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
private fun ScreenRecordVisual(layer: Layer) {
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
    gestureState: CanvasGestureState,
    onUpdateTransform: (String, LayerTransform) -> Unit,
    onToggleVisibility: () -> Unit,
    onTogglePlayPause: () -> Unit
) {
    val t = layer.transform
    val currentLayer by rememberUpdatedState(layer)
    val layerWidth = canvasWidthDp * t.w
    val layerHeight = canvasHeightDp * t.h
    val layerLeft = canvasWidthDp * (t.cx - t.w / 2f)
    val layerTop = canvasHeightDp * (t.cy - t.h / 2f)

    // Highest point of the chrome after rotation, measured in canvas dp. Used to decide where
    // the source quick controls can live: when the layer sits flush with the canvas top (Auto
    // Fill / Fit / background layers) the area above it is clipped by the rounded canvas, so
    // the controls dock inside the source's top-right corner instead of becoming invisible.
    val rotationRad = (t.rotationDeg % 360f) * (kotlin.math.PI.toFloat() / 180f)
    val absSinRot = abs(sin(rotationRad))
    val absCosRot = abs(cos(rotationRad))
    val chromeVisualTopDp =
        layerTop + layerHeight * (1f - absCosRot) / 2f - layerWidth * absSinRot / 2f
    val quickControlsAbove = chromeVisualTopDp >= 44.dp

    Box(
        modifier = Modifier
            .offset(x = layerLeft, y = layerTop)
            .size(width = layerWidth, height = layerHeight)
            .graphicsLayer {
                rotationZ = t.rotationDeg
            }
            .border(2.dp, if (layer.isLocked) StudioAmber else StudioCyan, RoundedCornerShape(2.dp))
    ) {
        // Label Pill at top edge
        Surface(
            color = if (layer.isLocked) StudioAmber else StudioCyan,
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 6.dp, bottomStart = 0.dp, bottomEnd = 6.dp),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = "${if (layer.isLocked) "🔒 " else ""}${layer.type.name} • ${layer.name.take(14)}",
                color = Color.Black,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }

        // Handles and the rotation knob are only interactive (and only shown) for unlocked
        // layers — a locked source must not display controls that silently do nothing.
        if (!layer.isLocked) {
        // Center Move Handle (Explicit crosshair button for moving)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .pointerInput(layer.id) {
                    detectDragGestures(
                        onDragStart = { gestureState.beginMove(currentLayer) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaX = if (canvasWidthPx > 0) dragAmount.x / canvasWidthPx else 0f
                            val deltaY = if (canvasHeightPx > 0) dragAmount.y / canvasHeightPx else 0f
                            gestureState.dragMove(deltaX, deltaY)
                        },
                        onDragEnd = { gestureState.commit(onUpdateTransform) },
                        onDragCancel = { gestureState.cancel() }
                    )
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
                    detectDragGestures(
                        onDragStart = { gestureState.beginResizeCorner(currentLayer, CornerPosition.BOTTOM_RIGHT) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaX = if (canvasWidthPx > 0) dragAmount.x / canvasWidthPx else 0f
                            val deltaY = if (canvasHeightPx > 0) dragAmount.y / canvasHeightPx else 0f
                            gestureState.dragResizeCorner(deltaX, deltaY)
                        },
                        onDragEnd = { gestureState.commit(onUpdateTransform) },
                        onDragCancel = { gestureState.cancel() }
                    )
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
                    detectDragGestures(
                        onDragStart = { gestureState.beginResizeCorner(currentLayer, CornerPosition.TOP_LEFT) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaX = if (canvasWidthPx > 0) dragAmount.x / canvasWidthPx else 0f
                            val deltaY = if (canvasHeightPx > 0) dragAmount.y / canvasHeightPx else 0f
                            gestureState.dragResizeCorner(deltaX, deltaY)
                        },
                        onDragEnd = { gestureState.commit(onUpdateTransform) },
                        onDragCancel = { gestureState.cancel() }
                    )
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
                    detectDragGestures(
                        onDragStart = { gestureState.beginResizeCorner(currentLayer, CornerPosition.TOP_RIGHT) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaX = if (canvasWidthPx > 0) dragAmount.x / canvasWidthPx else 0f
                            val deltaY = if (canvasHeightPx > 0) dragAmount.y / canvasHeightPx else 0f
                            gestureState.dragResizeCorner(deltaX, deltaY)
                        },
                        onDragEnd = { gestureState.commit(onUpdateTransform) },
                        onDragCancel = { gestureState.cancel() }
                    )
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
                    detectDragGestures(
                        onDragStart = { gestureState.beginResizeCorner(currentLayer, CornerPosition.BOTTOM_LEFT) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaX = if (canvasWidthPx > 0) dragAmount.x / canvasWidthPx else 0f
                            val deltaY = if (canvasHeightPx > 0) dragAmount.y / canvasHeightPx else 0f
                            gestureState.dragResizeCorner(deltaX, deltaY)
                        },
                        onDragEnd = { gestureState.commit(onUpdateTransform) },
                        onDragCancel = { gestureState.cancel() }
                    )
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
                    detectDragGestures(
                        onDragStart = { gestureState.beginResizeEdgeH(currentLayer) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaW = if (canvasWidthPx > 0) dragAmount.x / canvasWidthPx else 0f
                            gestureState.dragResizeEdgeH(deltaW)
                        },
                        onDragEnd = { gestureState.commit(onUpdateTransform) },
                        onDragCancel = { gestureState.cancel() }
                    )
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
                    detectDragGestures(
                        onDragStart = { gestureState.beginResizeEdgeH(currentLayer) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaW = if (canvasWidthPx > 0) -dragAmount.x / canvasWidthPx else 0f
                            gestureState.dragResizeEdgeH(deltaW)
                        },
                        onDragEnd = { gestureState.commit(onUpdateTransform) },
                        onDragCancel = { gestureState.cancel() }
                    )
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
                    detectDragGestures(
                        onDragStart = { gestureState.beginResizeEdgeV(currentLayer) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaH = if (canvasHeightPx > 0) dragAmount.y / canvasHeightPx else 0f
                            gestureState.dragResizeEdgeV(deltaH)
                        },
                        onDragEnd = { gestureState.commit(onUpdateTransform) },
                        onDragCancel = { gestureState.cancel() }
                    )
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
                    detectDragGestures(
                        onDragStart = { gestureState.beginResizeEdgeV(currentLayer) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaH = if (canvasHeightPx > 0) -dragAmount.y / canvasHeightPx else 0f
                            gestureState.dragResizeEdgeV(deltaH)
                        },
                        onDragEnd = { gestureState.commit(onUpdateTransform) },
                        onDragCancel = { gestureState.cancel() }
                    )
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
                    detectDragGestures(
                        onDragStart = { gestureState.beginRotate(currentLayer) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            gestureState.dragRotate(dragAmount.x * 0.9f)
                        },
                        onDragEnd = { gestureState.commit(onUpdateTransform) },
                        onDragCancel = { gestureState.cancel() }
                    )
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
        } else {
            // Locked source placeholder: position/size/rotation are locked, so replace the
            // move handle with a clear lock chip (unlock from the sidebar SOURCE CONTROLS).
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, StudioAmber, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Layer locked — unlock from the sidebar to move or resize",
                    tint = StudioAmber,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Contextual source quick controls: Show/Hide + Play/Pause (reuses existing ViewModel
        // operations; visibility/playback remain available on locked sources). The pill floats
        // above the source's top-right corner when the canvas has room, and docks inside the
        // corner otherwise so it is never clipped away or left looking like a dead control.
        SelectionQuickControls(
            layer = layer,
            onToggleVisibility = onToggleVisibility,
            onTogglePlayPause = onTogglePlayPause,
            modifier = if (quickControlsAbove) {
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = (-38).dp)
            } else {
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
            }
        )
    }
}
