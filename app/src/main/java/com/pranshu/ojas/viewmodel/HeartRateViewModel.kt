package com.pranshu.ojas.viewmodel


import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pranshu.ojas.core.NativeSignalProcessor
import com.pranshu.ojas.ml.PulseML
import com.pranshu.ojas.vision.FaceTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing rPPG heart rate measurement pipeline
 */
class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    // Core components
    val faceTracker = FaceTracker(application)
    private val signalProcessor = NativeSignalProcessor(bufferSize = 300, samplingRate = 30f)
    private val pulseML = PulseML(application)

    // UI State
    private val _heartRate = MutableStateFlow(0f)
    val heartRate: StateFlow<Float> = _heartRate.asStateFlow()

    private val _signalBuffer = MutableStateFlow<List<Float>>(emptyList())
    val signalBuffer: StateFlow<List<Float>> = _signalBuffer.asStateFlow()

    private val _status = MutableStateFlow(MeasurementStatus.INITIALIZING)
    val status: StateFlow<MeasurementStatus> = _status.asStateFlow()

    private val _confidence = MutableStateFlow(0f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()

    // Processing jobs
    private var processingJob: Job? = null
    private var hrComputationJob: Job? = null

    init {
        startProcessing()
    }

    private fun startProcessing() {
        // Monitor face detection and green signal
        processingJob = viewModelScope.launch {
            faceTracker.faceDetected.combine(faceTracker.greenSignal) { detected, green ->
                Pair(detected, green)
            }.collect { (detected, green) ->
                if (detected) {
                    // Add sample to native processor
                    signalProcessor.addSample(green)

                    val sampleCount = signalProcessor.getCurrentSampleCount()
                    updateStatus(sampleCount)

                } else {
                    _status.value = MeasurementStatus.NO_FACE
                }
            }
        }

        // Periodic heart rate computation
        hrComputationJob = viewModelScope.launch {
            while (true) {
                delay(1000)  // Compute HR every second

                val sampleCount = signalProcessor.getCurrentSampleCount()
                if (sampleCount >= 150) {  // Need at least 5 seconds of data
                    computeHeartRate()
                }
            }
        }
    }

    private fun updateStatus(sampleCount: Int) {
        _status.value = when {
            sampleCount < 90 -> MeasurementStatus.ACQUIRING  // < 3 seconds
            sampleCount < 150 -> MeasurementStatus.TRACKING  // < 5 seconds
            else -> MeasurementStatus.MEASURING
        }

        // Update confidence based on sample count
        val maxSamples = 300f
        _confidence.value = (sampleCount / maxSamples).coerceIn(0f, 1f)
    }

    private fun computeHeartRate() {
        viewModelScope.launch {
            try {
                // Get raw HR from C++ FFT processing
                val rawHR = signalProcessor.computeHeartRate()

                if (rawHR > 0) {
                    // Get signal buffer for AI refinement
                    val buffer = signalProcessor.getSignalBuffer()

                    // Refine HR using TFLite model with NNAPI
                    val refinedHR = pulseML.refineHeartRate(buffer, rawHR)

                    _heartRate.value = refinedHR

                    // Update signal buffer for visualization
                    _signalBuffer.value = buffer.takeLast(150).toList()

                    Log.d(TAG, "HR computed: raw=%.1f, refined=%.1f".format(rawHR, refinedHR))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error computing heart rate", e)
            }
        }
    }

    fun reset() {
        signalProcessor.reset()
        _heartRate.value = 0f
        _signalBuffer.value = emptyList()
        _status.value = MeasurementStatus.INITIALIZING
        _confidence.value = 0f
    }

    override fun onCleared() {
        super.onCleared()
        processingJob?.cancel()
        hrComputationJob?.cancel()
        faceTracker.release()
        signalProcessor.release()
        pulseML.release()
    }

    companion object {
        private const val TAG = "HeartRateViewModel"
    }
}

enum class MeasurementStatus {
    INITIALIZING,
    NO_FACE,
    ACQUIRING,   // Collecting initial samples
    TRACKING,    // Building signal buffer
    MEASURING    // Actively measuring HR
}