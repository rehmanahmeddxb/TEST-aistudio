package com.example.model

import java.util.UUID

enum class LayerType {
    CAMERA,
    VIDEO,
    IMAGE,
    SCREEN,
    TEXT
}

enum class CameraFacing {
    FRONT,
    BACK
}

enum class FitMode {
    FIT,   // Letterbox / contain
    FILL   // Cover / crop
}

enum class CornerPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

data class LayerTransform(
    val cx: Float = 0.5f,        // Center X (0..1 fraction of canvas)
    val cy: Float = 0.5f,        // Center Y (0..1 fraction of canvas)
    val w: Float = 1.0f,         // Width fraction of canvas
    val h: Float = 1.0f,         // Height fraction of canvas
    val rotationDeg: Float = 0f  // Degrees
)

data class TextData(
    val text: String = "Reaction Title",
    val colorHex: Long = 0xFFFFFFFF,
    val fontSizeSp: Float = 28f,
    val hasShadow: Boolean = true,
    val isBold: Boolean = true
)

data class Layer(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val type: LayerType,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val isMuted: Boolean = false,
    val volume: Float = 1.0f,
    val opacity: Float = 1.0f,
    val playbackSpeed: Float = 1.0f,
    val isPlaying: Boolean = true,
    val fitMode: FitMode = FitMode.FIT,
    val isBackground: Boolean = false,
    val transform: LayerTransform = LayerTransform(),
    val textData: TextData? = null,
    val accentColor: Long = 0xFF38BDF8,
    val durationMs: Long = 180000L,
    val sampleTag: String = "",
    val mediaUri: String? = null,
    val cameraFacing: CameraFacing? = null,
    val isTorchOn: Boolean = false
)

enum class AspectRatio(val label: String, val ratio: Float, val w: Int, val h: Int) {
    SIXTEEN_NINE("16:9 Landscape", 16f / 9f, 1920, 1080),
    NINE_SIXTEEN("9:16 Portrait", 9f / 16f, 1080, 1920),
    ONE_ONE("1:1 Square", 1f, 1080, 1080)
}

enum class CanvasBackground(val label: String, val colorLong: Long) {
    DARK("Dark", 0xFF0E1016),
    BLACK("Black", 0xFF000000),
    WHITE("White", 0xFFFFFFFF),
    ORANGE("Orange", 0xFFE65100),
    NAVY("Navy", 0xFF0D1B2A),
    GREEN("Green", 0xFF1B4332),
    PURPLE("Purple", 0xFF3A0CA3)
}

enum class TorchMode(val label: String) {
    OFF("Off"),
    FRONT("Front Torch"),
    BACK("Back Torch"),
    BOTH("Both Torches"),
    SCREEN_LIGHT("Screen Light")
}

data class AudioSettings(
    val micGain: Float = 1.0f,
    val masterVolume: Float = 1.0f,
    val isMicMuted: Boolean = false,
    val soloLayerId: String? = null
)

data class ExportSettings(
    val resolution: String = "720p (HD)",
    val fps: Int = 30,
    val codec: String = "H.264 / AVC",
    val bitrateMbps: Float = 8.0f
)

data class StatsInfo(
    val fps: Int = 60,
    val frameTimeMs: Float = 16.4f,
    val activeLayers: Int = 3,
    val decoderType: String = "HW MediaCodec + OES",
    val canvasResolution: String = "1920x1080",
    val latencyMs: Int = 12
)

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Ahmed Reaction Studio",
    val durationMs: Long = 204000L, // 3 mins 24 secs
    val aspectRatio: AspectRatio = AspectRatio.SIXTEEN_NINE,
    val background: CanvasBackground = CanvasBackground.DARK,
    val layers: List<Layer> = emptyList(),
    val isDirty: Boolean = false
)
