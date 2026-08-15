package com.beatohm.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioVisualizerManager {

    private var _isActive = false
    private val _levels = MutableStateFlow(FloatArray(BAND_COUNT) { 0f })
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()
    val isActive: Boolean get() = _isActive

    fun start() {
        _isActive = true
    }

    fun updateFromProcessor(processor: LevelCaptureProcessor) {
        _levels.value = processor.bands.copyOf()
    }

    fun stop() {
        _isActive = false
    }

    companion object {
        private const val BAND_COUNT = 5
    }
}
