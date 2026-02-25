//
// Created by Fawad Ali on 2/25/2026.
//
/**
 * espeak_bridge_stub.cpp
 *
 * TEMPORARY STUB — Phase 1
 *
 * Implements all JNI functions expected by EspeakBridge.kt
 * without requiring libespeak-ng.so or its headers.
 *
 * The app will fully build and run. The Speak button simulates
 * a 1-second delay so all UI state transitions work correctly.
 *
 * Replace with espeak_bridge.cpp + audio_player.cpp in Phase 2.
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <time.h>

#define LOG_TAG "SpeakNG"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_speakng_engine_EspeakBridge_init(
        JNIEnv* env, jobject, jstring dataPath) {
    const char* path = env->GetStringUTFChars(dataPath, nullptr);
    LOGI("[STUB] init() dataPath=%s", path);
    env->ReleaseStringUTFChars(dataPath, path);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_speakng_engine_EspeakBridge_synthesize(
        JNIEnv* env, jobject,
        jstring text, jstring lang,
        jint speed, jint pitch, jint volume) {

    const char* t = env->GetStringUTFChars(text, nullptr);
    const char* l = env->GetStringUTFChars(lang, nullptr);
    LOGI("[STUB] synthesize() lang=%s speed=%d pitch=%d vol=%d text=\"%s\"",
         l, (int)speed, (int)pitch, (int)volume, t);
    env->ReleaseStringUTFChars(text, t);
    env->ReleaseStringUTFChars(lang, l);

    // Simulate synthesis delay so UI transitions feel real
    struct timespec ts = {1, 0};
    nanosleep(&ts, nullptr);

    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_speakng_engine_EspeakBridge_stop(JNIEnv*, jobject) {
    LOGI("[STUB] stop()");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_speakng_engine_EspeakBridge_destroy(JNIEnv*, jobject) {
    LOGI("[STUB] destroy()");
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_example_speakng_engine_EspeakBridge_getAvailableVoices(
        JNIEnv* env, jobject) {
    LOGI("[STUB] getAvailableVoices()");

    const char* voices[] = {
            "English|en|en",
            "English US|en-us|en/en-us",
            "Urdu|ur|ur",
            "Arabic|ar|ar",
            "French|fr|fr",
            "German|de|de",
            "Spanish|es|es",
            "Hindi|hi|hi",
            "Chinese|zh|zh",
            "Japanese|ja|ja",
            "Russian|ru|ru",
            "Italian|it|it"
    };
    int count = 12;

    jclass strClass = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(count, strClass, nullptr);
    for (int i = 0; i < count; i++) {
        env->SetObjectArrayElement(arr, i, env->NewStringUTF(voices[i]));
    }
    return arr;
}
