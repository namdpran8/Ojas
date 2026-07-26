package com.pranshu.ojas.vision

import com.google.mediapipe.framework.image.BitmapExtractor
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Face tracking using MediaPipe Face Landmarker
 * Extracts ROI (forehead/cheeks) and computes average green channel intensity
 */
class FaceTracker(context: Context) {

    private var faceLandmarker: FaceLandmarker? = null
    private val _faceDetected = MutableStateFlow(false)
    val faceDetected: StateFlow<Boolean> = _faceDetected

    private val _greenSignal = MutableStateFlow(0f)
    val greenSignal: StateFlow<Float> = _greenSignal

    private val _landmarks = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val landmarks: StateFlow<List<Pair<Float, Float>>> = _landmarks

    // Forehead landmark indices (top of face)
    private val foreheadIndices = listOf(10, 151, 9, 8, 107, 66, 105, 104, 103, 67, 109, 108)

    // Cheek landmark indices
    private val leftCheekIndices = listOf(205, 207, 187, 123, 116, 100, 36)
    private val rightCheekIndices = listOf(425, 427, 411, 352, 345, 329, 266)

    init {
        initializeFaceLandmarker(context)
    }

    private fun initializeFaceLandmarker(context: Context) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, input ->
                    val bitmap = BitmapExtractor.extract(input) // <--- FIX
                    handleFaceLandmarkerResult(result, bitmap)
                }
                .setErrorListener { error ->
                    Log.e(TAG, "FaceLandmarker error: ${error.message}")
                }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d(TAG, "FaceLandmarker initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FaceLandmarker", e)
        }
    }

    /**
     * Process a camera frame to detect face and extract green signal
     */
    fun processFrame(bitmap: Bitmap, timestampMs: Long) {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            faceLandmarker?.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        }
    }

    private fun handleFaceLandmarkerResult(result: FaceLandmarkerResult, bitmap: Bitmap?) {
        if (result.faceLandmarks().isEmpty()) {
            _faceDetected.value = false
            return
        }

        _faceDetected.value = true

        // Get first detected face
        val faceLandmarks = result.faceLandmarks()[0]

        // Extract landmark coordinates for visualization
        val landmarkPoints = faceLandmarks.map { landmark ->
            Pair(landmark.x(), landmark.y())
        }
        _landmarks.value = landmarkPoints

        // Extract ROI and compute green signal
        if (bitmap != null) {
            val greenValue = extractGreenSignal(bitmap, faceLandmarks)
            _greenSignal.value = greenValue
        }
    }

    private var pixelBuffer: java.nio.ByteBuffer? = null
    private var pixelByteArray: ByteArray? = null

    /**
     * Extract average green channel intensity from face ROI using NEON optimization
     */
    private fun extractGreenSignal(
        bitmap: Bitmap,
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>
    ): Float {
        val width = bitmap.width
        val height = bitmap.height

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        val allIndices = foreheadIndices + leftCheekIndices + rightCheekIndices
        allIndices.forEach { index ->
            if (index < landmarks.size) {
                val lm = landmarks[index]
                if (lm.x() < minX) minX = lm.x()
                if (lm.y() < minY) minY = lm.y()
                if (lm.x() > maxX) maxX = lm.x()
                if (lm.y() > maxY) maxY = lm.y()
            }
        }

        val px = (minX * width).toInt().coerceIn(0, width - 1)
        val py = (minY * height).toInt().coerceIn(0, height - 1)
        val w = ((maxX - minX) * width).toInt().coerceAtLeast(1).coerceAtMost(width - px)
        val h = ((maxY - minY) * height).toInt().coerceAtLeast(1).coerceAtMost(height - py)

        val faceBitmap = Bitmap.createBitmap(bitmap, px, py, w, h)
        val size = faceBitmap.rowBytes * faceBitmap.height

        if (pixelByteArray == null || pixelByteArray!!.size < size) {
            pixelByteArray = ByteArray(size)
            pixelBuffer = java.nio.ByteBuffer.wrap(pixelByteArray)
        }

        pixelBuffer!!.position(0)
        faceBitmap.copyPixelsToBuffer(pixelBuffer!!)

        return com.pranshu.ojas.core.NativeSignalProcessor.computeGreenAverage(pixelByteArray!!, w, h)
    }

    fun release() {
        faceLandmarker?.close()
        faceLandmarker = null
    }

    companion object {
        private const val TAG = "FaceTracker"
    }
}