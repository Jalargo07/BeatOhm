package com.musicdownloader.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.musicdownloader.R

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

    // ── Player / Bottom Nav ───────────────────────────────────────
    const val ICON_PLAY = "play"
    const val ICON_PAUSE = "pause"
    const val ICON_NEXT = "next"
    const val ICON_PREV = "prev"
    const val ICON_SHUFFLE = "shuffle"
    const val ICON_REPEAT = "repeat"
    const val ICON_REPEAT_ONE = "repeat_one"
    const val ICON_HEART = "heart"
    const val ICON_HEART_BORDER = "heart_border"
    const val ICON_VOLUME = "volume"
    const val ICON_EQUALIZER = "equalizer"
    const val ICON_QUEUE = "queue"
    const val ICON_LYRICS = "lyrics"

    // ── Bottom Nav ────────────────────────────────────────────────
    const val ICON_PLAYER = "player"
    const val ICON_LIBRARY = "library"
    const val ICON_DOWNLOADS = "downloads"

    // ── Library Categories ────────────────────────────────────────
    const val ICON_MUSIC_NOTE = "music_note"
    const val ICON_MIC = "mic"
    const val ICON_GENRES = "genres"
    const val ICON_ALBUM = "album"
    const val ICON_PLAYLIST = "playlist"
    const val ICON_TRENDING = "trending"
    const val ICON_FOLDER = "folder"

    // ── Misc ──────────────────────────────────────────────────────
    const val ICON_SEARCH = "search"
    const val ICON_SETTINGS = "settings"
    const val ICON_PLAYLIST_ADD = "playlist_add"
    const val ICON_BACK = "back"

    private val allPacks = mutableListOf<IconPack>()

    init {
        allPacks.add(defaultPack())
        allPacks.add(outlinePack())
        allPacks.add(filledPack())
        allPacks.add(boowopPack())
        allPacks.add(darknovaPack())
        allPacks.add(mononokiPack())
        allPacks.add(mainstagePack())
    }

    fun getAllPacks(): List<IconPack> = allPacks.toList()

    /**
     * Get ALL app icon resource IDs for the active icon pack.
     * Replaces getPlayerIconResIds — returns ~30 entries covering
     * player controls, bottom nav, library categories, downloads, mini player.
     */
    fun getAppIconResIds(packId: String): Map<String, Int> {
        val pack = getPackById(packId)
        return mapOf(
            // Player controls
            ICON_PLAY to (pack.getIconResId(ICON_PLAY) ?: R.drawable.ic_play),
            ICON_PAUSE to (pack.getIconResId(ICON_PAUSE) ?: R.drawable.ic_pause),
            ICON_NEXT to (pack.getIconResId(ICON_NEXT) ?: R.drawable.ic_next),
            ICON_PREV to (pack.getIconResId(ICON_PREV) ?: R.drawable.ic_prev),
            ICON_SHUFFLE to (pack.getIconResId(ICON_SHUFFLE) ?: R.drawable.ic_shuffle),
            ICON_REPEAT to (pack.getIconResId(ICON_REPEAT) ?: R.drawable.ic_repeat),
            ICON_REPEAT_ONE to (pack.getIconResId(ICON_REPEAT_ONE) ?: R.drawable.ic_repeat_one),
            ICON_HEART to (pack.getIconResId(ICON_HEART) ?: R.drawable.ic_favorite),
            ICON_HEART_BORDER to (pack.getIconResId(ICON_HEART_BORDER) ?: R.drawable.ic_bookmark_border),
            ICON_VOLUME to (pack.getIconResId(ICON_VOLUME) ?: R.drawable.ic_volume),
            ICON_EQUALIZER to (pack.getIconResId(ICON_EQUALIZER) ?: R.drawable.ic_equalizer),
            ICON_QUEUE to (pack.getIconResId(ICON_QUEUE) ?: R.drawable.ic_queue_music),
            ICON_LYRICS to (pack.getIconResId(ICON_LYRICS) ?: R.drawable.ic_lyrics),
            ICON_SEARCH to (pack.getIconResId(ICON_SEARCH) ?: R.drawable.ic_search),
            ICON_SETTINGS to (pack.getIconResId(ICON_SETTINGS) ?: R.drawable.ic_settings),
            ICON_PLAYLIST_ADD to (pack.getIconResId(ICON_PLAYLIST_ADD) ?: R.drawable.ic_playlist_add),
            ICON_BACK to (pack.getIconResId(ICON_BACK) ?: R.drawable.ic_back),
            // Bottom nav
            ICON_PLAYER to (pack.getIconResId(ICON_PLAYER) ?: R.drawable.ic_player),
            ICON_LIBRARY to (pack.getIconResId(ICON_LIBRARY) ?: R.drawable.ic_library),
            ICON_DOWNLOADS to (pack.getIconResId(ICON_DOWNLOADS) ?: R.drawable.ic_downloads),
            // Library categories
            ICON_MUSIC_NOTE to (pack.getIconResId(ICON_MUSIC_NOTE) ?: R.drawable.ic_music_note),
            ICON_MIC to (pack.getIconResId(ICON_MIC) ?: R.drawable.ic_mic),
            ICON_GENRES to (pack.getIconResId(ICON_GENRES) ?: R.drawable.ic_genres),
            ICON_ALBUM to (pack.getIconResId(ICON_ALBUM) ?: R.drawable.ic_album),
            ICON_PLAYLIST to (pack.getIconResId(ICON_PLAYLIST) ?: R.drawable.ic_playlist),
            ICON_TRENDING to (pack.getIconResId(ICON_TRENDING) ?: R.drawable.ic_trending_up),
            ICON_FOLDER to (pack.getIconResId(ICON_FOLDER) ?: R.drawable.ic_folder),
        )
    }

    /**
     * @deprecated Use [getAppIconResIds] instead.
     */
    @Deprecated("Use getAppIconResIds", ReplaceWith("getAppIconResIds(packId)"))
    fun getPlayerIconResIds(packId: String): Map<String, Int> = getAppIconResIds(packId)

    fun getBottomNavIconResIds(packId: String): Map<String, Int> {
        val all = getAppIconResIds(packId)
        return mapOf(
            ICON_PLAYER to (all[ICON_PLAYER] ?: R.drawable.ic_player),
            ICON_LIBRARY to (all[ICON_LIBRARY] ?: R.drawable.ic_library),
            ICON_DOWNLOADS to (all[ICON_DOWNLOADS] ?: R.drawable.ic_downloads),
        )
    }

    fun getLibraryCategoryIconResIds(packId: String): Map<String, Int> {
        val all = getAppIconResIds(packId)
        return mapOf(
            ICON_MUSIC_NOTE to (all[ICON_MUSIC_NOTE] ?: R.drawable.ic_music_note),
            ICON_MIC to (all[ICON_MIC] ?: R.drawable.ic_mic),
            ICON_GENRES to (all[ICON_GENRES] ?: R.drawable.ic_genres),
            ICON_ALBUM to (all[ICON_ALBUM] ?: R.drawable.ic_album),
            ICON_PLAYLIST to (all[ICON_PLAYLIST] ?: R.drawable.ic_playlist),
            ICON_HEART to (all[ICON_HEART] ?: R.drawable.ic_favorite),
            ICON_TRENDING to (all[ICON_TRENDING] ?: R.drawable.ic_trending_up),
            ICON_FOLDER to (all[ICON_FOLDER] ?: R.drawable.ic_folder),
        )
    }

    fun getDownloadIconResId(packId: String): Int {
        val all = getAppIconResIds(packId)
        return all[ICON_MUSIC_NOTE] ?: R.drawable.ic_music_note
    }

    fun getMiniPlayerIconResIds(packId: String): Map<String, Int> {
        val all = getAppIconResIds(packId)
        return mapOf(
            ICON_PLAY to (all[ICON_PLAY] ?: R.drawable.ic_play),
            ICON_PAUSE to (all[ICON_PAUSE] ?: R.drawable.ic_pause),
        )
    }

    fun getPackById(id: String): IconPack =
        allPacks.find { it.id == id } ?: defaultPack()

    fun getIcon(name: String, packId: String, context: Context): Drawable? {
        val pack = getPackById(packId)
        return pack.getIcon(name, context)
    }

    // ── Default Pack (Material) ──────────────────────────────────

    private fun defaultPack() = IconPack(
        id = "default",
        displayName = "Material",
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
            ICON_BACK to R.drawable.ic_back,
            ICON_PLAYER to R.drawable.ic_player,
            ICON_LIBRARY to R.drawable.ic_library,
            ICON_DOWNLOADS to R.drawable.ic_downloads,
            ICON_MIC to R.drawable.ic_mic,
            ICON_GENRES to R.drawable.ic_genres,
            ICON_ALBUM to R.drawable.ic_album,
            ICON_PLAYLIST to R.drawable.ic_playlist,
            ICON_TRENDING to R.drawable.ic_trending_up,
            ICON_FOLDER to R.drawable.ic_folder,
        )
    )

    // ── Outline Pack ─────────────────────────────────────────────

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
            ICON_BACK to R.drawable.ic_back,
            ICON_PLAYER to R.drawable.ic_player,
            ICON_LIBRARY to R.drawable.ic_library,
            ICON_DOWNLOADS to R.drawable.ic_downloads,
            ICON_MIC to R.drawable.ic_mic,
            ICON_GENRES to R.drawable.ic_genres,
            ICON_ALBUM to R.drawable.ic_album,
            ICON_PLAYLIST to R.drawable.ic_playlist,
            ICON_TRENDING to R.drawable.ic_trending_up,
            ICON_FOLDER to R.drawable.ic_folder,
        )
    )

    // ── Filled Pack ──────────────────────────────────────────────

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
            ICON_BACK to R.drawable.ic_back,
            ICON_PLAYER to R.drawable.ic_player,
            ICON_LIBRARY to R.drawable.ic_library,
            ICON_DOWNLOADS to R.drawable.ic_downloads,
            ICON_MIC to R.drawable.ic_mic,
            ICON_GENRES to R.drawable.ic_genres,
            ICON_ALBUM to R.drawable.ic_album,
            ICON_PLAYLIST to R.drawable.ic_playlist,
            ICON_TRENDING to R.drawable.ic_trending_up,
            ICON_FOLDER to R.drawable.ic_folder,
        )
    )

    // ── BooWop Pack ──────────────────────────────────────────────
    // Soft, rounded filled shapes — kawaii aesthetic

    private fun boowopPack() = IconPack(
        id = "boowop",
        displayName = "BooWop",
        icons = mapOf(
            ICON_PLAY to R.drawable.ic_bn_play,
            ICON_PAUSE to R.drawable.ic_bn_pause,
            ICON_NEXT to R.drawable.ic_bn_next,
            ICON_PREV to R.drawable.ic_bn_prev,
            ICON_SHUFFLE to R.drawable.ic_bn_shuffle,
            ICON_REPEAT to R.drawable.ic_bn_repeat,
            ICON_REPEAT_ONE to R.drawable.ic_bn_repeat_one,
            ICON_HEART to R.drawable.ic_bn_heart,
            ICON_HEART_BORDER to R.drawable.ic_bn_heart_border,
            ICON_SEARCH to R.drawable.ic_bn_search,
            ICON_SETTINGS to R.drawable.ic_bn_settings,
            ICON_VOLUME to R.drawable.ic_bn_volume,
            ICON_PLAYLIST_ADD to R.drawable.ic_bn_playlist_add,
            ICON_EQUALIZER to R.drawable.ic_bn_equalizer,
            ICON_QUEUE to R.drawable.ic_bn_queue,
            ICON_LYRICS to R.drawable.ic_bn_lyrics,
            ICON_MUSIC_NOTE to R.drawable.ic_bn_music_note,
            ICON_BACK to R.drawable.ic_bn_back,
            ICON_PLAYER to R.drawable.ic_player,
            ICON_LIBRARY to R.drawable.ic_library,
            ICON_DOWNLOADS to R.drawable.ic_downloads,
            ICON_MIC to R.drawable.ic_mic,
            ICON_GENRES to R.drawable.ic_genres,
            ICON_ALBUM to R.drawable.ic_album,
            ICON_PLAYLIST to R.drawable.ic_playlist,
            ICON_TRENDING to R.drawable.ic_trending_up,
            ICON_FOLDER to R.drawable.ic_folder,
        )
    )

    // ── DarkNova Pack ────────────────────────────────────────────
    // Heavy geometric shapes, solid fills, extra thick strokes

    private fun darknovaPack() = IconPack(
        id = "darknova",
        displayName = "DarkNova",
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
            ICON_VOLUME to R.drawable.ic_volume,
            ICON_PLAYLIST_ADD to R.drawable.ic_playlist_add,
            ICON_EQUALIZER to R.drawable.ic_equalizer,
            ICON_QUEUE to R.drawable.ic_bd_queue,
            ICON_LYRICS to R.drawable.ic_bd_lyrics,
            ICON_MUSIC_NOTE to R.drawable.ic_bd_music_note,
            ICON_BACK to R.drawable.ic_bd_back,
            ICON_PLAYER to R.drawable.ic_player,
            ICON_LIBRARY to R.drawable.ic_library,
            ICON_DOWNLOADS to R.drawable.ic_downloads,
            ICON_MIC to R.drawable.ic_mic,
            ICON_GENRES to R.drawable.ic_genres,
            ICON_ALBUM to R.drawable.ic_album,
            ICON_PLAYLIST to R.drawable.ic_playlist,
            ICON_TRENDING to R.drawable.ic_trending_up,
            ICON_FOLDER to R.drawable.ic_folder,
        )
    )

    // ── Mononoki Pack ────────────────────────────────────────────
    // Glowing outlines + neon fill

    private fun mononokiPack() = IconPack(
        id = "mononoki",
        displayName = "Mononoki",
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
            ICON_BACK to R.drawable.ic_nn_back,
            ICON_PLAYER to R.drawable.ic_player,
            ICON_LIBRARY to R.drawable.ic_library,
            ICON_DOWNLOADS to R.drawable.ic_downloads,
            ICON_MIC to R.drawable.ic_mic,
            ICON_GENRES to R.drawable.ic_genres,
            ICON_ALBUM to R.drawable.ic_album,
            ICON_PLAYLIST to R.drawable.ic_playlist,
            ICON_TRENDING to R.drawable.ic_trending_up,
            ICON_FOLDER to R.drawable.ic_folder,
        )
    )

    // ── Mainstage Pack ───────────────────────────────────────────
    // Ultra-thin geometric lines

    private fun mainstagePack() = IconPack(
        id = "mainstage",
        displayName = "Mainstage",
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
            ICON_EQUALIZER to R.drawable.ic_mn_equalizer,
            ICON_QUEUE to R.drawable.ic_mn_queue,
            ICON_LYRICS to R.drawable.ic_mn_lyrics,
            ICON_MUSIC_NOTE to R.drawable.ic_mn_music_note,
            ICON_BACK to R.drawable.ic_mn_back,
            ICON_PLAYER to R.drawable.ic_player,
            ICON_LIBRARY to R.drawable.ic_library,
            ICON_DOWNLOADS to R.drawable.ic_downloads,
            ICON_MIC to R.drawable.ic_mic,
            ICON_GENRES to R.drawable.ic_genres,
            ICON_ALBUM to R.drawable.ic_album,
            ICON_PLAYLIST to R.drawable.ic_playlist,
            ICON_TRENDING to R.drawable.ic_trending_up,
            ICON_FOLDER to R.drawable.ic_folder,
        )
    )
}
