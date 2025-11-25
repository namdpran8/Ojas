package com.pranshu.ojas.analysis

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Real-time signal quality assessment for rPPG
 * Helps user understand if measurement is reliable
 */
class SignalQualityIndicator {

    /**
     * Compute Signal-to-Noise Ratio (SNR)
     * Higher SNR = better quality
     */
    fun computeSNR(signal: FloatArray): Float {
        if (signal.isEmpty()) return 0f

        // Signal power (variance)
        val mean = signal.average().toFloat()
        val variance = signal.map { (it - mean).pow(2) }.average().toFloat()

        // Noise estimation (high-frequency component)
        val noise = estimateNoise(signal)

        if (noise < 1e-6f) return 100f // Very clean signal

        return 10f * kotlin.math.log10(variance / noise)
    }

    /**
     * Estimate noise level using successive differences
     */
    private fun estimateNoise(signal: FloatArray): Float {
        if (signal.size < 2) return 0f

        var sumDiff = 0f
        for (i in 1 until signal.size) {
            sumDiff += abs(signal[i] - signal[i - 1])
        }

        return sumDiff / (signal.size - 1)
    }

    /**
     * Check if signal has periodic structure (expected for heart rate)
     * Returns confidence score 0.0-1.0
     */
    fun computePeriodicity(signal: FloatArray, samplingRate: Float): Float {
        if (signal.size < 60) return 0f // Need at least 2 seconds

        // Autocorrelation at expected lag
        val expectedHR = 70f // Assume typical HR
        val expectedLag = (samplingRate * 60f / expectedHR).toInt()

        if (expectedLag >= signal.size) return 0f

        val autocorr = computeAutocorrelation(signal, expectedLag)

        // Normalize to 0-1 range
        return (autocorr + 1f) / 2f
    }

    /**
     * Compute autocorrelation at specific lag
     */
    private fun computeAutocorrelation(signal: FloatArray, lag: Int): Float {
        if (lag >= signal.size) return 0f

        val mean = signal.average().toFloat()
        var numerator = 0f
        var denominator = 0f

        for (i in 0 until signal.size - lag) {
            val x = signal[i] - mean
            val y = signal[i + lag] - mean
            numerator += x * y
            denominator += x * x
        }

        return if (denominator < 1e-6f) 0f else numerator / denominator
    }

    /**
     * Detect motion artifacts (sudden spikes)
     */
    fun detectMotionArtifacts(signal: FloatArray): Float {
        if (signal.size < 2) return 0f

        var artifactCount = 0
        val threshold = computeThreshold(signal)

        for (i in 1 until signal.size) {
            val diff = abs(signal[i] - signal[i - 1])
            if (diff > threshold) {
                artifactCount++
            }
        }

        // Return percentage of clean samples
        return 1f - (artifactCount.toFloat() / signal.size)
    }

    private fun computeThreshold(signal: FloatArray): Float {
        val diffs = mutableListOf<Float>()
        for (i in 1 until signal.size) {
            diffs.add(abs(signal[i] - signal[i - 1]))
        }

        val stdDev = sqrt(diffs.map { it.pow(2) }.average()).toFloat()
        return 3f * stdDev // 3-sigma rule
    }

    /**
     * Overall quality score: 0.0 (poor) to 1.0 (excellent)
     */
    fun computeOverallQuality(signal: FloatArray, samplingRate: Float): SignalQuality {
        val snr = computeSNR(signal).coerceIn(0f, 50f) / 50f
        val periodicity = computePeriodicity(signal, samplingRate)
        val cleanness = detectMotionArtifacts(signal)

        // Weighted average
        val score = (snr * 0.3f + periodicity * 0.4f + cleanness * 0.3f)

        return when {
            score >= 0.8f -> SignalQuality.EXCELLENT
            score >= 0.6f -> SignalQuality.GOOD
            score >= 0.4f -> SignalQuality.FAIR
            score >= 0.2f -> SignalQuality.POOR
            else -> SignalQuality.VERY_POOR
        }
    }
}

enum class SignalQuality(val displayName: String, val color: Long) {
    EXCELLENT("Excellent", 0xFF00FF88),
    GOOD("Good", 0xFF00DDFF),
    FAIR("Fair", 0xFFFFAA00),
    POOR("Poor", 0xFFFF6644),
    VERY_POOR("Very Poor", 0xFFFF0000)
}