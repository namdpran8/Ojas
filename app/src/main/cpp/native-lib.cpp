#include <jni.h>
#include <string>
#include <android/log.h>
#include "signal_processor.h"

#define LOG_TAG "ojas-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global signal processor instance
static SignalProcessor* g_processor = nullptr;

extern "C" {

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

} // extern "C"