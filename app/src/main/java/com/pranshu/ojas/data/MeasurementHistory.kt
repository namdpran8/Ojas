package com.pranshu.ojas.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Store and analyze measurement history
 */
data class Measurement(
    val heartRate: Float,
    val timestamp: Long,
    val confidence: Float,
    val quality: String
)

class MeasurementHistory {
    private val _measurements = MutableStateFlow<List<Measurement>>(emptyList())
    val measurements: StateFlow<List<Measurement>> = _measurements

    private val maxHistory = 50 // Keep last 50 measurements

    fun addMeasurement(hr: Float, confidence: Float, quality: String) {
        val measurement = Measurement(
            heartRate = hr,
            timestamp = System.currentTimeMillis(),
            confidence = confidence,
            quality = quality
        )

        val updated = _measurements.value.toMutableList().apply {
            add(0, measurement) // Add to front
            if (size > maxHistory) {
                removeLast()
            }
        }

        _measurements.value = updated
    }

    /**
     * Get statistics for today's measurements
     */
    fun getTodayStatistics(): Statistics {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val todayStart = calendar.timeInMillis

        val todayMeasurements = _measurements.value.filter { it.timestamp >= todayStart }

        if (todayMeasurements.isEmpty()) {
            return Statistics(0, 0f, 0f, 0f, 0f)
        }

        val hrs = todayMeasurements.map { it.heartRate }

        return Statistics(
            count = todayMeasurements.size,
            average = hrs.average().toFloat(),
            min = hrs.minOrNull() ?: 0f,
            max = hrs.maxOrNull() ?: 0f,
            latest = todayMeasurements.firstOrNull()?.heartRate ?: 0f
        )
    }

    /**
     * Get measurements from last N minutes
     */
    fun getRecentMeasurements(minutes: Int): List<Measurement> {
        val cutoff = System.currentTimeMillis() - (minutes * 60 * 1000)
        return _measurements.value.filter { it.timestamp >= cutoff }
    }

    /**
     * Export measurements as CSV
     */
    fun exportToCSV(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val csv = StringBuilder()

        csv.append("Timestamp,Heart Rate (BPM),Confidence,Quality\n")

        _measurements.value.forEach { m ->
            csv.append("${dateFormat.format(Date(m.timestamp))},")
            csv.append("${m.heartRate},")
            csv.append("${m.confidence},")
            csv.append("${m.quality}\n")
        }

        return csv.toString()
    }

    /**
     * Detect anomalies (unusual readings)
     */
    fun detectAnomalies(): List<Measurement> {
        if (_measurements.value.size < 5) return emptyList()

        val hrs = _measurements.value.map { it.heartRate }
        val mean = hrs.average().toFloat()
        val stdDev = kotlin.math.sqrt(
            hrs.map { (it - mean) * (it - mean) }.average()
        ).toFloat()

        // Flag measurements > 2 standard deviations from mean
        return _measurements.value.filter {
            kotlin.math.abs(it.heartRate - mean) > 2 * stdDev
        }
    }

    fun clear() {
        _measurements.value = emptyList()
    }
}

data class Statistics(
    val count: Int,
    val average: Float,
    val min: Float,
    val max: Float,
    val latest: Float
)