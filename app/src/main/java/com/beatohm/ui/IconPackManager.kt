package com.beatohm.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.beatohm.R

data class IconPack(
    val id: String,
    val displayName: String,
    val icons: Map<String, Int>,
    val isColorAware: Boolean = false,
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
        allPacks.add(heroicPack())
        allPacks.add(lucidePack())
        allPacks.add(neonPack())
        allPacks.add(glassPack())
        allPacks.add(gradientPack())
        allPacks.add(phosphorPack())
    }

    fun getAllPacks(): List<IconPack> = allPacks.toList()

    /**
     * Get ALL app icon resource IDs for the active icon pack.
     * Returns ~30 entries covering
     * player controls, bottom nav, library categories, downloads, mini player.
     *
     * Note: color-aware packs (isColorAware=true) do NOT use this map.
     * Their drawables are built programmatically via [IconPackDrawableFactory.getDrawable],
     * accessed through [getIcon] which delegates accordingly.
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
            ICON_HEART_BORDER to (pack.getIconResId(ICON_HEART_BORDER) ?: R.drawable.ic_favorite_border),
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
        allPacks.find { it.id == id } ?: lucidePack()

    fun getIcon(name: String, packId: String, context: Context): Drawable? {
        val pack = getPackById(packId)
        if (pack.isColorAware) {
            return IconPackDrawableFactory.getDrawable(
                packId, name, context,
                ThemeManager.accentColor, ThemeManager.secondaryColor
            )
        }
        return pack.getIcon(name, context)
    }

    fun isColorAwarePack(packId: String): Boolean = getPackById(packId).isColorAware

    // ── Heroic Pack ─────────────────────────────────────────────

    private fun heroicPack() = IconPack(
        id = "heroic",
        displayName = "Heroic",
        icons = mapOf(
            ICON_PLAY to R.drawable.ic_heroic_play,
            ICON_PAUSE to R.drawable.ic_heroic_pause,
            ICON_NEXT to R.drawable.ic_heroic_next,
            ICON_PREV to R.drawable.ic_heroic_prev,
            ICON_SHUFFLE to R.drawable.ic_heroic_shuffle,
            ICON_REPEAT to R.drawable.ic_heroic_repeat,
            ICON_REPEAT_ONE to R.drawable.ic_heroic_repeat_one,
            ICON_HEART to R.drawable.ic_heroic_heart,
            ICON_HEART_BORDER to R.drawable.ic_heroic_heart_border,
            ICON_SEARCH to R.drawable.ic_heroic_search,
            ICON_SETTINGS to R.drawable.ic_heroic_settings,
            ICON_VOLUME to R.drawable.ic_heroic_volume,
            ICON_PLAYLIST_ADD to R.drawable.ic_heroic_playlist_add,
            ICON_EQUALIZER to R.drawable.ic_heroic_equalizer,
            ICON_QUEUE to R.drawable.ic_heroic_queue,
            ICON_LYRICS to R.drawable.ic_heroic_lyrics,
            ICON_MUSIC_NOTE to R.drawable.ic_music_note,
            ICON_BACK to R.drawable.ic_heroic_back,
            ICON_PLAYER to R.drawable.ic_heroic_player,
            ICON_LIBRARY to R.drawable.ic_heroic_library,
            ICON_DOWNLOADS to R.drawable.ic_heroic_downloads,
            ICON_MIC to R.drawable.ic_heroic_mic,
            ICON_GENRES to R.drawable.ic_heroic_genres,
            ICON_ALBUM to R.drawable.ic_heroic_album,
            ICON_PLAYLIST to R.drawable.ic_heroic_playlist,
            ICON_TRENDING to R.drawable.ic_heroic_trending,
            ICON_FOLDER to R.drawable.ic_heroic_folder,
        )
    )

    // ── Lucide Pack ─────────────────────────────────────────────
    // Fine rounded line icons (MIT, lucide.dev) — tint normal

    private fun lucidePack() = IconPack(
        id = "lucide",
        displayName = "Lucide",
        icons = mapOf(
            ICON_PLAY to R.drawable.ic_lucide_play,
            ICON_PAUSE to R.drawable.ic_lucide_pause,
            ICON_NEXT to R.drawable.ic_lucide_next,
            ICON_PREV to R.drawable.ic_lucide_prev,
            ICON_SHUFFLE to R.drawable.ic_lucide_shuffle,
            ICON_REPEAT to R.drawable.ic_lucide_repeat,
            ICON_REPEAT_ONE to R.drawable.ic_lucide_repeat_one,
            ICON_HEART to R.drawable.ic_lucide_heart,
            ICON_HEART_BORDER to R.drawable.ic_lucide_heart_border,
            ICON_VOLUME to R.drawable.ic_lucide_volume,
            ICON_EQUALIZER to R.drawable.ic_lucide_equalizer,
            ICON_QUEUE to R.drawable.ic_lucide_queue,
            ICON_LYRICS to R.drawable.ic_lucide_lyrics,
            ICON_SEARCH to R.drawable.ic_lucide_search,
            ICON_SETTINGS to R.drawable.ic_lucide_settings,
            ICON_PLAYLIST_ADD to R.drawable.ic_lucide_playlist_add,
            ICON_BACK to R.drawable.ic_lucide_back,
            ICON_MUSIC_NOTE to R.drawable.ic_lucide_music_note,
            ICON_PLAYER to R.drawable.ic_lucide_player,
            ICON_LIBRARY to R.drawable.ic_lucide_library,
            ICON_DOWNLOADS to R.drawable.ic_lucide_downloads,
            ICON_MIC to R.drawable.ic_lucide_mic,
            ICON_GENRES to R.drawable.ic_lucide_genres,
            ICON_ALBUM to R.drawable.ic_lucide_album,
            ICON_PLAYLIST to R.drawable.ic_lucide_playlist,
            ICON_TRENDING to R.drawable.ic_lucide_trending,
            ICON_FOLDER to R.drawable.ic_lucide_folder,
        )
    )

    // ── Neon Pack (color-aware) ──────────────────────────────────
    private fun neonPack() = IconPack(
        id = "neon",
        displayName = "Neón Glow 2.0",
        icons = emptyMap(),
        isColorAware = true,
    )

    // ── Glass Pack (color-aware) ─────────────────────────────────
    private fun glassPack() = IconPack(
        id = "glass",
        displayName = "Glassmorphism",
        icons = emptyMap(),
        isColorAware = true,
    )

    // ── Gradient Pack (color-aware) ──────────────────────────────
    private fun gradientPack() = IconPack(
        id = "gradient",
        displayName = "Gradient Bold",
        icons = emptyMap(),
        isColorAware = true,
    )

    // ── Phosphor Pack (color-aware) ──────────────────────────────
    private fun phosphorPack() = IconPack(
        id = "phosphor",
        displayName = "Phosphor Duotone",
        icons = emptyMap(),
        isColorAware = true,
    )

}
