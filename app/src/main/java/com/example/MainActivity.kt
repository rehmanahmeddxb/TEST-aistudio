package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.camera.CameraManager
import com.example.model.*
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
                var showSplash by remember { mutableStateOf(true) }
                Box(modifier = Modifier.fillMaxSize()) {
                    // The studio initializes underneath immediately — the splash never delays startup.
                    StudioScreen(splashVisible = showSplash)
                    if (showSplash) {
                        AmsSplashScreen(onFinished = { showSplash = false })
                    }
                }
            }
        }
    }
}

@Composable
fun StudioScreen(viewModel: StudioViewModel = viewModel(), splashVisible: Boolean = false) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sidebarWidth = if (isLandscape) 240.dp else 260.dp
    val lifecycleOwner = LocalLifecycleOwner.current

    // Camera permission handling
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        cameraPermissionGranted = isGranted
        if (isGranted) {
            // Initialize CameraManager once permission is granted
            CameraManager.initialize(context, lifecycleOwner)
        }
    }

    // Check/request camera permission on composition
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        cameraPermissionGranted = hasPermission
        if (hasPermission) {
            CameraManager.initialize(context, lifecycleOwner)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Bind cameras when camera layers are added/changed
    LaunchedEffect(uiState.project.layers) {
        val cameraLayers = uiState.project.layers.filter { it.type == LayerType.CAMERA }
        for (layer in cameraLayers) {
            val facing = layer.cameraFacing ?: continue
            if (!CameraManager.isCameraActive(layer.id)) {
                if (cameraPermissionGranted) {
                    // Camera will be bound when PreviewView is created in StageView
                    // We just need to ensure the provider is available
                    CameraManager.ensureProvider(context)
                }
            }
        }
        // Cleanup cameras for removed layers
        val validLayerIds = uiState.project.layers.map { it.id }.toSet()
        CameraManager.cleanupMissingLayers(validLayerIds)
    }

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

    // BackHandler: Exit full canvas mode or close sidebar if open before exiting
    BackHandler(enabled = uiState.isFullCanvasMode || uiState.isSidebarOpen) {
        if (uiState.isFullCanvasMode) {
            viewModel.toggleFullCanvasMode()
        } else if (uiState.isSidebarOpen) {
            viewModel.setSidebarOpen(false)
        }
    }

    // Inside the canvas workspace with Studio chrome hidden, Back first restores the floating
    // timeline + transport (chrome); a second Back then exits full canvas via the handler above.
    BackHandler(enabled = uiState.isFullCanvasMode && !uiState.showStudioChrome) {
        viewModel.toggleStudioChrome()
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
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Strip (48dp height) — Studio chrome: hidden while the canvas owns the
                // full workspace; the eye control restores it inside the workspace.
                if (!uiState.isFullCanvasMode || uiState.showStudioChrome) {
                    TopStrip(
                        projectName = uiState.project.name,
                        aspectRatio = uiState.project.aspectRatio,
                        isSidebarOpen = uiState.isSidebarOpen,
                        isDirty = uiState.project.isDirty,
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        showStatsOverlay = uiState.showStatsOverlay,
                        isFullCanvasMode = uiState.isFullCanvasMode,
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

                // Studio Workspace Row: [Sidebar] + [Canvas Stage with Floating Transport Controls]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Collapsible Hierarchical Sidebar (Studio chrome — hidden while the canvas
                    // owns the full workspace)
                    AnimatedVisibility(
                        visible = uiState.isSidebarOpen && (!uiState.isFullCanvasMode || uiState.showStudioChrome),
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
                            isFullCanvasMode = uiState.isFullCanvasMode,
                            showStatsOverlay = uiState.showStatsOverlay,
                            expandedSectionIds = uiState.expandedSectionIds,
                            expandedItemIds = uiState.expandedItemIds,
                            onToggleSection = { viewModel.toggleSectionExpanded(it) },
                            onToggleItem = { viewModel.toggleItemExpanded(it) },
                            viewModel = viewModel,
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight()
                        )
                    }

                    // Canvas Stage (Takes remaining width and real estate)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        StageView(
                            project = uiState.project,
                            selectedLayerId = uiState.selectedLayerId,
                            isPlaying = uiState.isPlaying,
                            currentPositionMs = uiState.currentPositionMs,
                            isRecording = uiState.isRecording,
                            recordDurationMs = uiState.recordDurationMs,
                            showStatsOverlay = uiState.showStatsOverlay,
                            stats = uiState.stats,
                            isFullCanvasMode = uiState.isFullCanvasMode,
                            showStudioChrome = uiState.showStudioChrome,
                            onSelectLayer = { viewModel.selectLayer(it) },
                            onUpdateTransform = { id, transform ->
                                viewModel.updateTransform(id, transform)
                            },
                            onDoubleTapText = {
                                viewModel.showTextEditorDialog(true)
                            },
                            onSeek = { viewModel.seekTo(it) },
                            onToggleFullCanvas = { viewModel.toggleFullCanvasMode() },
                            onToggleStudioChrome = { viewModel.toggleStudioChrome() },
                            onAutoFillSelected = { viewModel.autoFillSelectedLayer() },
                            onFitFrameSelected = { viewModel.fitSelectedToFrame() },
                            onCenterSelected = { viewModel.centerSelectedLayer() },
                            onStretchWidthSelected = { viewModel.stretchSelectedWidth() },
                            onStretchHeightSelected = { viewModel.stretchSelectedHeight() },
                            onResetRotationSelected = { uiState.selectedLayerId?.let { viewModel.resetLayerRotation(it) } },
                            onDeleteSelected = { viewModel.removeSelectedLayer() },
                            onToggleLayerVisibility = { viewModel.toggleLayerVisibility(it) },
                            onToggleLayerPlaying = { viewModel.toggleLayerPlaying(it) },
                            onPlayPause = { viewModel.togglePlayPause() }
                        )

                        // Floating Transport Controls (Bottom-Right on canvas) — Studio chrome:
                        // hidden in the canvas workspace; while recording without chrome the
                        // canvas shows only a small REC indicator (see StageView).
                        FloatingControls(
                            isPlaying = uiState.isPlaying,
                            isRecording = uiState.isRecording,
                            recordDurationMs = uiState.recordDurationMs,
                            isVisible = !uiState.isFullCanvasMode || uiState.showStudioChrome,
                            onPlayPause = { viewModel.togglePlayPause() },
                            onStop = { viewModel.stop() },
                            onRecord = { viewModel.toggleRecording() },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 64.dp)
                        )
                    }
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

        // Defer the startup dialog until the splash has finished so it doesn't appear above it.
        if (uiState.showStartupAspectRatioDialog && !splashVisible) {
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
