package com.beatohm.model

import android.content.Context
import com.beatohm.R

data class EqualizerPreset(
    val id: String,
    val name: String,
    val gainsMb: List<Int>,
    val isBuiltin: Boolean,
    val nameResId: Int = 0
) {
    fun getName(context: Context): String {
        return if (nameResId != 0) context.getString(nameResId) else name
    }

    companion object {
        fun builtinPresets(): List<EqualizerPreset> = listOf(
            EqualizerPreset("flat", "Flat", listOf(0, 0, 0, 0, 0), true, R.string.equalizer_flat),
            EqualizerPreset("pop", "Pop", listOf(-200, 100, 400, 300, 100), true, R.string.equalizer_pop),
            EqualizerPreset("rock", "Rock", listOf(500, 200, -200, 200, 500), true, R.string.equalizer_rock),
            EqualizerPreset("reggaeton", "Reggaeton", listOf(800, 400, -100, 100, -200), true, R.string.equalizer_reggaeton),
            EqualizerPreset("jazz", "Jazz", listOf(200, 100, 200, 100, 400), true, R.string.equalizer_jazz),
            EqualizerPreset("classical", "Classical", listOf(100, 0, -100, -100, 500), true, R.string.equalizer_classical),
            EqualizerPreset("dance", "Dance", listOf(700, 300, 200, -100, -300), true, R.string.equalizer_dance),
            EqualizerPreset("hiphop", "Hip-Hop", listOf(900, 400, -100, 100, -200), true, R.string.equalizer_hiphop),
            EqualizerPreset("bass_boost", "Bass Boost", listOf(1000, 600, 0, -100, -300), true, R.string.equalizer_bass_boost),
            EqualizerPreset("treble_boost", "Treble Boost", listOf(-300, -100, 0, 400, 1000), true, R.string.equalizer_treble_boost)
        )

        fun flatPreset(): EqualizerPreset = builtinPresets().first()
    }
}
