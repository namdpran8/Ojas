package com.pranshu.ojas.ml


import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AI-powered signal refinement using TFLite with Arm NPU acceleration (NNAPI)
 * Cleans motion artifacts from rPPG signals
 */
class PulseML(context: Context) {

    private var interpreter: Interpreter? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var gpuDelegate: GpuDelegate? = null

    private val inputSize = 300  // Must match buffer size
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null

    init {
        initializeModel(context)
    }

    private fun initializeModel(context: Context) {
        try {
            // Load model from assets
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_PATH)

            // Configure TFLite interpreter with NNAPI delegate for Arm NPU
            val options = Interpreter.Options().apply {
                // Primary: Use NNAPI delegate to leverage Arm NPU/DSP
                try {
                    nnApiDelegate = NnApiDelegate(
                        NnApiDelegate.Options().apply {
                            // Allow FP16 precision for better NPU utilization
                            setAllowFp16(true)
                            // Use NPU if available, fall back to CPU
                            setUseNnapiCpu(false)
                            // Set execution preference to sustained speed
                            setExecutionPreference(NnApiDelegate.Options.EXECUTION_PREFERENCE_SUSTAINED_SPEED)
                        }
                    )
                    addDelegate(nnApiDelegate)
                    Log.i(TAG, "NNAPI delegate enabled (Arm NPU acceleration)")
                } catch (e: Exception) {
                    Log.w(TAG, "NNAPI delegate not available, trying GPU delegate", e)

                    // Fallback: Try GPU delegate
                    try {
                        gpuDelegate = GpuDelegate()
                        addDelegate(gpuDelegate)
                        Log.i(TAG, "GPU delegate enabled")
                    } catch (e2: Exception) {
                        Log.w(TAG, "GPU delegate not available, using CPU", e2)
                    }
                }

                // Use 4 threads for CPU operations
                setNumThreads(4)
            }

            interpreter = Interpreter(modelBuffer, options)

            // Allocate input/output buffers
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            val outputShape = interpreter?.getOutputTensor(0)?.shape()

            Log.d(TAG, "Model input shape: ${inputShape?.contentToString()}")
            Log.d(TAG, "Model output shape: ${outputShape?.contentToString()}")

            inputBuffer = ByteBuffer.allocateDirect(inputSize * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            outputBuffer = ByteBuffer.allocateDirect(4).apply {  // Single float output
                order(ByteOrder.nativeOrder())
            }

            Log.i(TAG, "TFLite model initialized successfully with Arm optimization")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TFLite model", e)
        }
    }

    /**
     * Refine raw HR estimate using AI model
     * Input: 300-sample buffer of raw green values
     * Output: Cleaned heart rate estimate in BPM
     */
    fun refineHeartRate(rawSignal: FloatArray, rawHR: Float): Float {
        if (interpreter == null || rawSignal.size != inputSize) {
            return rawHR  // Fall back to raw estimate
        }

        try {
            // Prepare input buffer
            inputBuffer?.rewind()
            rawSignal.forEach { value ->
                inputBuffer?.putFloat(value)
            }

            // Run inference
            outputBuffer?.rewind()
            interpreter?.run(inputBuffer, outputBuffer)

            // Read output
            outputBuffer?.rewind()
            val refinedHR = outputBuffer?.float ?: rawHR

            // Sanity check: Keep HR in valid range
            return refinedHR.coerceIn(45f, 180f)

        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            return rawHR
        }
    }

    /**
     * Alternative: Refine signal waveform (for advanced use cases)
     */
    fun refineSignalWaveform(rawSignal: FloatArray): FloatArray {
        // If model outputs full waveform instead of single HR value
        // This would be used for more sophisticated signal cleaning
        return rawSignal  // Placeholder
    }

    fun release() {
        interpreter?.close()
        nnApiDelegate?.close()
        gpuDelegate?.close()

        interpreter = null
        nnApiDelegate = null
        gpuDelegate = null

        Log.d(TAG, "PulseML resources released")
    }

    companion object {
        private const val TAG = "PulseML"
        private const val MODEL_PATH = "rppg_model.tflite"
    }
}