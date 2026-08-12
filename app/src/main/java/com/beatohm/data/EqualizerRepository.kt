package com.beatohm.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.beatohm.model.EqualizerPreset

class EqualizerRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getActivePreset(): EqualizerPreset {
        val id = prefs.getString(KEY_ACTIVE_PRESET_ID, null) ?: return EqualizerPreset.flatPreset()
        val allBuiltin = EqualizerPreset.builtinPresets()
        val builtin = allBuiltin.find { it.id == id }
        if (builtin != null) return builtin
        val customs = getCustomPresets()
        return customs.find { it.id == id } ?: EqualizerPreset.flatPreset()
    }

    fun setActivePreset(presetId: String) {
        prefs.edit().putString(KEY_ACTIVE_PRESET_ID, presetId).apply()
    }

    fun getBandGains(): List<Int> {
        val gains = mutableListOf<Int>()
        for (i in 0 until 5) {
            gains.add(prefs.getInt("${KEY_BAND_PREFIX}$i", 0))
        }
        return gains
    }

    fun setBandGains(gains: List<Int>) {
        val editor = prefs.edit()
        for (i in gains.indices.take(5)) {
            editor.putInt("${KEY_BAND_PREFIX}$i", gains[i])
        }
        editor.apply()
    }

    fun getCustomPresets(): List<EqualizerPreset> {
        val json = prefs.getString(KEY_CUSTOM_PRESETS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<EqualizerPreset>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomPreset(preset: EqualizerPreset) {
        val customs = getCustomPresets().toMutableList()
        val index = customs.indexOfFirst { it.id == preset.id }
        if (index >= 0) {
            customs[index] = preset
        } else {
            customs.add(preset)
        }
        prefs.edit().putString(KEY_CUSTOM_PRESETS, gson.toJson(customs)).apply()
    }

    fun deleteCustomPreset(presetId: String) {
        val customs = getCustomPresets().toMutableList()
        customs.removeAll { it.id == presetId }
        prefs.edit().putString(KEY_CUSTOM_PRESETS, gson.toJson(customs)).apply()
    }

    companion object {
        private const val PREFS_NAME = "player_prefs"
        private const val KEY_ACTIVE_PRESET_ID = "eq_active_preset_id"
        private const val KEY_BAND_PREFIX = "eq_band_"
        private const val KEY_CUSTOM_PRESETS = "eq_custom_presets"
    }
}
