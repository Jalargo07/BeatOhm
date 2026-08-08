package com.musicdownloader.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.musicdownloader.R

/**
 * Represents a pack of icons for the app.
 * Each pack provides drawable resource IDs for every icon used.
 */
data class IconPack(
    val id: String,
    val displayName: String,
    val icons: Map<String, Int>
) {
    fun getIcon(name: String, context: Context): Drawable? {
        val resId = icons[name] ?: return null
        return ContextCompat.getDrawable(context, resId)
    }

    fun getIconResId(name: String): Int? = icons[name]
}

object IconPackManager {

    /** Canonical icon names used throughout the app */
    const val ICON_PLAY = "play"
    const val ICON_PAUSE = "pause"
    const val ICON_NEXT = "next"
    const val ICON_PREV = "prev"
    const val ICON_SHUFFLE = "shuffle"
    const val ICON_REPEAT = "repeat"
    const val ICON_REPEAT_ONE = "repeat_one"
    const val ICON_HEART = "heart"
    const val ICON_HEART_BORDER = "heart_border"
    const val ICON_SEARCH = "search"
    const val ICON_SETTINGS = "settings"
    const val ICON_VOLUME = "volume"
    const val ICON_PLAYLIST_ADD = "playlist_add"
    const val ICON_EQUALIZER = "equalizer"
    const val ICON_QUEUE = "queue"
    const val ICON_LYRICS = "lyrics"
    const val ICON_MUSIC_NOTE = "music_note"
    const val ICON_BACK = "back"

    private val allPacks = mutableListOf<IconPack>()

    init {
        allPacks.add(defaultPack())
        allPacks.add(outlinePack())
        allPacks.add(filledPack())
        allPacks.add(neonPack())
        allPacks.add(minimalPack())
        allPacks.add(boldPack())
    }

    fun getAllPacks(): List<IconPack> = allPacks.toList()

    /**
     * Get player-specific icon resource IDs based on the active icon pack.
     * Returns a map of semantic name to drawable resource ID.
     */
    fun getPlayerIconResIds(packId: String): Map<String, Int> {
        val pack = getPackById(packId)
        return mapOf(
            ICON_PLAY to (pack.getIconResId(ICON_PLAY) ?: R.drawable.ic_play),
            ICON_PAUSE to (pack.getIconResId(ICON_PAUSE) ?: R.drawable.ic_pause),
            ICON_NEXT to (pack.getIconResId(ICON_NEXT) ?: R.drawable.ic_next),
            ICON_PREV to (pack.getIconResId(ICON_PREV) ?: R.drawable.ic_prev),
            ICON_SHUFFLE to (pack.getIconResId(ICON_SHUFFLE) ?: R.drawable.ic_shuffle),
            ICON_REPEAT to (pack.getIconResId(ICON_REPEAT) ?: R.drawable.ic_repeat),
            ICON_REPEAT_ONE to (pack.getIconResId(ICON_REPEAT_ONE) ?: R.drawable.ic_repeat_one),
            ICON_HEART to (pack.getIconResId(ICON_HEART) ?: R.drawable.ic_favorite),
            ICON_HEART_BORDER to (pack.getIconResId(ICON_HEART_BORDER) ?: R.drawable.ic_bookmark_border),
            ICON_EQUALIZER to (pack.getIconResId(ICON_EQUALIZER) ?: R.drawable.ic_equalizer),
            ICON_QUEUE to (pack.getIconResId(ICON_QUEUE) ?: R.drawable.ic_queue_music),
            ICON_LYRICS to (pack.getIconResId(ICON_LYRICS) ?: R.drawable.ic_lyrics),
        )
    }

    fun getPackById(id: String): IconPack =
        allPacks.find { it.id == id } ?: defaultPack()

    fun getIcon(name: String, packId: String, context: Context): Drawable? {
        val pack = getPackById(packId)
        return pack.getIcon(name, context)
    }

    // ── Default Pack ──────────────────────────────────────────────

    private fun defaultPack() = IconPack(
        id = "default",
        displayName = "Default",
        icons = mapOf(
            ICON_PLAY to R.drawable.ic_play,
            ICON_PAUSE to R.drawable.ic_pause,
            ICON_NEXT to R.drawable.ic_next,
            ICON_PREV to R.drawable.ic_prev,
            ICON_SHUFFLE to R.drawable.ic_shuffle,
            ICON_REPEAT to R.drawable.ic_repeat,
            ICON_REPEAT_ONE to R.drawable.ic_repeat_one,
            ICON_HEART to R.drawable.ic_favorite,
            ICON_HEART_BORDER to R.drawable.ic_bookmark_border,
            ICON_SEARCH to R.drawable.ic_search,
            ICON_SETTINGS to R.drawable.ic_settings,
            ICON_VOLUME to R.drawable.ic_volume,
            ICON_PLAYLIST_ADD to R.drawable.ic_playlist_add,
            ICON_EQUALIZER to R.drawable.ic_equalizer,
            ICON_QUEUE to R.drawable.ic_queue_music,
            ICON_LYRICS to R.drawable.ic_lyrics,
            ICON_MUSIC_NOTE to R.drawable.ic_music_note,
            ICON_BACK to R.drawable.ic_back
        )
    )

    // ── Outline Pack (uses outline variants where available) ──────

    private fun outlinePack() = IconPack(
        id = "outline",
        displayName = "Outline",
        icons = mapOf(
            ICON_PLAY to R.drawable.ic_play_outline,
            ICON_PAUSE to R.drawable.ic_pause_outline,
            ICON_NEXT to R.drawable.ic_next_outline,
            ICON_PREV to R.drawable.ic_prev_outline,
            ICON_SHUFFLE to R.drawable.ic_shuffle_outline,
            ICON_REPEAT to R.drawable.ic_repeat_outline,
            ICON_REPEAT_ONE to R.drawable.ic_repeat_outline,
            ICON_HEART to R.drawable.ic_heart_outline,
            ICON_HEART_BORDER to R.drawable.ic_heart_outline,
            ICON_SEARCH to R.drawable.ic_search,
            ICON_SETTINGS to R.drawable.ic_settings_outline,
            ICON_VOLUME to R.drawable.ic_volume,
            ICON_PLAYLIST_ADD to R.drawable.ic_playlist_add,
            ICON_EQUALIZER to R.drawable.ic_equalizer,
            ICON_QUEUE to R.drawable.ic_queue_music,
            ICON_LYRICS to R.drawable.ic_lyrics,
            ICON_MUSIC_NOTE to R.drawable.ic_music_note,
            ICON_BACK to R.drawable.ic_back
        )
    )

    // ── Filled Pack ───────────────────────────────────────────────

    private fun filledPack() = IconPack(
        id = "filled",
        displayName = "Filled",
        icons = mapOf(
            ICON_PLAY to R.drawable.ic_play,
            ICON_PAUSE to R.drawable.ic_pause,
            ICON_NEXT to R.drawable.ic_next,
            ICON_PREV to R.drawable.ic_prev,
            ICON_SHUFFLE to R.drawable.ic_shuffle,
            ICON_REPEAT to R.drawable.ic_repeat,
            ICON_REPEAT_ONE to R.drawable.ic_repeat_one,
            ICON_HEART to R.drawable.ic_favorite,
            ICON_HEART_BORDER to R.drawable.ic_bookmark_border,
            ICON_SEARCH to R.drawable.ic_search,
            ICON_SETTINGS to R.drawable.ic_settings,
            ICON_VOLUME to R.drawable.ic_volume,
            ICON_PLAYLIST_ADD to R.drawable.ic_playlist_add,
            ICON_EQUALIZER to R.drawable.ic_equalizer,
            ICON_QUEUE to R.drawable.ic_queue_music,
            ICON_LYRICS to R.drawable.ic_lyrics,
            ICON_MUSIC_NOTE to R.drawable.ic_music_note,
            ICON_BACK to R.drawable.ic_back
        )
    )

    // ── Neon Pack ──────────────────────────────────────────────────
    // Glowing outlines + neon fill for the main play button

    private fun neonPack() = IconPack(
        id = "neon",
        displayName = "Neon",
        icons = mapOf(
            ICON_PLAY to R.drawable.ic_nn_play,
            ICON_PAUSE to R.drawable.ic_nn_pause,
            ICON_NEXT to R.drawable.ic_nn_next,
            ICON_PREV to R.drawable.ic_nn_prev,
            ICON_SHUFFLE to R.drawable.ic_nn_shuffle,
            ICON_REPEAT to R.drawable.ic_nn_repeat,
            ICON_REPEAT_ONE to R.drawable.ic_nn_repeat_one,
            ICON_HEART to R.drawable.ic_nn_heart,
            ICON_HEART_BORDER to R.drawable.ic_nn_heart_border,
            ICON_SEARCH to R.drawable.ic_nn_search,
            ICON_SETTINGS to R.drawable.ic_nn_settings,
            ICON_VOLUME to R.drawable.ic_nn_volume,
            ICON_PLAYLIST_ADD to R.drawable.ic_playlist_add,
            ICON_EQUALIZER to R.drawable.ic_nn_equalizer,
            ICON_QUEUE to R.drawable.ic_nn_queue,
            ICON_LYRICS to R.drawable.ic_nn_lyrics,
            ICON_MUSIC_NOTE to R.drawable.ic_nn_music_note,
            ICON_BACK to R.drawable.ic_nn_back
        )
    )

    // ── Minimal Pack ──────────────────────────────────────────────
    // Ultra-thin geometric lines, geometric simplicity

    private fun minimalPack() = IconPack(
        id = "minimal",
        displayName = "Minimal",
        icons = mapOf(
            ICON_PLAY to R.drawable.ic_mn_play,
            ICON_PAUSE to R.drawable.ic_mn_pause,
            ICON_NEXT to R.drawable.ic_mn_next,
            ICON_PREV to R.drawable.ic_mn_prev,
            ICON_SHUFFLE to R.drawable.ic_mn_shuffle,
            ICON_REPEAT to R.drawable.ic_mn_repeat,
            ICON_REPEAT_ONE to R.drawable.ic_mn_repeat_one,
            ICON_HEART to R.drawable.ic_mn_heart,
            ICON_HEART_BORDER to R.drawable.ic_mn_heart_border,
            ICON_SEARCH to R.drawable.ic_mn_search,
            ICON_SETTINGS to R.drawable.ic_mn_settings,
            ICON_VOLUME to R.drawable.ic_mn_volume,
            ICON_PLAYLIST_ADD to R.drawable.ic_playlist_add,
            ICON_EQUALIZER to R.drawable.ic_equalizer,
            ICON_QUEUE to R.drawable.ic_mn_queue,
            ICON_LYRICS to R.drawable.ic_mn_lyrics,
            ICON_MUSIC_NOTE to R.drawable.ic_mn_music_note,
            ICON_BACK to R.drawable.ic_mn_back
        )
    )

    // ── Bold Pack ──────────────────────────────────────────────────
    // Heavy geometric shapes, solid fills, extra thick strokes

    private fun boldPack() = IconPack(
        id = "bold",
        displayName = "Bold",
        icons = mapOf(
            ICON_PLAY to R.drawable.ic_bd_play,
            ICON_PAUSE to R.drawable.ic_bd_pause,
            ICON_NEXT to R.drawable.ic_bd_next,
            ICON_PREV to R.drawable.ic_bd_prev,
            ICON_SHUFFLE to R.drawable.ic_bd_shuffle,
            ICON_REPEAT to R.drawable.ic_bd_repeat,
            ICON_REPEAT_ONE to R.drawable.ic_bd_repeat_one,
            ICON_HEART to R.drawable.ic_bd_heart,
            ICON_HEART_BORDER to R.drawable.ic_bd_heart_border,
            ICON_SEARCH to R.drawable.ic_bd_search,
            ICON_SETTINGS to R.drawable.ic_bd_settings,
            ICON_VOLUME to R.drawable.ic_bn_volume,
            ICON_PLAYLIST_ADD to R.drawable.ic_playlist_add,
            ICON_EQUALIZER to R.drawable.ic_equalizer,
            ICON_QUEUE to R.drawable.ic_bd_queue,
            ICON_LYRICS to R.drawable.ic_bd_lyrics,
            ICON_MUSIC_NOTE to R.drawable.ic_bd_music_note,
            ICON_BACK to R.drawable.ic_bd_back
        )
    )
}