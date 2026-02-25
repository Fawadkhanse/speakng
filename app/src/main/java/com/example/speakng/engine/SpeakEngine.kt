package com.example.speakng.engine

import android.content.Context
import android.content.res.AssetManager
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import kotlin.coroutines.resume

/**
 * SpeakEngine
 *
 * Dual-mode engine:
 *   MODE_ANDROID_TTS  — uses Android's built-in TextToSpeech (works immediately)
 *   MODE_ESPEAK_JNI   — uses eSpeak NG via JNI (requires libespeak-ng.so)
 *
 * Switch mode by changing ENGINE_MODE below.
 */
class SpeakEngine(private val context: Context) {

    private val tag = "SpeakEngine"

    // ── Switch this to MODE_ESPEAK_JNI once you have libespeak-ng.so ──
    private val ENGINE_MODE = Mode.ANDROID_TTS

    enum class Mode { ANDROID_TTS, ESPEAK_JNI }

    // Android TTS
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // eSpeak JNI
    private var jniReady = false

    companion object {
        const val ASSET_DATA_FOLDER = "espeak-ng-data"
    }

    // ─────────────────────────────────────────────────────────
    //  Init
    // ─────────────────────────────────────────────────────────

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        when (ENGINE_MODE) {
            Mode.ANDROID_TTS -> initAndroidTts()
            Mode.ESPEAK_JNI  -> initEspeakJni()
        }
    }

    private suspend fun initAndroidTts(): Boolean =
        suspendCancellableCoroutine { cont ->
            tts = TextToSpeech(context) { status ->
                ttsReady = (status == TextToSpeech.SUCCESS)
                if (ttsReady) {
                    tts?.language = Locale.US
                    Log.i(tag, "Android TTS ready ✓")
                } else {
                    Log.e(tag, "Android TTS init failed: $status")
                }
                cont.resume(ttsReady)
            }
        }

    private suspend fun initEspeakJni(): Boolean = withContext(Dispatchers.IO) {
        val dataPath = getDataPath()
        copyDataFilesIfNeeded(context.assets, dataPath)
        val success = EspeakBridge.init(dataPath)
        jniReady = success
        if (success) Log.i(tag, "eSpeak JNI engine ready ✓")
        else Log.e(tag, "eSpeak JNI init failed!")
        success
    }

    // ─────────────────────────────────────────────────────────
    //  Speak
    // ─────────────────────────────────────────────────────────

    suspend fun speak(
        text: String,
        lang: String = "en",
        speed: Int   = 175,
        pitch: Int   = 50,
        volume: Int  = 100
    ): Boolean = withContext(Dispatchers.IO) {
        when (ENGINE_MODE) {
            Mode.ANDROID_TTS -> speakAndroidTts(text, lang, speed, pitch, volume)
            Mode.ESPEAK_JNI  -> speakEspeakJni(text, lang, speed, pitch, volume)
        }
    }

    private suspend fun speakAndroidTts(
        text: String, lang: String,
        speed: Int, pitch: Int, volume: Int
    ): Boolean = suspendCancellableCoroutine { cont ->
        val ttsInstance = tts
        if (!ttsReady || ttsInstance == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        // Map language code to Locale
        val locale = when (lang) {
            "en", "en-us" -> Locale.US
            "en-gb"       -> Locale.UK
            "fr"          -> Locale.FRENCH
            "de"          -> Locale.GERMAN
            "it"          -> Locale.ITALIAN
            "zh"          -> Locale.CHINESE
            "ja"          -> Locale.JAPANESE
            "ko"          -> Locale.KOREAN
            "ar"          -> Locale("ar")
            "ur"          -> Locale("ur")
            "hi"          -> Locale("hi")
            "ru"          -> Locale("ru")
            "es"          -> Locale("es")
            else          -> Locale.US
        }

        val setResult = ttsInstance.setLanguage(locale)
        if (setResult == TextToSpeech.LANG_MISSING_DATA ||
            setResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(tag, "Language '$lang' not available, falling back to EN")
            ttsInstance.setLanguage(Locale.US)
        }

        // Map eSpeak speed (80-500 wpm) → TTS speech rate (0.5x – 3.0x)
        // eSpeak default 175 wpm = TTS 1.0x
        val speechRate = (speed / 175f).coerceIn(0.5f, 3.0f)

        // Map eSpeak pitch (0-100) → TTS pitch (0.5 – 2.0)
        val ttsPitch = (pitch / 50f).coerceIn(0.5f, 2.0f)

        ttsInstance.setSpeechRate(speechRate)
        ttsInstance.setPitch(ttsPitch)

        val utteranceId = "speakng_${System.currentTimeMillis()}"

        ttsInstance.setOnUtteranceProgressListener(
            object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(true)
                }

                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(false)
                }

                // API 21+ error with message
                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && cont.isActive) cont.resume(false)
                }
            }
        )

        val result = ttsInstance.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )

        if (result == TextToSpeech.ERROR) {
            cont.resume(false)
        }
    }

    private suspend fun speakEspeakJni(
        text: String, lang: String,
        speed: Int, pitch: Int, volume: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (!jniReady) {
            if (!initEspeakJni()) return@withContext false
        }
        EspeakBridge.synthesize(text, lang, speed, pitch, volume)
    }

    // ─────────────────────────────────────────────────────────
    //  Stop / Destroy / Voices
    // ─────────────────────────────────────────────────────────

    fun stop() {
        when (ENGINE_MODE) {
            Mode.ANDROID_TTS -> tts?.stop()
            Mode.ESPEAK_JNI  -> if (jniReady) EspeakBridge.stop()
        }
    }

    fun getVoices(): List<VoiceInfo> {
        return when (ENGINE_MODE) {
            Mode.ANDROID_TTS -> listOf(
                VoiceInfo("English US", "en", "en-us"),
                VoiceInfo("English UK", "en-gb", "en-gb"),
                VoiceInfo("French", "fr", "fr"),
                VoiceInfo("German", "de", "de"),
                VoiceInfo("Italian", "it", "it"),
                VoiceInfo("Spanish", "es", "es"),
                VoiceInfo("Chinese", "zh", "zh"),
                VoiceInfo("Japanese", "ja", "ja"),
                VoiceInfo("Hindi", "hi", "hi"),
                VoiceInfo("Arabic", "ar", "ar"),
                VoiceInfo("Urdu", "ur", "ur"),
                VoiceInfo("Russian", "ru", "ru")
            )
            Mode.ESPEAK_JNI -> {
                if (!jniReady) return emptyList()
                EspeakBridge.getAvailableVoices().map { raw ->
                    val parts = raw.split("|")
                    VoiceInfo(
                        name       = parts.getOrElse(0) { "" },
                        language   = parts.getOrElse(1) { "" },
                        identifier = parts.getOrElse(2) { "" }
                    )
                }
            }
        }
    }

    fun destroy() {
        tts?.shutdown()
        tts = null
        ttsReady = false
        if (jniReady) {
            EspeakBridge.destroy()
            jniReady = false
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Asset helpers (for eSpeak JNI mode)
    // ─────────────────────────────────────────────────────────

    private fun getDataPath(): String =
        "${context.filesDir.absolutePath}/$ASSET_DATA_FOLDER"

    private fun copyDataFilesIfNeeded(assets: AssetManager, destDir: String) {
        val dest = File(destDir)
        if (dest.exists() && dest.listFiles()?.isNotEmpty() == true) return
        Log.i(tag, "Copying espeak-ng-data from assets...")
        copyAssetFolder(assets, ASSET_DATA_FOLDER, destDir)
        Log.i(tag, "Data files copied to: $destDir")
    }

    private fun copyAssetFolder(assets: AssetManager, assetPath: String, destPath: String) {
        val list = assets.list(assetPath) ?: return
        File(destPath).mkdirs()
        for (item in list) {
            val subAsset = "$assetPath/$item"
            val subDest  = "$destPath/$item"
            val subList  = assets.list(subAsset)
            if (subList != null && subList.isNotEmpty()) {
                copyAssetFolder(assets, subAsset, subDest)
            } else {
                copyAssetFile(assets, subAsset, subDest)
            }
        }
    }

    private fun copyAssetFile(assets: AssetManager, assetPath: String, destPath: String) {
        val dest = File(destPath)
        if (dest.exists()) return
        try {
            val input: InputStream       = assets.open(assetPath)
            val output: FileOutputStream = FileOutputStream(dest)
            input.copyTo(output)
            input.close()
            output.close()
        } catch (e: Exception) {
            Log.e(tag, "Failed to copy: $assetPath → $e")
        }
    }
}

data class VoiceInfo(
    val name: String,
    val language: String,
    val identifier: String
)
