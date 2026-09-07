package com.example

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AspectRatio
import com.example.model.TorchMode
import com.example.ui.components.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StudioDark
import com.example.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                StudioScreen()
            }
        }
    }
}

@Composable
fun StudioScreen(viewModel: StudioViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sidebarWidth = if (isLandscape) 240.dp else 260.dp

    // Toast notifications
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // Auto-rotate device orientation to match selected canvas aspect ratio
    LaunchedEffect(uiState.project.aspectRatio) {
        val activity = context as? Activity ?: return@LaunchedEffect
        when (uiState.project.aspectRatio) {
            AspectRatio.SIXTEEN_NINE -> {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            AspectRatio.NINE_SIXTEEN -> {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            AspectRatio.ONE_ONE -> {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // BackHandler: Close the floating menu/sidebar overlay (if open) before exiting.
    // The canvas itself is always full screen, so there's no separate mode to exit.
    BackHandler(enabled = uiState.isSidebarOpen) {
        viewModel.setSidebarOpen(false)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = StudioDark,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- FULL-SCREEN CANVAS BASE LAYER ---
            // The canvas always fills the entire available screen. The top strip
            // and sidebar are floating overlays on top of it (never resize it),
            // so the workspace is edge-to-edge at all times.
            StageView(
                project = uiState.project,
                selectedLayerId = uiState.selectedLayerId,
                isPlaying = uiState.isPlaying,
                currentPositionMs = uiState.currentPositionMs,
                isRecording = uiState.isRecording,
                showStatsOverlay = uiState.showStatsOverlay,
                stats = uiState.stats,
                isFullCanvasMode = !uiState.isSidebarOpen,
                onSelectLayer = { viewModel.selectLayer(it) },
                onUpdateTransform = { id, transform ->
                    viewModel.updateTransform(id, transform)
                },
                onDoubleTapText = {
                    viewModel.showTextEditorDialog(true)
                },
                onSeek = { viewModel.seekTo(it) },
                onToggleLayerVisibility = { viewModel.toggleLayerVisibility(it) },
                onToggleLayerPlaying = { viewModel.toggleLayerPlaying(it) },
                onStopLayer = { viewModel.stopLayerPlayback(it) },
                onToggleFullCanvas = { viewModel.toggleFullCanvasMode() },
                onAutoFillSelected = { viewModel.autoFillSelectedLayer() },
                onFitFrameSelected = { viewModel.fitSelectedToFrame() },
                onCenterSelected = { viewModel.centerSelectedLayer() },
                onStretchWidthSelected = { viewModel.stretchSelectedWidth() },
                onStretchHeightSelected = { viewModel.stretchSelectedHeight() },
                onResetRotationSelected = { uiState.selectedLayerId?.let { viewModel.resetLayerRotation(it) } },
                onDeleteSelected = { viewModel.removeSelectedLayer() },
                modifier = Modifier.fillMaxSize()
            )

            // Floating Transport Controls (Bottom-Right, always on top of the canvas).
            FloatingControls(
                isPlaying = uiState.isPlaying,
                isRecording = uiState.isRecording,
                recordDurationMs = uiState.recordDurationMs,
                isVisible = true,
                onPlayPause = { viewModel.togglePlayPause() },
                onStop = { viewModel.stop() },
                onRecord = { viewModel.toggleRecording() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            )

            // --- FLOATING MENU BUTTON (Top-Left) ---
            // Reveals the Top Strip + Sidebar overlay without ever resizing the canvas.
            AnimatedVisibility(
                visible = !uiState.isSidebarOpen,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                FloatingMenuButton(
                    onClick = { viewModel.setSidebarOpen(true) },
                    modifier = Modifier.padding(10.dp)
                )
            }

            // --- OVERLAY: TOP STRIP + SIDEBAR (floats above the canvas, doesn't resize it) ---
            AnimatedVisibility(
                visible = uiState.isSidebarOpen,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.fillMaxSize()
            ) {
                // Scrim to dismiss the overlay by tapping outside the sidebar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.setSidebarOpen(false) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.TopStart)
            ) {
                AnimatedVisibility(
                    visible = uiState.isSidebarOpen,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = tween(220)
                    ) + fadeIn(tween(200)),
                    exit = slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = tween(180)
                    ) + fadeOut(tween(150))
                ) {
                    TopStrip(
                        projectName = uiState.project.name,
                        aspectRatio = uiState.project.aspectRatio,
                        isSidebarOpen = uiState.isSidebarOpen,
                        isDirty = uiState.project.isDirty,
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        showStatsOverlay = uiState.showStatsOverlay,
                        isFullCanvasMode = !uiState.isSidebarOpen,
                        onToggleSidebar = { viewModel.toggleSidebar() },
                        onRenameClick = { viewModel.showRenameDialog(true) },
                        onAspectCycleClick = {
                            val nextAspect = when (uiState.project.aspectRatio) {
                                AspectRatio.SIXTEEN_NINE -> AspectRatio.NINE_SIXTEEN
                                AspectRatio.NINE_SIXTEEN -> AspectRatio.ONE_ONE
                                AspectRatio.ONE_ONE -> AspectRatio.SIXTEEN_NINE
                            }
                            viewModel.setAspectRatio(nextAspect)
                        },
                        onUndoClick = { viewModel.undo() },
                        onRedoClick = { viewModel.redo() },
                        onSaveClick = { viewModel.saveProject() },
                        onToggleStatsClick = { viewModel.toggleStatsOverlay() },
                        onQuickExportClick = { viewModel.quickExport() },
                        onToggleFullCanvas = { viewModel.toggleFullCanvasMode() },
                        onShowSaveExportGuide = { viewModel.setShowSaveExportExplainerDialog(true) },
                        onOpenSettingsClick = { viewModel.showDiagnosticsDialog(true) }
                    )
                }

                AnimatedVisibility(
                    visible = uiState.isSidebarOpen,
                    enter = slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(220)
                    ) + fadeIn(tween(200)),
                    exit = slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(180)
                    ) + fadeOut(tween(150))
                ) {
                    SidebarView(
                        project = uiState.project,
                        selectedLayerId = uiState.selectedLayerId,
                        audioSettings = uiState.audioSettings,
                        torchMode = uiState.torchMode,
                        isPlaying = uiState.isPlaying,
                        isRecording = uiState.isRecording,
                        isFullCanvasMode = !uiState.isSidebarOpen,
                        showStatsOverlay = uiState.showStatsOverlay,
                        expandedSectionIds = uiState.expandedSectionIds,
                        expandedItemIds = uiState.expandedItemIds,
                        onToggleSection = { viewModel.toggleSectionExpanded(it) },
                        onToggleItem = { viewModel.toggleItemExpanded(it) },
                        viewModel = viewModel,
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                            .shadow(12.dp)
                    )
                }
            }

            // Screen Light Overlay (Simulated white softbox fill for night recording)
            if (uiState.torchMode == TorchMode.SCREEN_LIGHT) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.85f))
                )
            }
        }

        // --- Modals & Dialogs ---

        if (uiState.showAudioMixer) {
            AudioMixerDialog(
                project = uiState.project,
                audioSettings = uiState.audioSettings,
                onMasterVolumeChange = { viewModel.setMasterVolume(it) },
                onMicGainChange = { viewModel.adjustMicGain(it - uiState.audioSettings.micGain) },
                onLayerVolumeChange = { id, vol ->
                    viewModel.adjustSourceVolume(id, vol - (uiState.project.layers.find { it.id == id }?.volume ?: 1f))
                },
                onToggleLayerMute = { viewModel.toggleLayerMute(it) },
                onToggleSolo = { viewModel.toggleSolo(it) },
                onDismiss = { viewModel.showAudioMixer(false) }
            )
        }

        if (uiState.showExportDialog) {
            ExportDialog(
                exportSettings = uiState.exportSettings,
                onStartExport = { viewModel.startExport(it) },
                onDismiss = { viewModel.showExportDialog(false) }
            )
        }

        if (uiState.isExporting || uiState.exportedSuccessPath != null) {
            ExportProgressDialog(
                progress = uiState.exportProgress,
                isComplete = uiState.exportedSuccessPath != null,
                successPath = uiState.exportedSuccessPath,
                onDismiss = { viewModel.dismissExportResult() }
            )
        }

        if (uiState.showLayerPropertiesDialog) {
            val selected = uiState.project.layers.find { it.id == uiState.selectedLayerId }
            if (selected != null) {
                LayerPropertiesDialog(
                    layer = selected,
                    onSave = { name, opacity, volume, speed ->
                        viewModel.updateLayerProperties(selected.id, name, opacity, volume, speed)
                        viewModel.showPropertiesDialog(false)
                    },
                    onDismiss = { viewModel.showPropertiesDialog(false) }
                )
            }
        }

        if (uiState.showTextEditorDialog) {
            val selectedTextLayer = uiState.project.layers.find {
                it.id == uiState.selectedLayerId && it.type == com.example.model.LayerType.TEXT
            }
            TextEditorDialog(
                initialText = selectedTextLayer?.textData?.text ?: "😱 UNBELIEVABLE TWIST!",
                onSave = { textData ->
                    if (selectedTextLayer != null) {
                        viewModel.updateTextData(selectedTextLayer.id, textData)
                    } else {
                        viewModel.addSource(com.example.model.LayerType.TEXT, textData.text)
                    }
                    viewModel.showTextEditorDialog(false)
                },
                onDismiss = { viewModel.showTextEditorDialog(false) }
            )
        }

        if (uiState.showRenameDialog) {
            RenameProjectDialog(
                currentName = uiState.project.name,
                onConfirm = {
                    viewModel.renameProject(it)
                    viewModel.showRenameDialog(false)
                },
                onDismiss = { viewModel.showRenameDialog(false) }
            )
        }

        if (uiState.showDiagnosticsDialog) {
            DiagnosticsDialog(
                onDismiss = { viewModel.showDiagnosticsDialog(false) }
            )
        }

        if (uiState.showSaveExportExplainerDialog) {
            SaveExportExplainerDialog(
                onSaveDraft = { viewModel.saveProject() },
                onExportVideo = { viewModel.quickExport() },
                onDismiss = { viewModel.setShowSaveExportExplainerDialog(false) }
            )
        }

        if (uiState.showStartupAspectRatioDialog) {
            StartupAspectRatioDialog(
                currentAspectRatio = uiState.project.aspectRatio,
                onSelectAspectRatio = { ratio ->
                    viewModel.setAspectRatio(ratio)
                    viewModel.setShowStartupAspectRatioDialog(false)
                },
                onDismiss = { viewModel.setShowStartupAspectRatioDialog(false) }
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Ahmed Reaction Studio ($name)", modifier = modifier)
}

/**
 * Small floating hamburger button pinned to the top-left of the full-screen
 * canvas. Reveals the Top Strip + Sidebar overlay without ever resizing or
 * pushing the canvas, so the workspace stays edge-to-edge at all times.
 */
@Composable
private fun FloatingMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open menu",
                tint = Color.White
            )
        }
    }
}
