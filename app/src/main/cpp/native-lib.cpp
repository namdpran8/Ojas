#include <jni.h>
#include <string>
#include <android/log.h>
#include <arm_neon.h> // Required for Arm optimization
#include "signal_processor.h"

#define LOG_TAG "ojas-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// --- Lifecycle Methods ---

JNIEXPORT jlong JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_nativeInit(
        JNIEnv* env,
        jobject /* this */,
        jint bufferSize,
        jfloat samplingRate) {

    LOGI("Initializing SignalProcessor: bufferSize=%d, samplingRate=%.2f",
         bufferSize, samplingRate);

    auto* processor = new SignalProcessor(bufferSize, samplingRate);
    return reinterpret_cast<jlong>(processor);
}

JNIEXPORT void JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_nativeRelease(
        JNIEnv* env,
        jobject /* this */,
        jlong handle) {

    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (processor) {
        delete processor;
        LOGI("SignalProcessor released");
    }
}

JNIEXPORT void JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_reset(
        JNIEnv* env,
        jobject /* this */,
        jlong handle) {

    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (processor) {
        processor->reset();
        LOGI("SignalProcessor reset");
    }
}

// --- Data Processing Methods ---

JNIEXPORT void JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_addSample(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jfloat greenValue,
        jlong timestamp) {

    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (processor) {
        processor->addSample(greenValue, timestamp);
    }
}

JNIEXPORT jfloat JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_getHeartRate(
        JNIEnv* env,
        jobject /* this */,
        jlong handle) {

    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (processor) {
        return processor->computeHeartRate();
    }
    return 0.0f;
}

// NEW: Respiration Rate (Optimization for Hackathon)
JNIEXPORT jfloat JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_getRespirationRate(
        JNIEnv* env,
        jobject /* this */,
        jlong handle) {

    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (processor) {
        // Ensure you implemented computeRespirationRate in signal_processor.cpp!
        return processor->computeRespirationRate();
    }
    return 0.0f;
}

JNIEXPORT jfloatArray JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_getBuffer(
        JNIEnv* env,
        jobject /* this */,
        jlong handle) {

    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (!processor) {
        return nullptr;
    }

    const auto& buffer = processor->getBuffer();
    jfloatArray result = env->NewFloatArray(buffer.size());
    if (result) {
        env->SetFloatArrayRegion(result, 0, buffer.size(), buffer.data());
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_getSampleCount(
        JNIEnv* env,
        jobject /* this */,
        jlong handle) {

    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (processor) {
        return processor->getSampleCount();
    }
    return 0;
}

// --- NEW: NEON Accelerated Image Processing ---
// Calculates the average GREEN intensity from an RGBA byte array.
// Optimized using Arm NEON intrinsics to process 16 pixels per cycle.
JNIEXPORT jfloat JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_computeGreenAverage(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray imageData,
        jint width,
        jint height) {

    jbyte* pixels = env->GetByteArrayElements(imageData, nullptr);
    int totalPixels = width * height;

    // NEON Accumulator: Vector of 4 x 32-bit integers, initialized to 0
    uint32x4_t sumVector = vdupq_n_u32(0);

    int i = 0;
    // Process 16 pixels at a time (16 pixels * 4 bytes/pixel = 64 bytes)
    for (; i <= totalPixels - 16; i += 16) {
        // Load 64 bytes of RGBA data into 4 vectors (interleaved)
        // val[0]=R, val[1]=G, val[2]=B, val[3]=A
        uint8x16x4_t pixelBlock = vld4q_u8(reinterpret_cast<uint8_t*>(&pixels[i * 4]));

        // Extract the Green channel (val[1])
        uint8x16_t greenBytes = pixelBlock.val[1];

        // Expand 8-bit values to 16-bit to prevent overflow
        uint16x8_t high = vmovl_u8(vget_high_u8(greenBytes));
        uint16x8_t low = vmovl_u8(vget_low_u8(greenBytes));

        // Accumulate 16-bit values into 32-bit sumVector
        sumVector = vaddq_u32(sumVector, vpaddlq_u16(high));
        sumVector = vaddq_u32(sumVector, vpaddlq_u16(low));
    }

    // Sum the 4 lanes of the accumulator vector into a single scalar
    float totalSum = vgetq_lane_u32(sumVector, 0) + vgetq_lane_u32(sumVector, 1) +
                     vgetq_lane_u32(sumVector, 2) + vgetq_lane_u32(sumVector, 3);

    // Handle remaining pixels (standard scalar loop)
    for (; i < totalPixels; i++) {
        // RGBA format: Green is at index 1
        totalSum += static_cast<uint8_t>(pixels[i * 4 + 1]);
    }

    env->ReleaseByteArrayElements(imageData, pixels, 0);
    return totalSum / totalPixels;
}

} // extern "C"