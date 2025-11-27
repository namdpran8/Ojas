package com.pranshu.ojas.analysis

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Heart Rate Variability (HRV) Analysis
 * Provides insights into autonomic nervous system health
 */
class HRVAnalyzer {

    data class HRVMetrics(
        val sdnn: Float,        // Standard deviation of NN intervals (ms)
        val rmssd: Float,       // Root mean square of successive differences
        val pnn50: Float,       // Percentage of intervals differing by >50ms
        val stressIndex: Float, // 0-100, higher = more stress
        val interpretation: String
    )

    /**
     * Compute HRV from peak-to-peak intervals
     * @param peakIntervals Time between heartbeats in milliseconds
     */
    fun computeHRV(peakIntervals: List<Float>): HRVMetrics? {
        if (peakIntervals.size < 10) return null // Need at least 10 intervals

        // 1. SDNN - Standard Deviation of NN intervals
        val mean = peakIntervals.average().toFloat()
        val variance = peakIntervals.map { (it - mean).pow(2) }.average().toFloat()
        val sdnn = sqrt(variance)

        // 2. RMSSD - Root Mean Square of Successive Differences
        val successiveDiffs = mutableListOf<Float>()
        for (i in 1 until peakIntervals.size) {
            successiveDiffs.add(peakIntervals[i] - peakIntervals[i - 1])
        }
        val rmssd = sqrt(successiveDiffs.map { it.pow(2) }.average().toFloat())

        // 3. pNN50 - Percentage of intervals differing by >50ms
        val nn50Count = successiveDiffs.count { kotlin.math.abs(it) > 50 }
        val pnn50 = (nn50Count.toFloat() / successiveDiffs.size) * 100f

        // 4. Stress Index (simplified)
        // Lower HRV = higher stress
        val stressIndex = (100f - (sdnn.coerceIn(0f, 100f))).coerceIn(0f, 100f)

        // 5. Interpretation
        val interpretation = when {
            sdnn > 50 -> "Excellent - Low stress, good recovery"
            sdnn > 30 -> "Good - Normal stress levels"
            sdnn > 15 -> "Fair - Moderate stress"
            else -> "Low - High stress or fatigue"
        }

        return HRVMetrics(sdnn, rmssd, pnn50, stressIndex, interpretation)
    }

    /**
     * Extract peak intervals from raw PPG signal
     * Simplified peak detection algorithm
     */
    fun extractPeakIntervals(signal: FloatArray, samplingRate: Float): List<Float> {
        if (signal.size < 90) return emptyList()

        val peaks = mutableListOf<Int>()
        val threshold = signal.average() * 1.1f // 10% above mean

        // Simple peak detection
        for (i in 1 until signal.size - 1) {
            if (signal[i] > signal[i-1] &&
                signal[i] > signal[i+1] &&
                signal[i] > threshold) {

                // Avoid detecting peaks too close together (min 300ms apart)
                if (peaks.isEmpty() || (i - peaks.last()) > (samplingRate * 0.3)) {
                    peaks.add(i)
                }
            }
        }

        // Convert peak indices to intervals (in milliseconds)
        val intervals = mutableListOf<Float>()
        for (i in 1 until peaks.size) {
            val intervalSamples = peaks[i] - peaks[i-1]
            val intervalMs = (intervalSamples / samplingRate) * 1000f

            // Filter unrealistic intervals (30-200 BPM range)
            if (intervalMs in 300f..2000f) {
                intervals.add(intervalMs)
            }
        }

        return intervals
    }

    /**
     * Get stress level recommendation
     */
    fun getStressRecommendation(metrics: HRVMetrics): String {
        return when {
            metrics.stressIndex > 70 -> "Consider taking a break. Try deep breathing exercises."
            metrics.stressIndex > 50 -> "Moderate stress detected. Stay hydrated and relax."
            metrics.stressIndex > 30 -> "Stress levels are normal. Keep it up!"
            else -> "Excellent! You're well-rested and relaxed."
        }
    }

    /**
     * Fitness level estimation based on resting HR and HRV
     */
    fun estimateFitnessLevel(restingHR: Float, sdnn: Float): String {
        val score = (100f - restingHR) + (sdnn * 2f)

        return when {
            score > 80 -> "Athlete Level"
            score > 60 -> "Excellent Fitness"
            score > 40 -> "Good Fitness"
            score > 20 -> "Average Fitness"
            else -> "Below Average"
        }
    }
}