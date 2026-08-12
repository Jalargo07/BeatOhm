package com.beatohm.importer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Imports playlists from Spotify using the public embed endpoint.
 * NO authentication needed — 100% free, no API keys required.
 * 
 * The embed page contains a __NEXT_DATA__ JSON blob with all tracks.
 * All tracks are returned in a single page (no pagination needed).
 * 
 * API: https://open.spotify.com/embed/playlist/{id}
 * Response: HTML with embedded JSON containing trackList array
 */
object SpotifyImporter : IPlaylistImporter {
    private const val TAG = "SpotifyImporter"
    private const val EMBED_URL = "https://open.spotify.com/embed/playlist"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    /**
     * Check if URL is a Spotify playlist.
     * Supports: https://open.spotify.com/playlist/{id}, https://spotify.com/playlist/{id}
     */
    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("open.spotify.com/playlist/") || 
               lower.contains("spotify.com/playlist/")
    }
    
    /**
     * Extract playlist ID from Spotify URL.
     * Handles: https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=xxx
     * Returns: 37i9dQZF1DXcBWIGoYBM5M
     */
    fun extractPlaylistId(url: String): String? {
        // Clean URL: remove query params, fragments
        val cleanUrl = url.split("?")[0].split("#")[0].trim()
        
        // Pattern: spotify.com/playlist/{id}
        val regex = Regex("""spotify\.com/playlist/([a-zA-Z0-9]+)""")
        regex.find(cleanUrl)?.let { return it.groupValues[1] }
        
        return null
    }
    
    /**
     * Fetch all tracks from a Spotify playlist using the embed endpoint.
     * Parses the __NEXT_DATA__ JSON blob from the HTML response.
     * 
     * The embed endpoint returns ALL tracks in a single page — no pagination needed.
     */
    override suspend fun fetchTracks(playlistId: String): List<ImportedTrack> = withContext(Dispatchers.IO) {
        val url = "$EMBED_URL/$playlistId"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()
        
        Log.d(TAG, "Fetching Spotify playlist via embed: $playlistId")
        
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Spotify embed error: ${response.code} ${response.message}")
                    return@withContext emptyList()
                }
                
                val html = response.body?.string() ?: ""
                parseTracksFromHtml(html)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Spotify embed: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Parse tracks from the __NEXT_DATA__ JSON embedded in the HTML.
     * 
     * Structure:
     * <script id="__NEXT_DATA__" type="application/json">
     *   {"props":{"pageProps":{"state":{"data":{"entity":{"trackList":[...]}}}}}}
     * </script>
     */
    private fun parseTracksFromHtml(html: String): List<ImportedTrack> {
        val tracks = mutableListOf<ImportedTrack>()
        
        // Find the __NEXT_DATA__ JSON blob
        val nextDataRegex = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""")
        val match = nextDataRegex.find(html) ?: run {
            Log.e(TAG, "Could not find __NEXT_DATA__ in embed page")
            return emptyList()
        }
        
        try {
            val json = JSONObject(match.groupValues[1])
            val trackList = json
                .getJSONObject("props")
                .getJSONObject("pageProps")
                .getJSONObject("state")
                .getJSONObject("data")
                .getJSONObject("entity")
                .optJSONArray("trackList") ?: run {
                Log.e(TAG, "trackList not found in JSON")
                return emptyList()
            }
            
            for (i in 0 until trackList.length()) {
                val track = trackList.getJSONObject(i)
                
                val title = track.optString("title", "")
                if (title.isBlank()) continue
                
                // subtitle contains artist name(s), possibly with "Explicit" tag prefix
                val subtitle = track.optString("subtitle", "")
                    .replace(Regex("""^[E]\s*"""), "")  // Remove "E " explicit tag prefix
                    .trim()
                
                val artist = subtitle.ifBlank { "Unknown" }
                
                // duration is in milliseconds
                val durationMs = track.optLong("duration", 0)
                val durationSec = (durationMs / 1000).toInt()
                
                tracks.add(ImportedTrack(
                    title = title,
                    artist = artist,
                    album = "",  // Not available in embed response
                    durationSec = durationSec
                ))
            }
            
            Log.d(TAG, "Parsed ${tracks.size} tracks from Spotify embed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON: ${e.message}")
        }
        
        return tracks
    }
}
