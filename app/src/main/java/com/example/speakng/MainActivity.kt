package com.example.speakng

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.speakng.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // Currently selected language code
    private var selectedLang = "en"

    // Supported languages: display name → eSpeak voice name
    private val languages = listOf(
        "English"    to "en",
        "English US" to "en-us",
        "Urdu"       to "ur",
        "Arabic"     to "ar",
        "French"     to "fr",
        "German"     to "de",
        "Spanish"    to "es",
        "Hindi"      to "hi",
        "Chinese"    to "zh",
        "Japanese"   to "ja",
        "Russian"    to "ru",
        "Italian"    to "it"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguageSpinner()
        setupSliders()
        setupButtons()
        observeViewModel()
    }

    // ─── UI Setup ──────────────────────────────────────────

    private fun setupLanguageSpinner() {
        val names = languages.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedLang = languages[pos].second
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSliders() {
        // Speed: 80–500, default 175
        binding.sliderSpeed.apply {
            valueFrom = 80f
            valueTo   = 500f
            value     = 175f
            stepSize  = 5f
        }

        // Pitch: 0–100, default 50
        binding.sliderPitch.apply {
            valueFrom = 0f
            valueTo   = 100f
            value     = 50f
            stepSize  = 1f
        }

        // Volume: 0–200, default 100
        binding.sliderVolume.apply {
            valueFrom = 0f
            valueTo   = 200f
            value     = 100f
            stepSize  = 5f
        }

        // Live label updates
        binding.sliderSpeed.addOnChangeListener  { _, v, _ -> binding.tvSpeedVal.text  = v.toInt().toString() }
        binding.sliderPitch.addOnChangeListener  { _, v, _ -> binding.tvPitchVal.text  = v.toInt().toString() }
        binding.sliderVolume.addOnChangeListener { _, v, _ -> binding.tvVolumeVal.text = v.toInt().toString() }

        // Init labels
        binding.tvSpeedVal.text  = "175"
        binding.tvPitchVal.text  = "50"
        binding.tvVolumeVal.text = "100"
    }

    private fun setupButtons() {
        binding.btnSpeak.setOnClickListener {
            val text = binding.etInput.text.toString().trim()
            if (text.isEmpty()) {
                binding.etInput.error = "Please enter some text"
                return@setOnClickListener
            }

            viewModel.speak(
                text   = text,
                lang   = selectedLang,
                speed  = binding.sliderSpeed.value.toInt(),
                pitch  = binding.sliderPitch.value.toInt(),
                volume = binding.sliderVolume.value.toInt()
            )
        }

        binding.btnStop.setOnClickListener {
            viewModel.stop()
        }
    }

    // ─── Observe ───────────────────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                when (state) {
                    is MainViewModel.SpeakState.Loading -> {
                        binding.btnSpeak.isEnabled = false
                        binding.btnSpeak.text      = "Loading engine..."
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvStatus.text = "Initializing eSpeak NG..."
                    }
                    is MainViewModel.SpeakState.Idle -> {
                        binding.btnSpeak.isEnabled = true
                        binding.btnSpeak.text      = "▶  Speak"
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = "Ready"
                        binding.waveformView.stopAnimation()
                    }
                    is MainViewModel.SpeakState.Speaking -> {
                        binding.btnSpeak.isEnabled = false
                        binding.btnSpeak.text      = "Speaking..."
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvStatus.text = "Synthesizing speech..."
                        binding.waveformView.startAnimation()
                    }
                    is MainViewModel.SpeakState.Done -> {
                        binding.tvStatus.text = "Done ✓"
                        binding.waveformView.stopAnimation()
                    }
                    is MainViewModel.SpeakState.Error -> {
                        binding.btnSpeak.isEnabled = true
                        binding.btnSpeak.text      = "▶  Speak"
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = "Error: ${state.message}"
                        binding.waveformView.stopAnimation()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.logMessages.collectLatest { logs ->
                binding.tvLog.text = logs.takeLast(20).joinToString("\n")
                binding.scrollLog.post { binding.scrollLog.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }
}
