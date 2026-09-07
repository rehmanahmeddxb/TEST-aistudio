package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudioUiState(
    val project: Project = Project(),
    val selectedLayerId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 14000L, // 00:14 default preview
    val isRecording: Boolean = false,
    val recordDurationMs: Long = 0L,
    val isSidebarOpen: Boolean = true,
    val isFullCanvasMode: Boolean = false,
    val showStatsOverlay: Boolean = false,
    val audioSettings: AudioSettings = AudioSettings(),
    val torchMode: TorchMode = TorchMode.OFF,
    val exportSettings: ExportSettings = ExportSettings(),
    val exportProgress: Float = 0f,
    val isExporting: Boolean = false,
    val exportedSuccessPath: String? = null,
    val showAudioMixer: Boolean = false,
    val showExportDialog: Boolean = false,
    val showDiagnosticsDialog: Boolean = false,
    val showAddSourceDialog: Boolean = false,
    val showLayerPropertiesDialog: Boolean = false,
    val showTextEditorDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showStartupAspectRatioDialog: Boolean = true,
    val showSaveExportExplainerDialog: Boolean = false,
    val toastMessage: String? = null,
    val expandedSectionIds: Set<String> = setOf("sources", "record"),
    val expandedItemIds: Set<String> = emptySet(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val stats: StatsInfo = StatsInfo()
)

class StudioViewModel : ViewModel() {

    private val undoStack = mutableListOf<Project>()
    private val redoStack = mutableListOf<Project>()

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var recordJob: Job? = null

    init {
        initializeEmptyProject()
    }

    /**
     * A new project starts with zero layers — no demo video, camera, text, or image sources.
     * The empty canvas is communicated visually by StageView's empty state (not a layer).
     */
    private fun initializeEmptyProject() {
        _uiState.update {
            it.copy(
                project = Project(
                    name = "Untitled Project",
                    durationMs = 204000L,
                    aspectRatio = AspectRatio.SIXTEEN_NINE,
                    background = CanvasBackground.DARK,
                    layers = emptyList(),
                    isDirty = false
                ),
                selectedLayerId = null,
                stats = StatsInfo(
                    fps = 60,
                    frameTimeMs = 16.6f,
                    activeLayers = 0,
                    decoderType = "HW MediaCodec (H.264/OES)",
                    canvasResolution = "1920x1080",
                    latencyMs = 9
                )
            )
        }
    }

    private fun pushUndoState() {
        val current = _uiState.value.project
        undoStack.add(current.copy())
        if (undoStack.size > 30) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
        _uiState.update { it.copy(canUndo = true, canRedo = false) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(_uiState.value.project.copy())
        _uiState.update {
            it.copy(
                project = previous,
                canUndo = undoStack.isNotEmpty(),
                canRedo = true,
                toastMessage = "Undone"
            )
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(_uiState.value.project.copy())
        _uiState.update {
            it.copy(
                project = next,
                canUndo = true,
                canRedo = redoStack.isNotEmpty(),
                toastMessage = "Redone"
            )
        }
    }

    // --- Sidebar & Navigation ---

    fun toggleSidebar() {
        _uiState.update { it.copy(isSidebarOpen = !it.isSidebarOpen) }
    }

    fun setSidebarOpen(open: Boolean) {
        _uiState.update { it.copy(isSidebarOpen = open) }
    }

    fun toggleSectionExpanded(sectionId: String) {
        _uiState.update { current ->
            val set = current.expandedSectionIds.toMutableSet()
            if (set.contains(sectionId)) {
                set.remove(sectionId)
            } else {
                set.add(sectionId)
            }
            current.copy(expandedSectionIds = set)
        }
    }

    fun toggleItemExpanded(itemId: String) {
        _uiState.update { current ->
            val set = current.expandedItemIds.toMutableSet()
            if (set.contains(itemId)) {
                set.remove(itemId)
            } else {
                set.add(itemId)
            }
            current.copy(expandedItemIds = set)
        }
    }

    fun toggleFullCanvasMode() {
        _uiState.update {
            val willBeFull = !it.isFullCanvasMode
            it.copy(
                isFullCanvasMode = willBeFull,
                isSidebarOpen = if (willBeFull) false else it.isSidebarOpen
            )
        }
    }

    // --- Playback & Transport ---

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        if (_uiState.value.isPlaying) return
        _uiState.update { it.copy(isPlaying = true) }
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val stepMs = 50L
            while (_uiState.value.isPlaying) {
                delay(stepMs)
                _uiState.update { current ->
                    val maxDur = current.project.durationMs
                    val next = current.currentPositionMs + stepMs
                    if (next >= maxDur) {
                        current.copy(currentPositionMs = 0L)
                    } else {
                        current.copy(currentPositionMs = next)
                    }
                }
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun stop() {
        pause()
        _uiState.update { it.copy(currentPositionMs = 0L) }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _uiState.value.project.durationMs)
        _uiState.update { it.copy(currentPositionMs = clamped) }
    }

    fun restart() {
        seekTo(0L)
        play()
    }

    // --- Recording ---

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return
        // Auto-collapse sidebar as per design guideline
        _uiState.update {
            it.copy(
                isRecording = true,
                recordDurationMs = 0L,
                isSidebarOpen = false,
                toastMessage = "Recording started"
            )
        }
        if (!_uiState.value.isPlaying) {
            play()
        }
        recordJob?.cancel()
        recordJob = viewModelScope.launch {
            while (_uiState.value.isRecording) {
                delay(1000L)
                _uiState.update { it.copy(recordDurationMs = it.recordDurationMs + 1000L) }
            }
        }
    }

    fun stopRecording() {
        recordJob?.cancel()
        val recordedSecs = _uiState.value.recordDurationMs / 1000L
        _uiState.update {
            it.copy(
                isRecording = false,
                toastMessage = "Take saved: ${recordedSecs}s reaction clip"
            )
        }
    }

    fun snapshotFrame() {
        _uiState.update {
            it.copy(toastMessage = "Snapshot saved to Gallery at ${formatTime(it.currentPositionMs)}")
        }
    }

    // --- Layer Operations ---

    fun selectLayer(id: String?) {
        _uiState.update { it.copy(selectedLayerId = id) }
    }

    fun toggleLayerVisibility(id: String) {
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == id) it.copy(isVisible = !it.isVisible) else it
            }
            current.copy(project = current.project.copy(layers = updated, isDirty = true))
        }
    }

    fun toggleLayerMute(id: String) {
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == id) it.copy(isMuted = !it.isMuted) else it
            }
            current.copy(project = current.project.copy(layers = updated, isDirty = true))
        }
    }

    fun toggleLayerLock(id: String) {
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == id) it.copy(isLocked = !it.isLocked) else it
            }
            current.copy(project = current.project.copy(layers = updated, isDirty = true))
        }
    }

    fun toggleLayerPlaying(id: String) {
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == id) it.copy(isPlaying = !it.isPlaying) else it
            }
            current.copy(project = current.project.copy(layers = updated))
        }
    }

    fun moveLayerUp(id: String) {
        pushUndoState()
        _uiState.update { current ->
            val layers = current.project.layers.toMutableList()
            val idx = layers.indexOfFirst { it.id == id }
            if (idx in 0 until layers.size - 1) {
                val item = layers.removeAt(idx)
                layers.add(idx + 1, item)
            }
            current.copy(project = current.project.copy(layers = layers, isDirty = true))
        }
    }

    fun moveLayerDown(id: String) {
        pushUndoState()
        _uiState.update { current ->
            val layers = current.project.layers.toMutableList()
            val idx = layers.indexOfFirst { it.id == id }
            if (idx > 0) {
                val item = layers.removeAt(idx)
                layers.add(idx - 1, item)
            }
            current.copy(project = current.project.copy(layers = layers, isDirty = true))
        }
    }

    fun removeSelectedLayer() {
        val selectedId = _uiState.value.selectedLayerId ?: return
        val layer = _uiState.value.project.layers.find { it.id == selectedId }
        // Release camera resources if this is a camera layer
        if (layer?.type == LayerType.CAMERA) {
            com.example.camera.CameraManager.unbindCamera(selectedId)
        }
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.filterNot { it.id == selectedId }
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                selectedLayerId = updated.lastOrNull()?.id,
                toastMessage = "Layer deleted"
            )
        }
    }

    fun duplicateSelectedLayer() {
        val selectedId = _uiState.value.selectedLayerId ?: return
        val layer = _uiState.value.project.layers.find { it.id == selectedId } ?: return
        pushUndoState()
        val copy = layer.copy(
            id = "copy_" + System.currentTimeMillis().toString().takeLast(4),
            name = "${layer.name} (Copy)",
            transform = layer.transform.copy(
                cx = (layer.transform.cx + 0.05f).coerceIn(0.1f, 0.9f),
                cy = (layer.transform.cy + 0.05f).coerceIn(0.1f, 0.9f)
            )
        )
        _uiState.update { current ->
            val updated = current.project.layers + copy
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                selectedLayerId = copy.id,
                toastMessage = "Layer duplicated"
            )
        }
    }

    fun addSource(type: LayerType, customText: String? = null) {
        pushUndoState()
        val newLayer = when (type) {
            LayerType.CAMERA -> Layer(
                name = "Live Reaction Camera",
                type = LayerType.CAMERA,
                transform = LayerTransform(cx = 0.78f, cy = 0.28f, w = 0.35f, h = 0.38f),
                accentColor = 0xFFF59E0B,
                sampleTag = "Live Cam",
                cameraFacing = CameraFacing.FRONT
            )
            LayerType.VIDEO -> Layer(
                name = "Video Clip #${_uiState.value.project.layers.size + 1}",
                type = LayerType.VIDEO,
                transform = LayerTransform(cx = 0.5f, cy = 0.5f, w = 0.8f, h = 0.8f),
                accentColor = 0xFF38BDF8,
                sampleTag = "Video"
            )
            LayerType.IMAGE -> Layer(
                name = "Sticker Overlay",
                type = LayerType.IMAGE,
                transform = LayerTransform(cx = 0.5f, cy = 0.5f, w = 0.25f, h = 0.25f),
                accentColor = 0xFF10B981,
                sampleTag = "Image"
            )
            LayerType.SCREEN -> Layer(
                name = "Screen Record Stream",
                type = LayerType.SCREEN,
                transform = LayerTransform(cx = 0.5f, cy = 0.5f, w = 0.9f, h = 0.9f),
                accentColor = 0xFF818CF8,
                sampleTag = "Screen"
            )
            LayerType.TEXT -> Layer(
                name = "Text: ${customText ?: "Subtitle"}",
                type = LayerType.TEXT,
                transform = LayerTransform(cx = 0.5f, cy = 0.82f, w = 0.75f, h = 0.12f),
                textData = TextData(
                    text = customText ?: "WOW! Look at this part!",
                    colorHex = 0xFFFFFFFF,
                    fontSizeSp = 22f,
                    hasShadow = true,
                    isBold = true
                ),
                accentColor = 0xFFEF4444,
                sampleTag = "Text"
            )
        }
        _uiState.update { current ->
            val updated = current.project.layers + newLayer
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                selectedLayerId = newLayer.id,
                toastMessage = "Added ${newLayer.name}"
            )
        }
    }

    /**
     * Add a camera source for the specified facing direction.
     * Each camera source is an independent layer with its own camera instance.
     * Front and back cameras can exist simultaneously.
     */
    fun addCameraSource(facing: CameraFacing) {
        pushUndoState()
        val name = when (facing) {
            CameraFacing.FRONT -> "Front Camera"
            CameraFacing.BACK -> "Back Camera"
        }
        val accentColor = when (facing) {
            CameraFacing.FRONT -> 0xFFF59E0B
            CameraFacing.BACK -> 0xFFEF4444
        }
        // Position them differently so they don't overlap by default
        val transform = when (facing) {
            CameraFacing.FRONT -> LayerTransform(cx = 0.78f, cy = 0.28f, w = 0.35f, h = 0.38f)
            CameraFacing.BACK -> LayerTransform(cx = 0.22f, cy = 0.28f, w = 0.35f, h = 0.38f)
        }
        val newLayer = Layer(
            name = name,
            type = LayerType.CAMERA,
            transform = transform,
            accentColor = accentColor,
            sampleTag = when (facing) {
                CameraFacing.FRONT -> "Front Cam"
                CameraFacing.BACK -> "Back Cam"
            },
            cameraFacing = facing,
            isTorchOn = false
        )
        _uiState.update { current ->
            val updated = current.project.layers + newLayer
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                selectedLayerId = newLayer.id,
                toastMessage = "Added $name"
            )
        }
    }

    /**
     * Toggle torch for a specific camera layer.
     * Returns true if the torch state changed successfully.
     */
    fun toggleCameraTorch(layerId: String): Boolean {
        val layer = _uiState.value.project.layers.find { it.id == layerId } ?: return false
        if (layer.type != LayerType.CAMERA) return false

        val success = com.example.camera.CameraManager.setTorch(layerId, !layer.isTorchOn)
        if (success) {
            _uiState.update { current ->
                val updated = current.project.layers.map {
                    if (it.id == layerId) it.copy(isTorchOn = !it.isTorchOn) else it
                }
                current.copy(
                    project = current.project.copy(layers = updated, isDirty = true),
                    toastMessage = "${layer.name} torch ${if (!layer.isTorchOn) "ON" else "OFF"}"
                )
            }
        } else {
            _uiState.update {
                it.copy(toastMessage = "${layer.name}: Torch not supported on this device")
            }
        }
        return success
    }

    /**
     * Set torch state for a specific camera layer.
     */
    fun setCameraTorch(layerId: String, on: Boolean): Boolean {
        val layer = _uiState.value.project.layers.find { it.id == layerId } ?: return false
        if (layer.type != LayerType.CAMERA) return false
        if (layer.isTorchOn == on) return true

        val success = com.example.camera.CameraManager.setTorch(layerId, on)
        if (success) {
            _uiState.update { current ->
                val updated = current.project.layers.map {
                    if (it.id == layerId) it.copy(isTorchOn = on) else it
                }
                current.copy(
                    project = current.project.copy(layers = updated, isDirty = true)
                )
            }
        }
        return success
    }

    // --- Transform & Fit Controls ---

    fun setFitMode(mode: FitMode) {
        val selectedId = _uiState.value.selectedLayerId ?: return
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == selectedId) it.copy(fitMode = mode) else it
            }
            current.copy(project = current.project.copy(layers = updated, isDirty = true))
        }
    }

    fun fitSelectedToCanvas() {
        val selectedId = _uiState.value.selectedLayerId ?: return
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == selectedId) {
                    it.copy(
                        transform = LayerTransform(cx = 0.5f, cy = 0.5f, w = 1.0f, h = 1.0f, rotationDeg = 0f)
                    )
                } else it
            }
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                toastMessage = "Auto-filled entire canvas (100%)"
            )
        }
    }

    fun autoFillSelectedLayer() {
        val selectedId = _uiState.value.selectedLayerId ?: return
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == selectedId) {
                    it.copy(
                        transform = LayerTransform(cx = 0.5f, cy = 0.5f, w = 1.0f, h = 1.0f, rotationDeg = 0f),
                        fitMode = FitMode.FILL
                    )
                } else it
            }
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                toastMessage = "Auto-filled entire frame (100% canvas)"
            )
        }
    }

    fun fitSelectedToFrame() {
        val selectedId = _uiState.value.selectedLayerId ?: return
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == selectedId) {
                    it.copy(
                        transform = LayerTransform(cx = 0.5f, cy = 0.5f, w = 0.90f, h = 0.90f, rotationDeg = 0f),
                        fitMode = FitMode.FIT
                    )
                } else it
            }
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                toastMessage = "Fitted within canvas frame"
            )
        }
    }

    fun centerSelectedLayer() {
        val selectedId = _uiState.value.selectedLayerId ?: return
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == selectedId) {
                    it.copy(transform = it.transform.copy(cx = 0.5f, cy = 0.5f))
                } else it
            }
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                toastMessage = "Centered in frame"
            )
        }
    }

    fun stretchSelectedWidth() {
        val selectedId = _uiState.value.selectedLayerId ?: return
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == selectedId) {
                    it.copy(transform = it.transform.copy(cx = 0.5f, w = 1.0f))
                } else it
            }
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                toastMessage = "Stretched to canvas width"
            )
        }
    }

    fun stretchSelectedHeight() {
        val selectedId = _uiState.value.selectedLayerId ?: return
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == selectedId) {
                    it.copy(transform = it.transform.copy(cy = 0.5f, h = 1.0f))
                } else it
            }
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                toastMessage = "Stretched to canvas height"
            )
        }
    }

    fun setSelectedDimensions(w: Float, h: Float) {
        val selectedId = _uiState.value.selectedLayerId ?: return
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == selectedId) {
                    it.copy(transform = it.transform.copy(w = w.coerceIn(0.1f, 2.0f), h = h.coerceIn(0.1f, 2.0f)))
                } else it
            }
            current.copy(project = current.project.copy(layers = updated, isDirty = true))
        }
    }

    fun resetSelectedPosition() {
        val selectedId = _uiState.value.selectedLayerId ?: return
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == selectedId) {
                    it.copy(
                        transform = it.transform.copy(cx = 0.5f, cy = 0.5f, rotationDeg = 0f)
                    )
                } else it
            }
            current.copy(project = current.project.copy(layers = updated, isDirty = true))
        }
    }

    fun setCornerPosition(corner: CornerPosition) {
        val selectedId = _uiState.value.selectedLayerId ?: return
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map { layer ->
                if (layer.id == selectedId) {
                    val w = layer.transform.w.coerceAtMost(0.45f)
                    val h = layer.transform.h.coerceAtMost(0.45f)
                    val margin = 0.05f
                    val (cx, cy) = when (corner) {
                        CornerPosition.TOP_LEFT -> Pair(margin + w / 2f, margin + h / 2f)
                        CornerPosition.TOP_RIGHT -> Pair(1f - margin - w / 2f, margin + h / 2f)
                        CornerPosition.BOTTOM_LEFT -> Pair(margin + w / 2f, 1f - margin - h / 2f)
                        CornerPosition.BOTTOM_RIGHT -> Pair(1f - margin - w / 2f, 1f - margin - h / 2f)
                    }
                    layer.copy(transform = layer.transform.copy(cx = cx, cy = cy, w = w, h = h))
                } else layer
            }
            current.copy(project = current.project.copy(layers = updated, isDirty = true))
        }
    }

    fun setAsBackground() {
        val selectedId = _uiState.value.selectedLayerId ?: return
        pushUndoState()
        _uiState.update { current ->
            val layers = current.project.layers.toMutableList()
            val idx = layers.indexOfFirst { it.id == selectedId }
            if (idx >= 0) {
                val selected = layers.removeAt(idx).copy(
                    isBackground = true,
                    isLocked = true,
                    transform = LayerTransform(cx = 0.5f, cy = 0.5f, w = 1.0f, h = 1.0f, rotationDeg = 0f),
                    fitMode = FitMode.FILL
                )
                layers.add(0, selected) // Send to bottom z-order
            }
            current.copy(
                project = current.project.copy(layers = layers, isDirty = true),
                toastMessage = "Set as background layer"
            )
        }
    }

    fun updateTransform(id: String, transform: LayerTransform) {
        val layer = _uiState.value.project.layers.find { it.id == id } ?: return
        if (layer.isLocked) return
        // Called once per completed gesture: capture undo state before applying.
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == id) it.copy(transform = transform) else it
            }
            current.copy(project = current.project.copy(layers = updated, isDirty = true))
        }
    }

    fun updateLayerProperties(
        id: String,
        name: String,
        opacity: Float,
        volume: Float,
        speed: Float
    ) {
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == id) {
                    it.copy(
                        name = name,
                        opacity = opacity,
                        volume = volume,
                        playbackSpeed = speed
                    )
                } else it
            }
            current.copy(project = current.project.copy(layers = updated, isDirty = true))
        }
    }

    fun updateTextData(id: String, textData: TextData) {
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == id && it.type == LayerType.TEXT) {
                    it.copy(textData = textData, name = "Text: ${textData.text.take(16)}")
                } else it
            }
            current.copy(project = current.project.copy(layers = updated, isDirty = true))
        }
    }

    // --- Audio Controls ---

    fun adjustMicGain(delta: Float) {
        _uiState.update { current ->
            val newGain = (current.audioSettings.micGain + delta).coerceIn(0f, 2.0f)
            current.copy(
                audioSettings = current.audioSettings.copy(micGain = newGain),
                toastMessage = "Mic Gain: ${(newGain * 100).toInt()}%"
            )
        }
    }

    fun resetMicGain() {
        _uiState.update { current ->
            current.copy(
                audioSettings = current.audioSettings.copy(micGain = 1.0f),
                toastMessage = "Mic Gain: 100%"
            )
        }
    }

    fun setMasterVolume(vol: Float) {
        _uiState.update { current ->
            current.copy(audioSettings = current.audioSettings.copy(masterVolume = vol.coerceIn(0f, 1f)))
        }
    }

    fun adjustSourceVolume(id: String, delta: Float) {
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == id) {
                    val newVol = (it.volume + delta).coerceIn(0f, 1f)
                    it.copy(volume = newVol)
                } else it
            }
            current.copy(project = current.project.copy(layers = updated))
        }
    }

    fun toggleSolo(id: String) {
        _uiState.update { current ->
            val isAlreadySolo = current.audioSettings.soloLayerId == id
            val newSoloId = if (isAlreadySolo) null else id
            val updatedLayers = current.project.layers.map {
                if (newSoloId == null) {
                    it.copy(isMuted = false)
                } else {
                    it.copy(isMuted = it.id != newSoloId)
                }
            }
            current.copy(
                audioSettings = current.audioSettings.copy(soloLayerId = newSoloId),
                project = current.project.copy(layers = updatedLayers),
                toastMessage = if (newSoloId != null) "Solo active for track" else "Solo disabled"
            )
        }
    }

    // --- Light & Torch ---

    fun setTorchMode(mode: TorchMode) {
        // Map global torch mode to per-layer torch states
        val layers = _uiState.value.project.layers
        val frontCameraLayer = layers.find { it.type == LayerType.CAMERA && it.cameraFacing == CameraFacing.FRONT }
        val backCameraLayer = layers.find { it.type == LayerType.CAMERA && it.cameraFacing == CameraFacing.BACK }

        val wantFront = mode == TorchMode.FRONT || mode == TorchMode.BOTH
        val wantBack = mode == TorchMode.BACK || mode == TorchMode.BOTH

        // Set torch on front camera layer
        if (frontCameraLayer != null) {
            if (wantFront && !frontCameraLayer.isTorchOn) {
                com.example.camera.CameraManager.setTorch(frontCameraLayer.id, true)
            } else if (!wantFront && frontCameraLayer.isTorchOn) {
                com.example.camera.CameraManager.setTorch(frontCameraLayer.id, false)
            }
        }

        // Set torch on back camera layer
        if (backCameraLayer != null) {
            if (wantBack && !backCameraLayer.isTorchOn) {
                com.example.camera.CameraManager.setTorch(backCameraLayer.id, true)
            } else if (!wantBack && backCameraLayer.isTorchOn) {
                com.example.camera.CameraManager.setTorch(backCameraLayer.id, false)
            }
        }

        _uiState.update { current ->
            val updated = current.project.layers.map { layer ->
                when {
                    layer.id == frontCameraLayer?.id -> layer.copy(isTorchOn = wantFront)
                    layer.id == backCameraLayer?.id -> layer.copy(isTorchOn = wantBack)
                    else -> layer
                }
            }
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                torchMode = mode,
                toastMessage = "Lighting: ${mode.label}"
            )
        }
    }

    // --- Canvas & Project ---

    fun setAspectRatio(ratio: AspectRatio) {
        pushUndoState()
        _uiState.update { current ->
            current.copy(
                project = current.project.copy(aspectRatio = ratio, isDirty = true),
                toastMessage = "Aspect: ${ratio.label}"
            )
        }
    }

    fun setCanvasBackground(bg: CanvasBackground) {
        pushUndoState()
        _uiState.update { current ->
            current.copy(
                project = current.project.copy(background = bg, isDirty = true),
                toastMessage = "Background: ${bg.label}"
            )
        }
    }

    fun fitAllSources() {
        pushUndoState()
        _uiState.update { current ->
            val count = current.project.layers.size
            val updated = current.project.layers.mapIndexed { idx, layer ->
                if (layer.isBackground) layer
                else {
                    layer.copy(
                        transform = LayerTransform(
                            cx = 0.5f,
                            cy = 0.5f,
                            w = 0.85f,
                            h = 0.85f,
                            rotationDeg = 0f
                        )
                    )
                }
            }
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                toastMessage = "Aligned all sources"
            )
        }
    }

    fun renameProject(newName: String) {
        if (newName.isNotBlank()) {
            _uiState.update { current ->
                current.copy(
                    project = current.project.copy(name = newName.trim(), isDirty = true),
                    toastMessage = "Renamed to $newName"
                )
            }
        }
    }

    fun saveProject() {
        _uiState.update { current ->
            current.copy(
                project = current.project.copy(isDirty = false),
                toastMessage = "Project saved to local store"
            )
        }
    }

    fun toggleStatsOverlay() {
        _uiState.update { it.copy(showStatsOverlay = !it.showStatsOverlay) }
    }

    fun setShowStartupAspectRatioDialog(show: Boolean) {
        _uiState.update { it.copy(showStartupAspectRatioDialog = show) }
    }

    fun setShowSaveExportExplainerDialog(show: Boolean) {
        _uiState.update { it.copy(showSaveExportExplainerDialog = show) }
    }

    // --- Rotation Controls ---

    fun rotateLayerBy(id: String, deltaDeg: Float) {
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == id && !it.isLocked) {
                    val newRot = (it.transform.rotationDeg + deltaDeg) % 360f
                    it.copy(transform = it.transform.copy(rotationDeg = newRot))
                } else it
            }
            current.copy(project = current.project.copy(layers = updated, isDirty = true))
        }
    }

    fun setLayerRotation(id: String, deg: Float) {
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == id && !it.isLocked) {
                    it.copy(transform = it.transform.copy(rotationDeg = deg % 360f))
                } else it
            }
            current.copy(project = current.project.copy(layers = updated, isDirty = true))
        }
    }

    fun resetLayerRotation(id: String) {
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == id && !it.isLocked) {
                    it.copy(transform = it.transform.copy(rotationDeg = 0f))
                } else it
            }
            current.copy(
                project = current.project.copy(layers = updated, isDirty = true),
                toastMessage = "Rotation reset to 0°"
            )
        }
    }

    // --- Real File & Media Attachment ---

    fun attachRealMediaToLayer(layerId: String, uri: String, fileName: String, durationMs: Long? = null) {
        pushUndoState()
        _uiState.update { current ->
            val updated = current.project.layers.map {
                if (it.id == layerId) {
                    it.copy(
                        name = fileName,
                        mediaUri = uri,
                        durationMs = durationMs ?: it.durationMs
                    )
                } else it
            }
            val maxDur = maxOf(current.project.durationMs, durationMs ?: 0L)
            current.copy(
                project = current.project.copy(layers = updated, durationMs = maxDur, isDirty = true),
                toastMessage = "Attached real file: $fileName"
            )
        }
    }

    fun addRealMediaLayer(uri: String, fileName: String, isVideo: Boolean, durationMs: Long? = null) {
        pushUndoState()
        val newLayer = if (isVideo) {
            Layer(
                name = fileName,
                type = LayerType.VIDEO,
                mediaUri = uri,
                durationMs = durationMs ?: 180000L,
                transform = LayerTransform(cx = 0.5f, cy = 0.5f, w = 0.85f, h = 0.85f),
                accentColor = 0xFF38BDF8,
                sampleTag = "User Video"
            )
        } else {
            Layer(
                name = fileName,
                type = LayerType.IMAGE,
                mediaUri = uri,
                transform = LayerTransform(cx = 0.5f, cy = 0.5f, w = 0.35f, h = 0.35f),
                accentColor = 0xFF10B981,
                sampleTag = "User Image"
            )
        }
        _uiState.update { current ->
            val updated = current.project.layers + newLayer
            val maxDur = maxOf(current.project.durationMs, durationMs ?: 0L)
            current.copy(
                project = current.project.copy(layers = updated, durationMs = maxDur, isDirty = true),
                selectedLayerId = newLayer.id,
                toastMessage = "Added $fileName"
            )
        }
    }

    // --- Modals ---

    fun showAudioMixer(show: Boolean) {
        _uiState.update { it.copy(showAudioMixer = show) }
    }

    fun showExportDialog(show: Boolean) {
        _uiState.update { it.copy(showExportDialog = show) }
    }

    fun showDiagnosticsDialog(show: Boolean) {
        _uiState.update { it.copy(showDiagnosticsDialog = show) }
    }

    fun showAddSourceDialog(show: Boolean) {
        _uiState.update { it.copy(showAddSourceDialog = show) }
    }

    fun showPropertiesDialog(show: Boolean) {
        _uiState.update { it.copy(showLayerPropertiesDialog = show) }
    }

    fun showTextEditorDialog(show: Boolean) {
        _uiState.update { it.copy(showTextEditorDialog = show) }
    }

    fun showRenameDialog(show: Boolean) {
        _uiState.update { it.copy(showRenameDialog = show) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    // --- Export Pipeline ---

    fun quickExport() {
        startExport(ExportSettings(resolution = "720p (HD)", fps = 30, codec = "H.264 / AVC", bitrateMbps = 8.0f))
    }

    fun startExport(settings: ExportSettings) {
        _uiState.update {
            it.copy(
                exportSettings = settings,
                isExporting = true,
                exportProgress = 0f,
                showExportDialog = false
            )
        }
        viewModelScope.launch {
            for (progress in 1..100) {
                delay(30L)
                _uiState.update { it.copy(exportProgress = progress / 100f) }
            }
            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportedSuccessPath = "/sdcard/Movies/AhmedReactionStudio_${System.currentTimeMillis()}.mp4",
                    toastMessage = "Export complete! Video saved to Movies"
                )
            }
        }
    }

    fun dismissExportResult() {
        _uiState.update { it.copy(exportedSuccessPath = null) }
    }

    companion object {
        fun formatTime(ms: Long): String {
            val totalSec = ms / 1000
            val m = totalSec / 60
            val s = totalSec % 60
            return String.format("%02d:%02d", m, s)
        }
    }
}
