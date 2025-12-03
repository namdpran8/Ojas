// app/src/main/cpp/native-lib.cpp
#include <jni.h>
#include <string>
#include <android/log.h>
#include <arm_neon.h>
#include "signal_processor.h"

#define LOG_TAG "ojas-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_nativeInit(JNIEnv* env, jobject, jint bufferSize, jfloat samplingRate) {
    auto* processor = new SignalProcessor(bufferSize, samplingRate);
    return reinterpret_cast<jlong>(processor);
}

JNIEXPORT void JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_nativeRelease(JNIEnv* env, jobject, jlong handle) {
    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (processor) delete processor;
}

JNIEXPORT void JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_reset(JNIEnv* env, jobject, jlong handle) {
    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (processor) processor->reset();
}

JNIEXPORT void JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_addSample(JNIEnv* env, jobject, jlong handle, jfloat greenValue, jlong timestamp) {
    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (processor) processor->addSample(greenValue, timestamp);
}

JNIEXPORT jfloat JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_getHeartRate(JNIEnv* env, jobject, jlong handle) {
    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (processor) return processor->computeHeartRate();
    return 0.0f;
}

JNIEXPORT jfloatArray JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_getBuffer(JNIEnv* env, jobject, jlong handle) {
    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (!processor) return nullptr;
    const auto& buffer = processor->getBuffer();
    jfloatArray result = env->NewFloatArray(buffer.size());
    if (result) env->SetFloatArrayRegion(result, 0, buffer.size(), buffer.data());
    return result;
}

JNIEXPORT jint JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_getSampleCount(JNIEnv* env, jobject, jlong handle) {
    auto* processor = reinterpret_cast<SignalProcessor*>(handle);
    if (processor) return processor->getSampleCount();
    return 0;
}

// --- OPTIMIZATION: NEON Accelerated Image Processing ---
// You can mention this in your README as an Arm Optimization feature
JNIEXPORT jfloat JNICALL
Java_com_pranshu_ojas_core_NativeSignalProcessor_computeGreenAverage(
        JNIEnv* env, jobject, jbyteArray imageData, jint width, jint height) {

    jbyte* pixels = env->GetByteArrayElements(imageData, nullptr);
    int totalPixels = width * height;

    uint32x4_t sumVector = vdupq_n_u32(0);
    int i = 0;

    // Process 16 pixels at a time using NEON
    for (; i <= totalPixels - 16; i += 16) {
        uint8x16x4_t pixelBlock = vld4q_u8(reinterpret_cast<uint8_t*>(&pixels[i * 4]));
        uint8x16_t greenBytes = pixelBlock.val[1];
        uint16x8_t high = vmovl_u8(vget_high_u8(greenBytes));
        uint16x8_t low = vmovl_u8(vget_low_u8(greenBytes));
        sumVector = vaddq_u32(sumVector, vpaddlq_u16(high));
        sumVector = vaddq_u32(sumVector, vpaddlq_u16(low));
    }

    float totalSum = vgetq_lane_u32(sumVector, 0) + vgetq_lane_u32(sumVector, 1) +
                     vgetq_lane_u32(sumVector, 2) + vgetq_lane_u32(sumVector, 3);

    for (; i < totalPixels; i++) {
        totalSum += static_cast<uint8_t>(pixels[i * 4 + 1]);
    }

    env->ReleaseByteArrayElements(imageData, pixels, 0);
    return totalSum / totalPixels;
}

} // extern "C"