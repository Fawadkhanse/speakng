/**
 * audio_player.cpp
 *
 * OpenSL ES audio output for eSpeak NG PCM data.
 * Implements a simple double-buffer strategy.
 */

#include "audio_player.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "AudioPlayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioPlayer::AudioPlayer(int sampleRate)
    : mSampleRate(sampleRate) {
    memset(mBuffers, 0, sizeof(mBuffers));
}

AudioPlayer::~AudioPlayer() {
    destroy();
}

bool AudioPlayer::init() {
    SLresult result;

    // ── Create engine ──────────────────────────────────────
    result = slCreateEngine(&mEngineObj, 0, nullptr, 0, nullptr, nullptr);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("slCreateEngine failed: %d", result);
        return false;
    }
    result = (*mEngineObj)->Realize(mEngineObj, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*mEngineObj)->GetInterface(mEngineObj, SL_IID_ENGINE, &mEngine);
    if (result != SL_RESULT_SUCCESS) return false;

    // ── Create output mix ──────────────────────────────────
    result = (*mEngine)->CreateOutputMix(mEngine, &mOutputMixObj, 0, nullptr, nullptr);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*mOutputMixObj)->Realize(mOutputMixObj, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) return false;

    // ── Audio source: PCM buffer queue ─────────────────────
    SLDataLocator_AndroidSimpleBufferQueue bufferQueueLoc = {
        SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
        (SLuint32)BUFFER_COUNT
    };

    SLDataFormat_PCM pcmFormat = {
        SL_DATAFORMAT_PCM,
        1,                                      // channels (mono)
        (SLuint32)(mSampleRate * 1000),         // milli-Hz
        SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_SPEAKER_FRONT_CENTER,
        SL_BYTEORDER_LITTLEENDIAN
    };

    SLDataSource audioSource = { &bufferQueueLoc, &pcmFormat };

    // ── Audio sink: output mix ─────────────────────────────
    SLDataLocator_OutputMix outputMixLoc = {
        SL_DATALOCATOR_OUTPUTMIX,
        mOutputMixObj
    };
    SLDataSink audioSink = { &outputMixLoc, nullptr };

    // ── Create audio player ────────────────────────────────
    const SLInterfaceID ids[]   = { SL_IID_BUFFERQUEUE };
    const SLboolean     reqs[]  = { SL_BOOLEAN_TRUE };

    result = (*mEngine)->CreateAudioPlayer(
        mEngine, &mPlayerObj,
        &audioSource, &audioSink,
        1, ids, reqs
    );
    if (result != SL_RESULT_SUCCESS) {
        LOGE("CreateAudioPlayer failed: %d", result);
        return false;
    }

    result = (*mPlayerObj)->Realize(mPlayerObj, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*mPlayerObj)->GetInterface(mPlayerObj, SL_IID_PLAY, &mPlayer);
    if (result != SL_RESULT_SUCCESS) return false;

    result = (*mPlayerObj)->GetInterface(mPlayerObj, SL_IID_BUFFERQUEUE, &mBufferQueue);
    if (result != SL_RESULT_SUCCESS) return false;

    // Start playing (will output silence until we enqueue buffers)
    result = (*mPlayer)->SetPlayState(mPlayer, SL_PLAYSTATE_PLAYING);
    if (result != SL_RESULT_SUCCESS) return false;

    LOGI("AudioPlayer initialized. Sample rate: %d Hz", mSampleRate);
    return true;
}

void AudioPlayer::write(short* pcm, int numSamples) {
    if (!mBufferQueue) return;

    int remaining = numSamples;
    int offset    = 0;

    while (remaining > 0) {
        int space  = SAMPLES_PER_BUF - mBufferFill;
        int toCopy = (remaining < space) ? remaining : space;

        memcpy(mBuffers[mCurrentBuffer] + mBufferFill, pcm + offset,
               toCopy * sizeof(short));

        mBufferFill += toCopy;
        offset      += toCopy;
        remaining   -= toCopy;

        if (mBufferFill == SAMPLES_PER_BUF) {
            (*mBufferQueue)->Enqueue(
                mBufferQueue,
                mBuffers[mCurrentBuffer],
                SAMPLES_PER_BUF * sizeof(short)
            );
            mCurrentBuffer = (mCurrentBuffer + 1) % BUFFER_COUNT;
            mBufferFill    = 0;
        }
    }
}

void AudioPlayer::flush() {
    if (!mBufferQueue) return;

    // Enqueue whatever is left in the current buffer
    if (mBufferFill > 0) {
        (*mBufferQueue)->Enqueue(
            mBufferQueue,
            mBuffers[mCurrentBuffer],
            mBufferFill * sizeof(short)
        );
        mCurrentBuffer = (mCurrentBuffer + 1) % BUFFER_COUNT;
        mBufferFill    = 0;
    }

    // Clear queue
    (*mBufferQueue)->Clear(mBufferQueue);
}

void AudioPlayer::destroy() {
    if (mPlayerObj) {
        (*mPlayerObj)->Destroy(mPlayerObj);
        mPlayerObj   = nullptr;
        mPlayer      = nullptr;
        mBufferQueue = nullptr;
    }
    if (mOutputMixObj) {
        (*mOutputMixObj)->Destroy(mOutputMixObj);
        mOutputMixObj = nullptr;
    }
    if (mEngineObj) {
        (*mEngineObj)->Destroy(mEngineObj);
        mEngineObj = nullptr;
        mEngine    = nullptr;
    }
    LOGI("AudioPlayer destroyed");
}
