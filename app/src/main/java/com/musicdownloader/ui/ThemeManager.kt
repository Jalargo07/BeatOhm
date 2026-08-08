package com.musicdownloader.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_PRIMARY_COLOR = "primary_color"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_FONT_FAMILY = "font_family"
    private const val KEY_NIGHT_MODE = "night_mode"
    private const val KEY_PLAYER_GRADIENT = "player_gradient"

    private const val DEFAULT_PRIMARY = 0xFF9D35FF.toInt()
    private const val DEFAULT_ACCENT = 0xFFFF304F.toInt()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var primaryColor: Int
        get() = prefs?.getInt(KEY_PRIMARY_COLOR, DEFAULT_PRIMARY) ?: DEFAULT_PRIMARY
        set(value) { prefs?.edit()?.putInt(KEY_PRIMARY_COLOR, value)?.apply() }

    var accentColor: Int
        get() = prefs?.getInt(KEY_ACCENT_COLOR, DEFAULT_ACCENT) ?: DEFAULT_ACCENT
        set(value) { prefs?.edit()?.putInt(KEY_ACCENT_COLOR, value)?.apply() }

    var fontFamily: String
        get() = prefs?.getString(KEY_FONT_FAMILY, "default") ?: "default"
        set(value) { prefs?.edit()?.putString(KEY_FONT_FAMILY, value)?.apply() }

    var nightMode: Int
        get() = prefs?.getInt(KEY_NIGHT_MODE, 2) ?: 2
        set(value) { prefs?.edit()?.putInt(KEY_NIGHT_MODE, value)?.apply() }

    var playerGradient: Int
        get() = prefs?.getInt(KEY_PLAYER_GRADIENT, 0) ?: 0
        set(value) { prefs?.edit()?.putInt(KEY_PLAYER_GRADIENT, value)?.apply() }

    fun primaryColorIndex(): Int = when (primaryColor) {
        0xFF9D35FF.toInt() -> 0
        0xFF3D7BFF.toInt() -> 1
        0xFF00C2A8.toInt() -> 2
        0xFFFF6B2C.toInt() -> 3
        0xFFFF304F.toInt() -> 4
        0xFF12A150.toInt() -> 5
        0xFFE8A600.toInt() -> 6
        0xFF8B5CF6.toInt() -> 7
        else -> 0
    }

    fun accentColorIndex(): Int = when (accentColor) {
        0xFFFF304F.toInt() -> 0
        0xFFFF6B2C.toInt() -> 1
        0xFF3D7BFF.toInt() -> 2
        0xFF00C2A8.toInt() -> 3
        0xFF12A150.toInt() -> 4
        0xFFE8A600.toInt() -> 5
        0xFF9D35FF.toInt() -> 6
        0xFFFF8FB2.toInt() -> 7
        else -> 0
    }

    fun applyNightMode() {
        val mode = when (nightMode) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    val PRIMARY_COLORS = intArrayOf(
        0xFF9D35FF.toInt(),
        0xFF3D7BFF.toInt(),
        0xFF00C2A8.toInt(),
        0xFFFF6B2C.toInt(),
        0xFFFF304F.toInt(),
        0xFF12A150.toInt(),
        0xFFE8A600.toInt(),
        0xFF8B5CF6.toInt()
    )

    val ACCENT_COLORS = intArrayOf(
        0xFFFF304F.toInt(),
        0xFFFF6B2C.toInt(),
        0xFF3D7BFF.toInt(),
        0xFF00C2A8.toInt(),
        0xFF12A150.toInt(),
        0xFFE8A600.toInt(),
        0xFF9D35FF.toInt(),
        0xFFFF8FB2.toInt()
    )
}
