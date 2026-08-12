package com.beatohm.importer

/**
 * Detects the type of URL pasted by the user.
 */
object UrlDetector {

    enum class Type {
        YOUTUBE_SONG,
        YOUTUBE_PLAYLIST,
        DEEZER_PLAYLIST,
        SPOTIFY_PLAYLIST,
        UNKNOWN
    }

    data class Result(
        val type: Type,
        val url: String
    )

    fun detect(url: String): Result {
        val normalized = url.trim()

        // Spotify playlist
        if (SpotifyImporter.canHandle(normalized)) {
            return Result(Type.SPOTIFY_PLAYLIST, normalized)
        }

        // Deezer playlist
        if (DeezerImporter.canHandle(normalized)) {
            return Result(Type.DEEZER_PLAYLIST, normalized)
        }

        // YouTube playlist (must check before single song)
        if (isYouTubePlaylist(normalized)) {
            return Result(Type.YOUTUBE_PLAYLIST, normalized)
        }

        // YouTube single song
        if (isYouTubeSong(normalized)) {
            return Result(Type.YOUTUBE_SONG, normalized)
        }

        return Result(Type.UNKNOWN, normalized)
    }

    private fun isYouTubePlaylist(url: String): Boolean {
        return url.matches(Regex("(https?://)?(www\\.|m\\.)?(youtube\\.com|youtu\\.be|music\\.youtube\\.com)/.*[?&]list=[\\w-]+.*")) &&
            url.contains("list=")
    }

    private fun isYouTubeSong(url: String): Boolean {
        return url.matches(Regex("(https?://)?(www\\.|m\\.)?(youtube\\.com|youtu\\.be|music\\.youtube\\.com)/.*"))
    }
}
