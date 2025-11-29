package com.pranshu.ojas.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pranshu.ojas.vision.FaceTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages CameraX setup and frame streaming with camera switching and flash support
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val faceTracker: FaceTracker?
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    // Camera state
    private val _currentLensFacing = MutableStateFlow(CameraSelector.LENS_FACING_FRONT)
    val currentLensFacing: StateFlow<Int> = _currentLensFacing

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn

    private val _hasFlash = MutableStateFlow(false)
    val hasFlash: StateFlow<Boolean> = _hasFlash

    private var currentPreviewView: PreviewView? = null

    /**
     * Start camera with front-facing lens (default)
     */
    fun startCamera(previewView: PreviewView) {
        currentPreviewView = previewView
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(previewView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(previewView: PreviewView) {
        val cameraProvider = cameraProvider ?: return

        // Unbind all use cases before rebinding
        cameraProvider.unbindAll()

        // Select camera based on current lens facing
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(_currentLensFacing.value)
            .build()

        // Preview use case
        preview = Preview.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Image analysis use case for frame processing
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }

        try {
            // Bind use cases to lifecycle
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            // Check if flash is available
            val hasFlashUnit = camera?.cameraInfo?.hasFlashUnit() ?: false
            _hasFlash.value = hasFlashUnit

            // Apply flash state if available
            if (hasFlashUnit && _isFlashOn.value) {
                camera?.cameraControl?.enableTorch(true)
            }

            Log.d(TAG, "Camera bound successfully. Lens: ${getLensFacingName()}, Flash available: $hasFlashUnit")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            // Convert ImageProxy to Bitmap
            val bitmap = imageProxy.toBitmap()

            // Process with face tracker
            val timestamp = System.currentTimeMillis()
            faceTracker.processFrame(bitmap, timestamp)

            // FPS tracking
            frameCount++
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastFpsTime >= 1000) {
                val fps = frameCount * 1000f / (currentTime - lastFpsTime)
                Log.d(TAG, "Processing FPS: %.1f".format(fps))
                frameCount = 0
                lastFpsTime = currentTime
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))

        // Rotate bitmap to correct orientation
        val matrix = Matrix()
        matrix.postRotate(imageInfo.rotationDegrees.toFloat())

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    /**
     * Switch between front and back camera
     */
    fun switchCamera() {
        _currentLensFacing.value = if (_currentLensFacing.value == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }

        // Rebind camera with new lens
        currentPreviewView?.let { bindCameraUseCases(it) }

        Log.d(TAG, "Camera switched to: ${getLensFacingName()}")
    }

    /**
     * Toggle flash on/off
     */
    fun toggleFlash() {
        val camera = camera ?: return

        if (!_hasFlash.value) {
            Log.w(TAG, "Flash not available on this camera")
            return
        }

        val newFlashState = !_isFlashOn.value

        try {
            camera.cameraControl.enableTorch(newFlashState)
            _isFlashOn.value = newFlashState
            Log.d(TAG, "Flash ${if (newFlashState) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flash", e)
        }
    }

    /**
     * Set flash state explicitly
     */
    fun setFlash(enabled: Boolean) {
        val camera = camera ?: return

        if (!_hasFlash.value) {
            Log.w(TAG, "Flash not available on this camera")
            return
        }

        try {
            camera.cameraControl.enableTorch(enabled)
            _isFlashOn.value = enabled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set flash", e)
        }
    }

    private fun getLensFacingName(): String {
        return if (_currentLensFacing.value == CameraSelector.LENS_FACING_FRONT) {
            "Front"
        } else {
            "Back"
        }
    }

    fun stopCamera() {
        // Turn off flash before stopping
        if (_isFlashOn.value) {
            camera?.cameraControl?.enableTorch(false)
        }

        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraManager"
    }
}