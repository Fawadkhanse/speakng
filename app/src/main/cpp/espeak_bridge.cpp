/**
 * espeak_bridge.cpp
 *
 * JNI bridge: Kotlin  ↔  eSpeak NG C library
 *
 * Responsibilities:
 *  - Initialize eSpeak NG with the data path copied from assets
 *  - Set synthesis parameters (speed, pitch, volume)
 *  - Set voice/language
 *  - Run synthesis and pipe PCM audio to AudioPlayer (OpenSL ES)
 */

#include <jni.h>
#include <string>
#include <cstring>
#include <android/log.h>
#include "audio_player.h"

// eSpeak NG public API headers
// These are bundled in app/src/main/cpp/include/
#include "espeak-ng/espeak_ng.h"
#include "espeak-ng/speak_lib.h"

#define LOG_TAG "SpeakNG-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Global audio player instance
static AudioPlayer* gAudioPlayer = nullptr;
static bool         gInitialized = false;

// ─────────────────────────────────────────────────────────
//  Synthesis callback
//  Called by eSpeak NG when PCM samples are ready.
//  We pipe them directly into OpenSL ES.
// ─────────────────────────────────────────────────────────
static int synthCallback(short* wav, int numSamples, espeak_EVENT* events) {
    if (wav == nullptr) {
        // NULL wav = synthesis complete
        LOGD("Synthesis complete (null wav signal)");
        return 0;
    }
    if (gAudioPlayer && numSamples > 0) {
        gAudioPlayer->write(wav, numSamples);
    }
    return 0;  // 0 = continue, 1 = abort
}

// ─────────────────────────────────────────────────────────
//  JNI: init(dataPath: String)
//  Call once from Kotlin after copying assets to internal storage
// ─────────────────────────────────────────────────────────
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_speakng_engine_EspeakBridge_init(
        JNIEnv* env,
        jobject /* obj */,
        jstring dataPath) {

    if (gInitialized) {
        LOGI("Already initialized, skipping");
        return JNI_TRUE;
    }

    const char* path = env->GetStringUTFChars(dataPath, nullptr);
    LOGI("Initializing eSpeak NG with data path: %s", path);

    // Set data path before initializing
    espeak_ng_InitializePath(path);
    env->ReleaseStringUTFChars(dataPath, path);

    // Initialize eSpeak NG
    espeak_ng_ERROR_CONTEXT context = nullptr;
    espeak_ng_STATUS status = espeak_ng_Initialize(&context);
    if (status != ENS_OK) {
        LOGE("espeak_ng_Initialize failed with status: %d", status);
        espeak_ng_ClearErrorContext(&context);
        return JNI_FALSE;
    }

    // Initialize audio output in AudioPlayer mode
    // AUDIO_OUTPUT_RETRIEVAL = no direct output, we handle it in callback
    int sampleRate = espeak_Initialize(
            AUDIO_OUTPUT_RETRIEVAL,
            500,    // buffer length ms
            nullptr,
            0       // options
    );
    LOGI("eSpeak initialized. Sample rate: %d Hz", sampleRate);

    // Create audio player with matching sample rate
    gAudioPlayer = new AudioPlayer(sampleRate);
    if (!gAudioPlayer->init()) {
        LOGE("Failed to initialize OpenSL ES AudioPlayer");
        delete gAudioPlayer;
        gAudioPlayer = nullptr;
        return JNI_FALSE;
    }

    // Register our PCM callback
    espeak_SetSynthCallback(synthCallback);

    gInitialized = true;
    LOGI("eSpeak NG bridge initialized successfully");
    return JNI_TRUE;
}

// ─────────────────────────────────────────────────────────
//  JNI: synthesize(text, lang, speed, pitch, volume)
//  Called from Kotlin on a background thread
// ─────────────────────────────────────────────────────────
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_speakng_engine_EspeakBridge_synthesize(
        JNIEnv* env,
        jobject /* obj */,
        jstring text,
        jstring lang,
        jint speed,
        jint pitch,
        jint volume) {

    if (!gInitialized || !gAudioPlayer) {
        LOGE("Engine not initialized!");
        return JNI_FALSE;
    }

    // Clamp parameters to safe ranges
    int safeSpeed  = std::max(80,  std::min(500, (int)speed));
    int safePitch  = std::max(0,   std::min(100, (int)pitch));
    int safeVolume = std::max(0,   std::min(200, (int)volume));

    LOGD("synthesize() speed=%d pitch=%d volume=%d", safeSpeed, safePitch, safeVolume);

    // Apply parameters
    espeak_SetParameter(espeakRATE,   safeSpeed,  0);
    espeak_SetParameter(espeakPITCH,  safePitch,  0);
    espeak_SetParameter(espeakVOLUME, safeVolume, 0);

    // Set voice/language
    const char* langStr = env->GetStringUTFChars(lang, nullptr);
    espeak_ERROR voiceErr = espeak_SetVoiceByName(langStr);
    if (voiceErr != EE_OK) {
        LOGE("Failed to set voice '%s', falling back to 'en'", langStr);
        espeak_SetVoiceByName("en");
    }
    env->ReleaseStringUTFChars(lang, langStr);

    // Synthesize
    const char* textStr = env->GetStringUTFChars(text, nullptr);
    size_t textLen = strlen(textStr) + 1;

    LOGI("Synthesizing: \"%s\"", textStr);

    espeak_ERROR synthErr = espeak_Synth(
            textStr,
            textLen,
            0,                  // position (char offset)
            POS_CHARACTER,      // position type
            0,                  // end position (0 = to end)
            espeakCHARS_UTF8,   // text flags - UTF-8 input
            nullptr,            // unique identifier
            nullptr             // user data
    );

    env->ReleaseStringUTFChars(text, textStr);

    if (synthErr != EE_OK) {
        LOGE("espeak_Synth failed: %d", synthErr);
        return JNI_FALSE;
    }

    // Block until synthesis is complete
    espeak_Synchronize();
    LOGI("Synthesis complete");
    return JNI_TRUE;
}

// ─────────────────────────────────────────────────────────
//  JNI: stop()
//  Cancel any ongoing synthesis
// ─────────────────────────────────────────────────────────
extern "C"
JNIEXPORT void JNICALL
Java_com_example_speakng_engine_EspeakBridge_stop(
        JNIEnv* /* env */,
        jobject /* obj */) {
    LOGI("Stop requested");
    espeak_Cancel();
    if (gAudioPlayer) {
        gAudioPlayer->flush();
    }
}

// ─────────────────────────────────────────────────────────
//  JNI: destroy()
//  Release all native resources
// ─────────────────────────────────────────────────────────
extern "C"
JNIEXPORT void JNICALL
Java_com_example_speakng_engine_EspeakBridge_destroy(
        JNIEnv* /* env */,
        jobject /* obj */) {
    LOGI("Destroying eSpeak NG bridge");
    espeak_ng_Terminate();
    if (gAudioPlayer) {
        gAudioPlayer->destroy();
        delete gAudioPlayer;
        gAudioPlayer = nullptr;
    }
    gInitialized = false;
}

// ─────────────────────────────────────────────────────────
//  JNI: getAvailableVoices() → Array<String>
// ─────────────────────────────────────────────────────────
extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_example_speakng_engine_EspeakBridge_getAvailableVoices(
        JNIEnv* env,
        jobject /* obj */) {

    const espeak_VOICE** voices = espeak_ListVoices(nullptr);
    int count = 0;
    while (voices[count] != nullptr) count++;

    jclass strClass = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(count, strClass, nullptr);

    for (int i = 0; i < count; i++) {
        const espeak_VOICE* v = voices[i];
        // format: "name|language|identifier"
        std::string entry = std::string(v->name ? v->name : "") + "|" +
                            std::string(v->languages ? v->languages + 1 : "") + "|" +
                            std::string(v->identifier ? v->identifier : "");
        env->SetObjectArrayElement(arr, i, env->NewStringUTF(entry.c_str()));
    }

    return arr;
}
