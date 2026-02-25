#pragma once

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <cstdint>

/**
 * AudioPlayer
 *
 * Wraps OpenSL ES to play PCM audio produced by eSpeak NG.
 * - 16-bit signed PCM
 * - Mono channel
 * - Sample rate set by eSpeak NG (typically 22050 Hz)
 */
class AudioPlayer {
public:
    explicit AudioPlayer(int sampleRate);
    ~AudioPlayer();

    bool init();
    void write(short* pcm, int numSamples);
    void flush();
    void destroy();

private:
    int mSampleRate;

    // OpenSL ES objects
    SLObjectItf mEngineObj     = nullptr;
    SLEngineItf mEngine        = nullptr;
    SLObjectItf mOutputMixObj  = nullptr;
    SLObjectItf mPlayerObj     = nullptr;
    SLPlayItf   mPlayer        = nullptr;
    SLAndroidSimpleBufferQueueItf mBufferQueue = nullptr;

    static constexpr int BUFFER_COUNT     = 4;
    static constexpr int SAMPLES_PER_BUF  = 4096;

    short mBuffers[BUFFER_COUNT][SAMPLES_PER_BUF];
    int   mCurrentBuffer = 0;
    int   mBufferFill    = 0;
};
