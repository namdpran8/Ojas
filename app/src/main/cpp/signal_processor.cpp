// app/src/main/cpp/signal_processor.cpp
#include "signal_processor.h"
#include <numeric>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "ojas-Proc"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

SignalProcessor::SignalProcessor(int bufferSize, float samplingRate)
        : mBufferSize(bufferSize), mSamplingRate(samplingRate) {

    mRawBuffer.reserve(bufferSize);
    mTimeBuffer.reserve(bufferSize);

    // Initialize FFT (nfft should ideally be power of 2, but kiss_fft handles others)
    mFftCfg = kiss_fft_alloc(bufferSize, 0, nullptr, nullptr);
    mFftIn.resize(bufferSize);
    mFftOut.resize(bufferSize);
}

SignalProcessor::~SignalProcessor() {
    free(mFftCfg);
}

void SignalProcessor::addSample(float greenValue, long timestamp) {
    if (mRawBuffer.size() >= mBufferSize) {
        // Slide window: remove oldest
        mRawBuffer.erase(mRawBuffer.begin());
        mTimeBuffer.erase(mTimeBuffer.begin());
    }
    mRawBuffer.push_back(greenValue);
    mTimeBuffer.push_back(timestamp);
}

void SignalProcessor::reset() {
    mRawBuffer.clear();
    mTimeBuffer.clear();
}

const std::vector<float>& SignalProcessor::getBuffer() const {
    return mRawBuffer;
}

int SignalProcessor::getSampleCount() const {
    return mRawBuffer.size();
}

// Helper: Remove DC component and normalize
void SignalProcessor::normalizeBuffer(const std::vector<float>& input, std::vector<float>& output) {
    output = input;
    if (input.empty()) return;

    // Calculate mean
    float sum = std::accumulate(input.begin(), input.end(), 0.0f);
    float mean = sum / input.size();

    // Subtract mean (detrending DC)
    for (float &val : output) {
        val -= mean;
    }
}

// Helper: Apply Hamming window to reduce spectral leakage
void SignalProcessor::applyWindow(std::vector<float>& data) {
    size_t N = data.size();
    for (size_t i = 0; i < N; ++i) {
        // Hamming window
        float multiplier = 0.54f - 0.46f * cosf((2.0f * M_PI * i) / (N - 1));
        data[i] *= multiplier;
    }
}

float SignalProcessor::computeHeartRate() {
    int N = mRawBuffer.size();
    // Need a reasonable amount of data (e.g., ~3-4 seconds)
    if (N < mSamplingRate * 3) {
        return 0.0f;
    }

    // 1. Prepare data
    std::vector<float> processed;
    normalizeBuffer(mRawBuffer, processed);
    applyWindow(processed);

    // 2. Fill FFT input
    for (int i = 0; i < N; ++i) {
        mFftIn[i].r = processed[i];
        mFftIn[i].i = 0.0f;
    }
    // Zero pad if we haven't filled the buffer yet, though normally we wait
    for (int i = N; i < mBufferSize; ++i) {
        mFftIn[i].r = 0.0f;
        mFftIn[i].i = 0.0f;
    }

    // 3. Execute FFT
    kiss_fft(mFftCfg, mFftIn.data(), mFftOut.data());

    // 4. Find Peak Frequency in HR range
    // Interest range: 45 BPM (0.75 Hz) to 240 BPM (4.0 Hz)
    float minFreq = 0.75f;
    float maxFreq = 4.0f;

    float maxMagnitude = 0.0f;
    int peakIndex = -1;

    for (int i = 1; i < mBufferSize / 2; ++i) {
        float freq = (i * mSamplingRate) / mBufferSize;

        if (freq >= minFreq && freq <= maxFreq) {
            float magnitude = sqrtf(mFftOut[i].r * mFftOut[i].r + mFftOut[i].i * mFftOut[i].i);
            if (magnitude > maxMagnitude) {
                maxMagnitude = magnitude;
                peakIndex = i;
            }
        }
    }

    // 5. Convert peak index to BPM
    if (peakIndex != -1) {
        float freq = (peakIndex * mSamplingRate) / mBufferSize;
        float bpm = freq * 60.0f;
        return bpm;
    }

    return 0.0f;
}