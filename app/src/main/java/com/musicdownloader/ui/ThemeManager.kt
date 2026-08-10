package com.musicdownloader.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.musicdownloader.data.AppDatabase
import com.musicdownloader.data.PresetThemes
import com.musicdownloader.data.UserTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object ThemeManager {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_PRIMARY_COLOR = "primary_color"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_FONT_FAMILY = "font_family"
    private const val KEY_NIGHT_MODE = "night_mode"
    private const val KEY_PLAYER_GRADIENT = "player_gradient"
    private const val KEY_ACTIVE_THEME_ID = "active_theme_id"

    private const val DEFAULT_PRIMARY = 0xFF9D35FF.toInt()
    private const val DEFAULT_ACCENT = 0xFFFF304F.toInt()

    private var prefs: SharedPreferences? = null
    private var db: AppDatabase? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Active theme (in-memory cache) ---
    var activeTheme: UserTheme? = null
        private set

    fun init(context: Context) {
        val ctx = context.applicationContext
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        db = AppDatabase.getInstance(ctx)

        // Load active theme from Room synchronously (single fast read)
        val themeId = prefs?.getLong(KEY_ACTIVE_THEME_ID, -1) ?: -1
        if (themeId > 0) {
            val theme = runBlocking { db?.themeDao()?.getById(themeId) }
            if (theme != null) activeTheme = theme
        }
        if (activeTheme == null) {
            activeTheme = PresetThemes.getDefault()
        }
    }

    fun initSync(context: Context) {
        val ctx = context.applicationContext
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        db = AppDatabase.getInstance(ctx)

        // Load theme ID from prefs and set active theme immediately from presets
        val themeId = prefs?.getLong(KEY_ACTIVE_THEME_ID, -1) ?: -1
        // Set a temporary theme from presets while async load happens
        activeTheme = PresetThemes.getDefault()

        // Load from Room asynchronously but quickly
        scope.launch {
            val theme = if (themeId > 0) {
                db?.themeDao()?.getById(themeId)
            } else {
                null
            }
            if (theme != null) {
                activeTheme = theme
            }
        }
    }

    /**
     * Apply a theme from the database by ID (used after importing).
     */
    suspend fun applyThemeFromDb(context: Context, theme: UserTheme) {
        activeTheme = theme
        prefs?.edit()?.putLong(KEY_ACTIVE_THEME_ID, theme.id)?.apply()
    }

    /**
     * Initialize preset themes in Room if not already present.
     * Call this on first launch or after migration.
     */
    fun ensurePresetThemes() {
        scope.launch {
            val themeDao = db?.themeDao() ?: return@launch
            if (themeDao.count() == 0) {
                themeDao.insertAll(PresetThemes.getAll())
                // Set Neon Night as default active theme
                val neonNight = themeDao.getById(1)
                if (neonNight != null && activeTheme == null) {
                    activeTheme = neonNight
                    prefs?.edit()?.putLong(KEY_ACTIVE_THEME_ID, 1)?.apply()
                }
            }
        }
    }

    /**
     * Migrate legacy SharedPreferences colors to a Room theme.
     * Only runs once (checks if active theme ID is already set).
     */
    fun migrateFromLegacy() {
        scope.launch {
            val themeDao = db?.themeDao() ?: return@launch
            val themeId = prefs?.getLong(KEY_ACTIVE_THEME_ID, -1) ?: -1
            if (themeId > 0) return@launch // Already migrated

            // Check if user has customized colors
            val currentPrimary = prefs?.getInt(KEY_PRIMARY_COLOR, DEFAULT_PRIMARY) ?: DEFAULT_PRIMARY
            val currentAccent = prefs?.getInt(KEY_ACCENT_COLOR, DEFAULT_ACCENT) ?: DEFAULT_ACCENT

            if (currentPrimary != DEFAULT_PRIMARY || currentAccent != DEFAULT_ACCENT) {
                // Create a custom theme from legacy colors
                val customTheme = UserTheme(
                    name = "Mi Tema",
                    primaryColor = currentPrimary,
                    secondaryColor = currentPrimary,
                    accentColor = currentAccent,
                    backgroundColor = 0xFF0B0910.toInt(),
                    surfaceColor = 0xFF12101A.toInt(),
                    textColor = 0xFFFFFFFF.toInt(),
                    fontStyle = prefs?.getString(KEY_FONT_FAMILY, "default") ?: "default",
                    isPreset = false
                )
                val newId = themeDao.insert(customTheme)
                activeTheme = customTheme.copy(id = newId)
                prefs?.edit()?.putLong(KEY_ACTIVE_THEME_ID, newId)?.apply()
            } else {
                // Use Neon Night as default
                ensurePresetThemes()
                val neonNight = themeDao.getById(1)
                if (neonNight != null) {
                    activeTheme = neonNight
                    prefs?.edit()?.putLong(KEY_ACTIVE_THEME_ID, 1)?.apply()
                }
            }
        }
    }

    // --- Active theme management ---

    suspend fun setActiveTheme(theme: UserTheme) {
        activeTheme = theme
        prefs?.edit()?.putLong(KEY_ACTIVE_THEME_ID, theme.id)?.apply()
        // Also update legacy prefs for backward compatibility
        prefs?.edit()
            ?.putInt(KEY_PRIMARY_COLOR, theme.primaryColor)
            ?.putInt(KEY_ACCENT_COLOR, theme.accentColor)
            ?.putString(KEY_FONT_FAMILY, theme.fontStyle)
            ?.apply()
    }

    fun setActiveThemeBlocking(theme: UserTheme) {
        activeTheme = theme
        prefs?.edit()?.putLong(KEY_ACTIVE_THEME_ID, theme.id)?.apply()
        prefs?.edit()
            ?.putInt(KEY_PRIMARY_COLOR, theme.primaryColor)
            ?.putInt(KEY_ACCENT_COLOR, theme.accentColor)
            ?.putString(KEY_FONT_FAMILY, theme.fontStyle)
            ?.apply()
    }

    suspend fun getAllThemes(): List<UserTheme> {
        return db?.themeDao()?.getAll() ?: emptyList()
    }

    suspend fun getCustomThemes(): List<UserTheme> {
        return db?.themeDao()?.getCustom() ?: emptyList()
    }

    suspend fun createCustomTheme(theme: UserTheme): Long {
        return db?.themeDao()?.insert(theme) ?: -1
    }

    suspend fun updateTheme(theme: UserTheme) {
        db?.themeDao()?.update(theme)
        if (activeTheme?.id == theme.id) {
            activeTheme = theme
        }
    }

    suspend fun deleteTheme(theme: UserTheme) {
        db?.themeDao()?.delete(theme)
        if (activeTheme?.id == theme.id) {
            activeTheme = PresetThemes.getDefault()
            prefs?.edit()?.putLong(KEY_ACTIVE_THEME_ID, activeTheme!!.id)?.apply()
        }
    }

    // --- Legacy backward-compatible properties ---
    // These read from the active theme's colors when available, fallback to prefs

    var primaryColor: Int
        get() = activeTheme?.primaryColor ?: (prefs?.getInt(KEY_PRIMARY_COLOR, DEFAULT_PRIMARY) ?: DEFAULT_PRIMARY)
        set(value) {
            prefs?.edit()?.putInt(KEY_PRIMARY_COLOR, value)?.apply()
            activeTheme?.let {
                val updated = it.copy(primaryColor = value)
                activeTheme = updated
                scope.launch { db?.themeDao()?.update(updated) }
            }
        }

    var accentColor: Int
        get() = activeTheme?.accentColor ?: (prefs?.getInt(KEY_ACCENT_COLOR, DEFAULT_ACCENT) ?: DEFAULT_ACCENT)
        set(value) {
            prefs?.edit()?.putInt(KEY_ACCENT_COLOR, value)?.apply()
            activeTheme?.let {
                val updated = it.copy(accentColor = value)
                activeTheme = updated
                scope.launch { db?.themeDao()?.update(updated) }
            }
        }

    var fontFamily: String
        get() = activeTheme?.fontStyle ?: (prefs?.getString(KEY_FONT_FAMILY, "default") ?: "default")
        set(value) {
            prefs?.edit()?.putString(KEY_FONT_FAMILY, value)?.apply()
            activeTheme?.let {
                val updated = it.copy(fontStyle = value)
                activeTheme = updated
                scope.launch { db?.themeDao()?.update(updated) }
            }
        }

    var nightMode: Int
        get() = prefs?.getInt(KEY_NIGHT_MODE, 2) ?: 2
        set(value) { prefs?.edit()?.putInt(KEY_NIGHT_MODE, value)?.apply() }

    var playerGradient: Int
        get() = prefs?.getInt(KEY_PLAYER_GRADIENT, 0) ?: 0
        set(value) { prefs?.edit()?.putInt(KEY_PLAYER_GRADIENT, value)?.apply() }

    // --- Colors from active theme ---

    val backgroundColor: Int
        get() = activeTheme?.backgroundColor ?: 0xFF0B0910.toInt()

    val surfaceColor: Int
        get() = activeTheme?.surfaceColor ?: 0xFF12101A.toInt()

    val textColor: Int
        get() = activeTheme?.textColor ?: 0xFFFFFFFF.toInt()

    val secondaryColor: Int
        get() = activeTheme?.secondaryColor ?: primaryColor

    val currentIconPack: String
        get() {
            val raw = activeTheme?.iconPackId ?: "default"
            return when (raw) {
                "neon" -> {
                    migrateIconPackId("mononoki")
                    "mononoki"
                }
                "minimal" -> {
                    migrateIconPackId("mainstage")
                    "mainstage"
                }
                "bold" -> {
                    migrateIconPackId("darknova")
                    "darknova"
                }
                else -> raw
            }
        }

    private fun migrateIconPackId(newId: String) {
        val theme = activeTheme ?: return
        if (theme.iconPackId == newId) return
        val updated = theme.copy(iconPackId = newId)
        activeTheme = updated
        scope.launch { db?.themeDao()?.update(updated) }
    }

    val currentPlayerLayout: String
        get() = activeTheme?.playerLayoutId ?: "classic"

    // --- Index helpers for overlays ---

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

    // --- Night mode ---

    fun applyNightMode() {
        val mode = when (nightMode) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        val isDark = nightMode == 2
        DynamicGradientDrawable.setThemeMode(isDark)
        WaterVisualizerDrawable.setThemeMode(isDark)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    // --- Color arrays for picker ---

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