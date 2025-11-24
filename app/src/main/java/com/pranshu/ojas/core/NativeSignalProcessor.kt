package com.pranshu.ojas.core

import android.util.Log

/**
 * JNI wrapper for C++ signal processing with Arm Neon optimization
 */
class NativeSignalProcessor(
    private val bufferSize: Int = 300,  // ~10 seconds at 30fps
    private val samplingRate: Float = 30f
) {
    private var nativeHandle: Long = 0

    init {
        System.loadLibrary("ojas")
        nativeHandle = nativeInit(bufferSize, samplingRate)
        Log.d(TAG, "NativeSignalProcessor initialized: handle=$nativeHandle")
    }

    /**
     * Add a new green channel sample to the circular buffer
     */
    fun addSample(greenValue: Float, timestamp: Long = System.currentTimeMillis()) {
        if (nativeHandle != 0L) {
            addSample(nativeHandle, greenValue, timestamp)
        }
    }

    /**
     * Compute heart rate from accumulated samples using FFT
     * Returns heart rate in BPM (0 if insufficient data)
     */
    fun computeHeartRate(): Float {
        return if (nativeHandle != 0L) {
            getHeartRate(nativeHandle)
        } else {
            0f
        }
    }

    /**
     * Get the current signal buffer for visualization
     */
    fun getSignalBuffer(): FloatArray {
        return if (nativeHandle != 0L) {
            getBuffer(nativeHandle) ?: FloatArray(0)
        } else {
            FloatArray(0)
        }
    }

    /**
     * Get current sample count
     */
    fun getCurrentSampleCount(): Int {
        return if (nativeHandle != 0L) {
            getSampleCount(nativeHandle)
        } else {
            0
        }
    }

    /**
     * Reset the signal processor
     */
    fun reset() {
        if (nativeHandle != 0L) {
            reset(nativeHandle)
        }
    }

    /**
     * Release native resources
     */
    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0
        }
    }

    // Native method declarations
    private external fun nativeInit(bufferSize: Int, samplingRate: Float): Long
    private external fun nativeRelease(handle: Long)
    private external fun addSample(handle: Long, greenValue: Float, timestamp: Long)
    private external fun getHeartRate(handle: Long): Float
    private external fun getBuffer(handle: Long): FloatArray?
    private external fun getSampleCount(handle: Long): Int
    private external fun reset(handle: Long)

    companion object {
        private const val TAG = "NativeSignalProcessor"
    }
}