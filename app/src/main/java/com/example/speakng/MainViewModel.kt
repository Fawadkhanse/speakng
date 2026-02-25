package com.example.speakng

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speakng.engine.SpeakEngine
import com.example.speakng.engine.VoiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MainViewModel
 *
 * Holds UI state and orchestrates calls to SpeakEngine.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = SpeakEngine(application)

    // ── UI State ───────────────────────────────────────────

    sealed class SpeakState {
        object Idle      : SpeakState()
        object Loading   : SpeakState()
        object Speaking  : SpeakState()
        data class Error(val message: String) : SpeakState()
        object Done      : SpeakState()
    }

    private val _state = MutableStateFlow<SpeakState>(SpeakState.Loading)
    val state: StateFlow<SpeakState> = _state.asStateFlow()

    private val _voices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    val voices: StateFlow<List<VoiceInfo>> = _voices.asStateFlow()

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    // ── Init ───────────────────────────────────────────────

    init {
        viewModelScope.launch {
            log("Initializing eSpeak NG engine...")
            val ok = engine.init()
            if (ok) {
                log("Engine ready ✓")
                _state.value = SpeakState.Idle
                _voices.value = engine.getVoices()
                log("Loaded ${_voices.value.size} voices")
            } else {
                log("Engine initialization FAILED")
                _state.value = SpeakState.Error("Engine failed to initialize")
            }
        }
    }

    // ── Actions ────────────────────────────────────────────

    fun speak(
        text: String,
        lang: String,
        speed: Int,
        pitch: Int,
        volume: Int
    ) {
        if (_state.value == SpeakState.Speaking) return

        viewModelScope.launch {
            _state.value = SpeakState.Speaking
            log("Synthesizing: \"${text.take(50)}...\"")
            log("  lang=$lang  speed=$speed  pitch=$pitch  vol=$volume")

            val success = engine.speak(
                text   = text,
                lang   = lang,
                speed  = speed,
                pitch  = pitch,
                volume = volume
            )

            if (success) {
                log("Synthesis complete ✓")
                _state.value = SpeakState.Done
            } else {
                log("Synthesis failed ✗")
                _state.value = SpeakState.Error("Synthesis failed")
            }

            // Return to idle after a short moment
            kotlinx.coroutines.delay(500)
            _state.value = SpeakState.Idle
        }
    }

    fun stop() {
        engine.stop()
        log("Stopped")
        _state.value = SpeakState.Idle
    }

    // ── Lifecycle ──────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        engine.destroy()
    }

    // ── Helpers ────────────────────────────────────────────

    private fun log(msg: String) {
        val current = _logMessages.value.toMutableList()
        current.add(msg)
        if (current.size > 100) current.removeAt(0) // cap log size
        _logMessages.value = current
    }
}
