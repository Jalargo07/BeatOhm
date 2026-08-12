package com.beatohm.importer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Imports playlists from Deezer API (free, no auth required).
 * Handles pagination for playlists with >100 tracks.
 *
 * API: https://api.deezer.com/playlist/{id}
 * Max tracks per request: 100 (via limit parameter)
 */
object DeezerImporter : IPlaylistImporter {
    private const val TAG = "DeezerImporter"
    private const val BASE_URL = "https://api.deezer.com/playlist"
    private const val MAX_PER_PAGE = 100

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Check if URL is a Deezer playlist.
     * Supports: https://deezer.com/playlist/{id}, https://www.deezer.com/playlist/{id},
     *           deezer://playlist/{id}, https://dzr.page/{id}
     */
    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("deezer.com/playlist/") ||
               lower.contains("dzr.page/") ||
               lower.startsWith("deezer://playlist/")
    }

    /**
     * Extract playlist ID from various Deezer URL formats.
     */
    fun extractPlaylistId(url: String): String? {
        // Clean URL: remove query params, fragments
        val cleanUrl = url.split("?")[0].split("#")[0].trim()

        // Pattern: deezer.com/playlist/{id} or www.deezer.com/playlist/{id}
        val regex = Regex("""deezer\.com/playlist/(\d+)""")
        regex.find(cleanUrl)?.let { return it.groupValues[1] }

        // Pattern: dzr.page/{id}
        val shortRegex = Regex("""dzr\.page/(\d+)""")
        shortRegex.find(cleanUrl)?.let { return it.groupValues[1] }

        // Pattern: deezer://playlist/{id}
        val deezerRegex = Regex("""deezer://playlist/(\d+)""")
        deezerRegex.find(cleanUrl)?.let { return it.groupValues[1] }

        return null
    }

    /**
     * Fetch all tracks from a Deezer playlist.
     * Handles pagination automatically.
     */
    override suspend fun fetchTracks(playlistId: String): List<ImportedTrack> = withContext(Dispatchers.IO) {
        val allTracks = mutableListOf<ImportedTrack>()
        var index = 0
        var hasMore = true

        Log.d(TAG, "Fetching Deezer playlist: $playlistId")

        while (hasMore) {
            val url = "$BASE_URL/$playlistId/tracks?limit=$MAX_PER_PAGE&index=$index"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BeatOhm/1.0 (Android)")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Deezer API error: ${response.code} ${response.message}")
                        hasMore = false
                        return@use
                    }

                    val json = JSONObject(response.body?.string() ?: "")
                    val dataArray = json.optJSONArray("data") ?: run {
                        hasMore = false
                        return@use
                    }

                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.getJSONObject(i)

                        // Skip null tracks (can happen with region-locked content)
                        if (item.isNull("title")) continue

                        val title = item.optString("title", "")
                        if (title.isBlank()) continue

                        val artist = item.optJSONObject("artist")?.optString("name", "Unknown") ?: "Unknown"
                        val album = item.optJSONObject("album")?.optString("title", "Single") ?: "Single"
                        val duration = item.optInt("duration", 0)

                        allTracks.add(ImportedTrack(
                            title = title,
                            artist = artist,
                            album = album,
                            durationSec = duration
                        ))
                    }

                    // Check if there are more pages
                    val nextUrl = json.optString("next", "")
                    hasMore = nextUrl.isNotBlank() && dataArray.length() == MAX_PER_PAGE
                    index += dataArray.length()

                    Log.d(TAG, "Fetched page: ${dataArray.length()} tracks (total: ${allTracks.size})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Deezer playlist: ${e.message}")
                hasMore = false
            }
        }

        Log.d(TAG, "Total tracks fetched from Deezer: ${allTracks.size}")
        allTracks
    }
}
