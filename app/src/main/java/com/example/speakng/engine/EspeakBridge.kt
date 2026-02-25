package com.example.speakng.engine

import android.util.Log

/**
 * EspeakBridge
 *
 * Kotlin wrapper around the native eSpeak NG JNI library.
 * All external functions map 1:1 to functions in espeak_bridge.cpp.
 *
 * Usage:
 *   val bridge = EspeakBridge()
 *   bridge.init("/data/data/com.example.speakng/files/espeak-ng-data")
 *   bridge.synthesize("Hello World", "en", 175, 50, 100)
 *   bridge.stop()
 *   bridge.destroy()
 */
object EspeakBridge {

    private const val TAG = "EspeakBridge"

    init {
        try {
            System.loadLibrary("speakng-jni")
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
        }
    }

    /**
     * Initialize the eSpeak NG engine.
     *
     * @param dataPath Absolute path to the espeak-ng-data directory.
     *                 Copy this from assets to internal storage first!
     * @return true if initialization succeeded
     */
    external fun init(dataPath: String): Boolean

    /**
     * Synthesize text to speech.
     * Blocks until synthesis is complete — call from a background thread!
     *
     * @param text   UTF-8 text to synthesize
     * @param lang   Language/voice name, e.g. "en", "en-us", "fr", "de"
     * @param speed  Words per minute. Range: 80–500. Default: 175
     * @param pitch  Pitch value. Range: 0–100. Default: 50
     * @param volume Volume. Range: 0–200. Default: 100
     * @return true if synthesis succeeded
     */
    external fun synthesize(
        text: String,
        lang: String,
        speed: Int,
        pitch: Int,
        volume: Int
    ): Boolean

    /**
     * Cancel any ongoing synthesis immediately.
     */
    external fun stop()

    /**
     * Release all native resources. Call when done.
     */
    external fun destroy()

    /**
     * Returns list of available voices as "name|language|identifier" strings.
     */
    external fun getAvailableVoices(): Array<String>
}
