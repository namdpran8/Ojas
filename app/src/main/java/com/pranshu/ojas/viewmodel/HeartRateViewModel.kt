package com.pranshu.ojas.viewmodel

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pranshu.ojas.analysis.HRVAnalyzer
import com.pranshu.ojas.analysis.SignalQuality
import com.pranshu.ojas.analysis.SignalQualityIndicator
import com.pranshu.ojas.core.NativeSignalProcessor
import com.pranshu.ojas.data.MeasurementHistory
import com.pranshu.ojas.ml.PulseML
import com.pranshu.ojas.vision.FaceTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    // --- Core Components ---
    // Make these nullable to prevent Main Thread freeze on init
    var faceTracker: FaceTracker? = null
    private var pulseML: PulseML? = null

    // Native processor (C++)
    private val signalProcessor = NativeSignalProcessor(bufferSize = 300, samplingRate = 30f)

    // --- Ghost Features (Now Active!) ---
    private val hrvAnalyzer = HRVAnalyzer()
    private val qualityIndicator = SignalQualityIndicator()
    private val history = MeasurementHistory()

    // --- UI State ---
    private val _heartRate = MutableStateFlow(0f)
    val heartRate: StateFlow<Float> = _heartRate.asStateFlow()

    private val _signalBuffer = MutableStateFlow<List<Float>>(emptyList())
    val signalBuffer: StateFlow<List<Float>> = _signalBuffer.asStateFlow()

    private val _status = MutableStateFlow(MeasurementStatus.INITIALIZING)
    val status: StateFlow<MeasurementStatus> = _status.asStateFlow()

    private val _confidence = MutableStateFlow(0f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()

    // NEW: Stress & Insights
    private val _stressLevel = MutableStateFlow("Analyzing...")
    val stressLevel: StateFlow<String> = _stressLevel.asStateFlow()

    private val _signalQualityMsg = MutableStateFlow("")
    val signalQualityMsg: StateFlow<String> = _signalQualityMsg.asStateFlow()

    // Internal logic variables
    private var processingJob: Job? = null
    private var hrComputationJob: Job? = null
    private var currentHrEstimate = 0f
    private val alpha = 0.15f // Smoothing factor

    private val _faceDetected = MutableStateFlow(false)
    val faceDetected: StateFlow<Boolean> = _faceDetected.asStateFlow()

    private val _landmarks = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val landmarks: StateFlow<List<Pair<Float, Float>>> = _landmarks.asStateFlow()


    init {
        // Initialize heavy AI models in background to prevent UI freeze
        viewModelScope.launch(Dispatchers.Default) {
            try {
                faceTracker = FaceTracker(application)
                pulseML = PulseML(application)
                startProcessing()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init AI models", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun startProcessing() {
        val tracker = faceTracker ?: return

        viewModelScope.launch {
            tracker.faceDetected.collect { _faceDetected.value = it }
        }
        viewModelScope.launch {
            tracker.landmarks.collect { _landmarks.value = it }
        }

        // 1. Collect Green Signal from Face
        processingJob = viewModelScope.launch {
            tracker.faceDetected.combine(tracker.greenSignal) { detected, green ->
                Pair(detected, green)
            }.collect { (detected, green) ->
                if (detected) {
                    signalProcessor.addSample(green)
                    val sampleCount = signalProcessor.getCurrentSampleCount()
                    updateStatus(sampleCount)
                } else {
                    _status.value = MeasurementStatus.NO_FACE
                    _signalQualityMsg.value = "" // Clear warnings
                }
            }
        }

        // 2. Periodic Analysis Loop (1Hz)
        hrComputationJob = viewModelScope.launch {
            while (true) {
                delay(1000) // Run every second
                if (signalProcessor.getCurrentSampleCount() >= 150) {
                    analyzeSignal()
                }
            }
        }
    }

    private fun updateStatus(sampleCount: Int) {
        if (_status.value != MeasurementStatus.COMPLETED) {
            _status.value = when {
                sampleCount < 90 -> MeasurementStatus.ACQUIRING
                sampleCount < 300 -> MeasurementStatus.TRACKING
                else -> MeasurementStatus.MEASURING
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM) // For History saving
    private fun analyzeSignal() {
        viewModelScope.launch {
            try {
                val bufferFloatArray = signalProcessor.getSignalBuffer()

                // --- A. Quality Check (Ghost Feature #1) ---
                val quality = qualityIndicator.computeOverallQuality(bufferFloatArray, 30f)

                // Update UI warning if quality is bad
                if (quality == SignalQuality.POOR || quality == SignalQuality.VERY_POOR) {
                    _signalQualityMsg.value = "⚠️ Poor Lighting: Move to brighter area"
                    _confidence.value = 0.2f
                } else {
                    _signalQualityMsg.value = "" // Clear warning
                    _confidence.value = 1.0f
                }

                // If quality is terrible, don't show random numbers
                if (quality == SignalQuality.VERY_POOR) return@launch

                // --- B. Heart Rate Calculation ---
                val rawHR = signalProcessor.computeHeartRate()

                // Filter valid range (45-200 BPM)
                if (rawHR > 45 && rawHR < 200) {
                    // Refine with AI
                    var finalHR = pulseML?.refineHeartRate(bufferFloatArray, rawHR) ?: rawHR

                    // Fallback if AI returns 45 (clamped) but raw was good
                    if (finalHR == 45f && rawHR > 50) finalHR = rawHR

                    // Exponential Smoothing (prevents jumping)
                    if (currentHrEstimate == 0f) {
                        currentHrEstimate = finalHR
                    } else {
                        currentHrEstimate = (alpha * finalHR) + ((1 - alpha) * currentHrEstimate)
                    }

                    _heartRate.value = currentHrEstimate
                    _signalBuffer.value = bufferFloatArray.takeLast(150).toList()

                    // --- C. Stress/HRV Analysis (Ghost Feature #2) ---
                    // Only analyze stress if we have a full 10s buffer (300 samples)
                    if (bufferFloatArray.size >= 300) {
                        val peaks = hrvAnalyzer.extractPeakIntervals(bufferFloatArray, 30f)
                        val hrvMetrics = hrvAnalyzer.computeHRV(peaks)

                        if (hrvMetrics != null) {
                            _stressLevel.value = "Stress: ${hrvMetrics.stressIndex.toInt()}/100\n(${hrvMetrics.interpretation})"

                            // --- D. Auto-Save History (Ghost Feature #3) ---
                            // If confidence is high and we haven't saved recently...
                            // (For hackathon, we can just log it or save on button press)
                            history.addMeasurement(currentHrEstimate, 1.0f, quality.name)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis error", e)
            }
        }
    }

    fun reset() {
        signalProcessor.reset()
        currentHrEstimate = 0f
        _heartRate.value = 0f
        _stressLevel.value = "Analyzing..."
        _signalBuffer.value = emptyList()
        _status.value = MeasurementStatus.INITIALIZING
    }

    override fun onCleared() {
        super.onCleared()
        faceTracker?.release()
        signalProcessor.release()
        pulseML?.release()
    }

    companion object {
        private const val TAG = "HeartRateViewModel"
    }
}

enum class MeasurementStatus {
    INITIALIZING, NO_FACE, ACQUIRING, TRACKING, MEASURING, COMPLETED
}