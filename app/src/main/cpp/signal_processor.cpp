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

    // Initialize KissFFT
    mFftCfg = kiss_fft_alloc(bufferSize, 0, nullptr, nullptr);
    mFftIn.resize(bufferSize);
    mFftOut.resize(bufferSize);
}

SignalProcessor::~SignalProcessor() {
    free(mFftCfg);
}

void SignalProcessor::addSample(float greenValue, long timestamp) {
    if (mRawBuffer.size() >= mBufferSize) {
        mRawBuffer.erase(mRawBuffer.begin());
        mTimeBuffer.erase(mTimeBuffer.begin());
    }
    mRawBuffer.push_back(greenValue);
    mTimeBuffer.push_back(timestamp);
}

void SignalProcessor::reset() {
    mRawBuffer.clear();
    mTimeBuffer.clear();
    mPrevHR = 0.0f;
}

const std::vector<float>& SignalProcessor::getBuffer() const {
    return mRawBuffer;
}

int SignalProcessor::getSampleCount() const {
    return mRawBuffer.size();
}

void SignalProcessor::normalizeBuffer(const std::vector<float>& input, std::vector<float>& output) {
    output = input;
    if (input.empty()) return;

    float sum = std::accumulate(input.begin(), input.end(), 0.0f);
    float mean = sum / input.size();

    for (float &val : output) {
        val -= mean;
    }
}

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
    for (int i = N; i < mBufferSize; ++i) {
        mFftIn[i].r = 0.0f;
        mFftIn[i].i = 0.0f;
    }

    // 3. Execute FFT (Using KissFFT)
    kiss_fft(mFftCfg, mFftIn.data(), mFftOut.data());

    // 4. Define Search Range (45 - 200 BPM)
    float minFreq = 0.75f;
    float maxFreq = 3.33f;

    // Smart Search: Narrow window if we have a previous lock
    if (mPrevHR > 0.0f) {
        float prevFreq = mPrevHR / 60.0f;
        float window = 15.0f / 60.0f; // +/- 15 BPM
        minFreq = std::max(0.75f, prevFreq - window);
        maxFreq = std::min(3.33f, prevFreq + window);
    }

    // 5. Find Peak
    float maxMagnitude = 0.0f;
    int peakIndex = -1;
    float sumMagnitude = 0.0f;
    int countMagnitude = 0;

    for (int i = 1; i < mBufferSize / 2; ++i) {
        float freq = (i * mSamplingRate) / mBufferSize;
        float magnitude = sqrtf(mFftOut[i].r * mFftOut[i].r + mFftOut[i].i * mFftOut[i].i);

        // Calculate average noise in valid range
        if (freq >= 0.75f && freq <= 3.33f) {
            sumMagnitude += magnitude;
            countMagnitude++;
        }

        // Peak Tracking
        if (freq >= minFreq && freq <= maxFreq) {
            if (magnitude > maxMagnitude) {
                maxMagnitude = magnitude;
                peakIndex = i;
            }
        }
    }

    // 6. Validate Signal Quality (SNR)
    if (countMagnitude > 0) {
        float avgMagnitude = sumMagnitude / countMagnitude;
        // Peak must be at least 2x the average noise
        if (maxMagnitude < avgMagnitude * 2.0f) {
            return mPrevHR > 0 ? mPrevHR : 0.0f;
        }
    }

    // 7. Convert to BPM
    if (peakIndex != -1) {
        float freq = (peakIndex * mSamplingRate) / mBufferSize;
        float currentBpm = freq * 60.0f;

        // Smooth update (Exponential Moving Average)
        if (mPrevHR > 0.0f) {
            mPrevHR = (mPrevHR * 0.7f) + (currentBpm * 0.3f);
        } else {
            mPrevHR = currentBpm;
        }
        return mPrevHR;
    }

    return mPrevHR;
}