package com.example.speakng.engine

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * SpeakEngine
 *
 * High-level wrapper around EspeakBridge.
 * Handles:
 *   - First-run copying of espeak-ng-data from assets → internal storage
 *   - Engine lifecycle
 *   - Suspend functions for easy coroutine usage
 */
class SpeakEngine(private val context: Context) {

    private val tag = "SpeakEngine"
    private var isReady = false

    companion object {
        // Name of the folder inside assets/ that contains eSpeak data files
        const val ASSET_DATA_FOLDER = "espeak-ng-data"
    }

    /**
     * Initialize the engine. Safe to call multiple times.
     * Should be called from a background coroutine or IO thread.
     */
    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        if (isReady) return@withContext true

        // Step 1: Ensure data files are on disk
        val dataPath = getDataPath()
        copyDataFilesIfNeeded(context.assets, dataPath)

        // Step 2: Initialize native engine
        val success = EspeakBridge.init(dataPath)
        isReady = success

        if (success) {
            Log.i(tag, "eSpeak NG engine ready. Data path: $dataPath")
        } else {
            Log.e(tag, "Engine initialization failed!")
        }
        success
    }

    /**
     * Speak the given text.
     * Automatically initializes the engine if not done yet.
     */
    suspend fun speak(
        text: String,
        lang: String  = "en",
        speed: Int    = 175,
        pitch: Int    = 50,
        volume: Int   = 100
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isReady) init()
        if (!isReady) return@withContext false
        EspeakBridge.synthesize(text, lang, speed, pitch, volume)
    }

    /**
     * Stop any ongoing speech immediately.
     */
    fun stop() {
        if (isReady) EspeakBridge.stop()
    }

    /**
     * Get list of available voices.
     */
    fun getVoices(): List<VoiceInfo> {
        if (!isReady) return emptyList()
        return EspeakBridge.getAvailableVoices().map { raw ->
            val parts = raw.split("|")
            VoiceInfo(
                name       = parts.getOrElse(0) { "" },
                language   = parts.getOrElse(1) { "" },
                identifier = parts.getOrElse(2) { "" }
            )
        }
    }

    /**
     * Release native resources. Call from onDestroy.
     */
    fun destroy() {
        if (isReady) {
            EspeakBridge.destroy()
            isReady = false
        }
    }

    // ─── Internal helpers ──────────────────────────────────

    private fun getDataPath(): String =
        "${context.filesDir.absolutePath}/$ASSET_DATA_FOLDER"

    /**
     * Copies espeak-ng-data from assets to internal storage on first run.
     * Skips if already copied.
     */
    private fun copyDataFilesIfNeeded(assets: AssetManager, destDir: String) {
        val dest = File(destDir)
        if (dest.exists() && dest.listFiles()?.isNotEmpty() == true) {
            Log.d(tag, "Data files already present, skipping copy")
            return
        }
        Log.i(tag, "Copying espeak-ng-data from assets...")
        copyAssetFolder(assets, ASSET_DATA_FOLDER, destDir)
        Log.i(tag, "Data files copied to: $destDir")
    }

    private fun copyAssetFolder(assets: AssetManager, assetPath: String, destPath: String) {
        val list = assets.list(assetPath) ?: return
        val destDir = File(destPath)
        destDir.mkdirs()

        for (item in list) {
            val subAsset = "$assetPath/$item"
            val subDest  = "$destPath/$item"
            val subList  = assets.list(subAsset)
            if (subList != null && subList.isNotEmpty()) {
                // Directory — recurse
                copyAssetFolder(assets, subAsset, subDest)
            } else {
                // File — copy
                copyAssetFile(assets, subAsset, subDest)
            }
        }
    }

    private fun copyAssetFile(assets: AssetManager, assetPath: String, destPath: String) {
        val dest = File(destPath)
        if (dest.exists()) return
        try {
            val input: InputStream  = assets.open(assetPath)
            val output: FileOutputStream = FileOutputStream(dest)
            input.copyTo(output)
            input.close()
            output.close()
        } catch (e: Exception) {
            Log.e(tag, "Failed to copy asset: $assetPath → $e")
        }
    }
}

data class VoiceInfo(
    val name: String,
    val language: String,
    val identifier: String
)
