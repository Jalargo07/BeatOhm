package com.beatohm.importer

import android.util.Log
import com.beatohm.extractor.YouTubeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports playlists from YouTube using the existing YouTubeExtractor.
 *
 * Detects YouTube playlist URLs (youtube.com, youtu.be, music.youtube.com),
 * extracts the playlist ID, and uses the Innertube API via YouTubeExtractor
 * to fetch all tracks.
 */
object YouTubeImporter : IPlaylistImporter {
    private const val TAG = "YouTubeImporter"

    /**
     * Check if URL is a YouTube playlist.
     * Supports youtube.com, youtu.be, and music.youtube.com with list= parameter.
     */
    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        val isYouTubeDomain = lower.contains("youtube.com") ||
                lower.contains("youtu.be") ||
                lower.contains("music.youtube.com")
        return isYouTubeDomain && lower.contains("list=")
    }

    /**
     * Extract playlist ID from YouTube URL.
     * Handles:
     * - https://www.youtube.com/playlist?list=PLxxxxx
     * - https://www.youtube.com/watch?v=xxx&list=PLxxxxx
     * - https://youtu.be/xxx?list=PLxxxxx
     * - https://music.youtube.com/playlist?list=PLxxxxx
     */
    fun extractPlaylistId(url: String): String? {
        val regex = Regex("""[?&]list=([\w-]+)""")
        return regex.find(url)?.groupValues?.get(1)
    }

    /**
     * Fetch all tracks from a YouTube playlist.
     * Reconstructs a full playlist URL and delegates to YouTubeExtractor.extractPlaylist().
     * Converts the resulting Song objects to ImportedTrack.
     */
    override suspend fun fetchTracks(playlistId: String): List<ImportedTrack> = withContext(Dispatchers.IO) {
        try {
            val extractor = YouTubeExtractor()
            val playlistUrl = "https://www.youtube.com/playlist?list=$playlistId"

            Log.d(TAG, "Fetching YouTube playlist: $playlistUrl")
            val result = extractor.extractPlaylist(playlistUrl)

            if (result.isFailure) {
                Log.e(TAG, "YouTube extractPlaylist failed: ${result.exceptionOrNull()?.message}")
                return@withContext emptyList()
            }

            val songs = result.getOrNull() ?: emptyList()
            val tracks = songs.map { song ->
                // For YouTube imports, DON'T use the channel name as artist.
                // The channel is usually a fan channel or record label, not the actual artist.
                // Let the metadata algorithm find the correct artist from the song title.
                ImportedTrack(
                    title = song.title,
                    artist = "",  // Empty — let metadata algorithm find the real artist
                    album = song.album,
                    durationSec = song.duration.toInt()
                )
            }

            Log.d(TAG, "Fetched ${tracks.size} tracks from YouTube playlist: $playlistId")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching YouTube playlist: ${e.message}")
            emptyList()
        }
    }
}
