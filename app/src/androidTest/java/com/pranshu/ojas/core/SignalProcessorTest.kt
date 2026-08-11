package com.pranshu.ojas.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class SignalProcessorTest {

    @Test
    fun testHeartRate_60BPM() {
        val processor = NativeSignalProcessor(300, 30f)
        val samplingRate = 30f
        
        for (i in 0 until 300) {
            val t = i / samplingRate
            val greenVal = 100f + 10f * sin(2.0 * PI * 1.0 * t).toFloat()
            processor.addSample(greenVal, (i * 33).toLong())
        }
        
        val hr = processor.computeHeartRate()
        assertTrue("Expected ~60 BPM, got $hr", hr > 58f && hr < 62f)
        processor.release()
    }
    
    @Test
    fun testHeartRate_120BPM() {
        val processor = NativeSignalProcessor(300, 30f)
        val samplingRate = 30f
        
        for (i in 0 until 300) {
            val t = i / samplingRate
            val greenVal = 100f + 10f * sin(2.0 * PI * 2.0 * t).toFloat() // 2 Hz = 120 BPM
            processor.addSample(greenVal, (i * 33).toLong())
        }
        
        val hr = processor.computeHeartRate()
        assertTrue("Expected ~120 BPM, got $hr", hr > 118f && hr < 122f)
        processor.release()
    }

    @Test
    fun testSnrGating_NoiseOnly() {
        val processor = NativeSignalProcessor(300, 30f)
        
        for (i in 0 until 300) {
            // Random noise between 0 and 10
            val greenVal = (Math.random() * 10.0).toFloat()
            processor.addSample(greenVal, (i * 33).toLong())
        }
        
        val hr = processor.computeHeartRate()
        assertEquals("Expected 0 BPM for noise due to SNR gating", 0f, hr)
        processor.release()
    }
}
