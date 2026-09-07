package com.example.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.model.CameraFacing
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Singleton CameraManager that manages multiple simultaneous camera instances.
 * Each camera layer gets its own Camera instance keyed by layer ID.
 * Supports independent torch control per camera.
 */
object CameraManager {

    private const val TAG = "CameraManager"

    /**
     * Holds the state for a single active camera instance.
     */
    data class CameraInstance(
        val layerId: String,
        val facing: CameraFacing,
        var camera: Camera?,
        var preview: Preview?,
        var isTorchOn: Boolean = false
    )

    // Active camera instances keyed by layer ID
    private val activeCameras = ConcurrentHashMap<String, CameraInstance>()

    private var cameraProvider: ProcessCameraProvider? = null
    private var mainExecutor: Executor? = null
    private var lifecycleOwner: LifecycleOwner? = null

    /**
     * Initialize the CameraManager. Must be called once from a LifecycleOwner context.
     */
    fun initialize(context: Context, lifecycleOwner: LifecycleOwner) {
        this.mainExecutor = ContextCompat.getMainExecutor(context)
        this.lifecycleOwner = lifecycleOwner
        // Eagerly get the camera provider so bindCamera can use it immediately
        val executor = this.mainExecutor
        if (executor != null && cameraProvider == null) {
            try {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try {
                        cameraProvider = future.get()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get ProcessCameraProvider", e)
                    }
                }, executor)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request ProcessCameraProvider", e)
            }
        }
    }

    /**
     * Returns whether the CameraX provider has been initialized.
     */
    val isInitialized: Boolean
        get() = cameraProvider != null && lifecycleOwner != null

    /**
     * Ensure the camera provider is available. Returns true if ready.
     */
    suspend fun ensureProvider(context: Context): Boolean {
        if (cameraProvider != null) return true
        // The manager must have been initialised (executor + lifecycle owner) first.
        if (mainExecutor == null || lifecycleOwner == null) return false
        return try {
            val provider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider = provider
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get ProcessCameraProvider", e)
            false
        }
    }

    /**
     * Bind a camera preview for the given layer.
     * Returns true if the camera was successfully bound.
     */
    fun bindCamera(
        layerId: String,
        facing: CameraFacing,
        previewView: PreviewView
    ): Boolean {
        val provider = cameraProvider ?: run {
            Log.e(TAG, "CameraProvider not initialized")
            return false
        }
        val lo = lifecycleOwner ?: run {
            Log.e(TAG, "LifecycleOwner not set")
            return false
        }

        // Unbind any existing camera for this layer first
        unbindCamera(layerId)

        val selector = when (facing) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        return try {
            val camera = provider.bindToLifecycle(lo, selector, preview)
            activeCameras[layerId] = CameraInstance(
                layerId = layerId,
                facing = facing,
                camera = camera,
                preview = preview,
                isTorchOn = false
            )
            Log.i(TAG, "Bound camera $facing for layer $layerId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera $facing for layer $layerId: ${e.message}", e)
            // Clean up the preview
            try {
                provider.unbind(preview)
            } catch (cleanupError: Exception) {
                Log.w(TAG, "Error cleaning up preview for layer $layerId", cleanupError)
            }
            false
        }
    }

    /**
     * Unbind and release the camera for the given layer.
     */
    fun unbindCamera(layerId: String) {
        val instance = activeCameras.remove(layerId) ?: return
        val provider = cameraProvider
        if (provider != null && instance.preview != null) {
            try {
                provider.unbind(instance.preview)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding preview for layer $layerId", e)
            }
        }
        Log.i(TAG, "Unbound camera for layer $layerId")
    }

    /**
     * Set torch state for a specific camera layer.
     * Returns true if torch was successfully set, false if not supported or failed.
     */
    fun setTorch(layerId: String, on: Boolean): Boolean {
        val instance = activeCameras[layerId] ?: return false
        val camera = instance.camera ?: return false
        val cameraControl = camera.cameraControl
        val cameraInfo = camera.cameraInfo

        // Check if torch is available on this camera.
        // CameraInfo.hasFlashUnit() is a plain Boolean in CameraX 1.5 (not a future).
        val torchAvailable = try {
            cameraInfo.hasFlashUnit()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query flash unit for layer $layerId", e)
            false
        }

        if (!torchAvailable) {
            Log.w(TAG, "Torch not available for camera ${instance.facing} on layer $layerId")
            return false
        }

        return try {
            cameraControl.enableTorch(on).get()
            instance.isTorchOn = on
            activeCameras[layerId] = instance
            Log.i(TAG, "Torch $on for layer $layerId (${instance.facing})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set torch $on for layer $layerId", e)
            false
        }
    }

    /**
     * Toggle torch for a specific camera layer.
     */
    fun toggleTorch(layerId: String): Boolean {
        val instance = activeCameras[layerId] ?: return false
        return setTorch(layerId, !instance.isTorchOn)
    }

    /**
     * Get current torch state for a camera layer.
     */
    fun isTorchOn(layerId: String): Boolean {
        return activeCameras[layerId]?.isTorchOn ?: false
    }

    /**
     * Check if a camera layer is currently bound.
     */
    fun isCameraActive(layerId: String): Boolean {
        return activeCameras.containsKey(layerId)
    }

    /**
     * Get the facing of an active camera.
     */
    fun getFacing(layerId: String): CameraFacing? {
        return activeCameras[layerId]?.facing
    }

    /**
     * Get all active camera layer IDs.
     */
    fun getActiveLayerIds(): Set<String> {
        return activeCameras.keys.toSet()
    }

    /**
     * Check if the device has a front camera available.
     */
    fun hasFrontCamera(): Boolean {
        val provider = cameraProvider ?: return true // Assume available, will fail gracefully
        return provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    }

    /**
     * Check if the device has a back camera available.
     */
    fun hasBackCamera(): Boolean {
        val provider = cameraProvider ?: return true
        return provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
    }

    /**
     * Release all camera resources. Call on app shutdown or when leaving the studio.
     */
    fun releaseAll() {
        val layerIds = activeCameras.keys.toList()
        for (id in layerIds) {
            unbindCamera(id)
        }
        cameraProvider = null
        lifecycleOwner = null
        Log.i(TAG, "All cameras released")
    }

    /**
     * Release cameras for layers that no longer exist in the project.
     */
    fun cleanupMissingLayers(validLayerIds: Set<String>) {
        val staleIds = activeCameras.keys.filter { it !in validLayerIds }
        for (id in staleIds) {
            unbindCamera(id)
        }
    }
}
