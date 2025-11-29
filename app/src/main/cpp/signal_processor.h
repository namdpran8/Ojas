// app/src/main/cpp/signal_processor.h
#ifndef OJAS_SIGNAL_PROCESSOR_H
#define OJAS_SIGNAL_PROCESSOR_H

#include <vector>
#include <cmath>
#include "kiss_fft.h"

class SignalProcessor {
public:
    SignalProcessor(int bufferSize, float samplingRate);
    ~SignalProcessor();


    void addSample(float greenValue, long timestamp);
    float computeHeartRate();
    const std::vector<float>& getBuffer() const;
    int getSampleCount() const;
    void reset();

private:
    float mPrevHR = 0.0f;

    int mBufferSize;
    float mSamplingRate;
    std::vector<float> mRawBuffer;
    std::vector<long> mTimeBuffer;

    // FFT resources
    kiss_fft_cfg mFftCfg;
    std::vector<kiss_fft_cpx> mFftIn;
    std::vector<kiss_fft_cpx> mFftOut;

    // Helpers
    void normalizeBuffer(const std::vector<float>& input, std::vector<float>& output);
    void applyWindow(std::vector<float>& data);
};

#endif //OJAS_SIGNAL_PROCESSOR_H