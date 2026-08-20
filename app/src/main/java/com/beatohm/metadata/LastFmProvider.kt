package com.beatohm.metadata

import android.util.Log
import com.beatohm.network.NetworkModule
import com.beatohm.util.ApiKeyProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

/**
 * Provider de metadata de Last.fm `track.search` (requiere API key).
 *
 * - Endpoint: `https://ws.audioscrobbler.com/2.0/?method=track.search&track=...&artist=...&api_key=...&format=json&limit=3`
 * - Key: via [ApiKeyProvider.lastFmKey] (secrets.properties → build.gradle.kts).
 *   Si está vacía → `search()` retorna lista vacía silenciosamente.
 * - Campos que trae: name (título), artist (string), album (puede venir vacío),
 *   image[] (carátula, se elige el mayor tamaño disponible: extralarge > large >
 *   medium > small). year/genre NO vienen de track.search → "".
 * - Limpieza: reutiliza [cleanChannelName]/[cleanTitle] para armar la query.
 */
object LastFmProvider : MetadataProvider {

    override val source = MetadataSource.LASTFM

    private val client = NetworkModule.client

    override suspend fun search(artist: String, title: String, extraTags: ExtraTags): List<MetadataCandidate> =
        withContext(Dispatchers.IO) {
            val apiKey = ApiKeyProvider.lastFmKey()
            if (apiKey == null) {
                Log.d(TAG, "search SKIP: LASTFM_API_KEY not configured")
                return@withContext emptyList()
            }
            try {
                val cleanArtist = cleanChannelName(artist)
                val cleanTitle = cleanTitle(title)
                if (cleanArtist.isBlank() && cleanTitle.isBlank()) return@withContext emptyList()
                Log.d(TAG, "search: artist=${cleanArtist.length}ch, title=${cleanTitle.length}ch")

                // Sin artista pero con album extra (Fase 2): el album se agrega al term
                // de `track` (Last.fm track.search no tiene filtro de album) para
                // acotar la búsqueda y evitar falsos positivos por solo título.
                val searchTrack = if (cleanArtist.isBlank() && extraTags.album.isNotBlank()) {
                    "$cleanTitle ${extraTags.album}"
                } else {
                    cleanTitle
                }
                val encodedTrack = URLEncoder.encode(searchTrack, "UTF-8")
                val artistParam = if (cleanArtist.isNotBlank()) {
                    "&artist=${URLEncoder.encode(cleanArtist, "UTF-8")}"
                } else {
                    ""
                }
                val url = "https://ws.audioscrobbler.com/2.0/" +
                    "?method=track.search&track=$encodedTrack$artistParam" +
                    "&api_key=$apiKey&format=json&limit=3"
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.use { it.body?.string() } ?: return@withContext emptyList()
                val json = JsonParser.parseString(body).asJsonObject
                val trackmatches = json.getAsJsonObject("results")
                    ?.getAsJsonObject("trackmatches")
                    ?: return@withContext emptyList()
                val tracks = trackmatches.getAsJsonArray("track") ?: return@withContext emptyList()

                tracks.mapNotNull { element ->
                    if (!element.isJsonObject) return@mapNotNull null
                    val item = element.asJsonObject
                    val trackName = jsonString(item, "name")?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    MetadataCandidate(
                        title = trackName,
                        artist = jsonString(item, "artist") ?: "",
                        album = jsonString(item, "album") ?: "",
                        year = "",
                        genre = "",
                        artworkUrl = largestImageUrl(item),
                        source = source
                    )
                }.take(3)
            } catch (e: Exception) {
                Log.e(TAG, "search ERROR: ${e.message}", e)
                emptyList()
            }
        }

    /**
     * Elige la imagen de mayor tamaño disponible del array `image` de Last.fm
     * (extralarge > large > medium > small). Fallback: cualquier URL no vacía.
     */
    private fun largestImageUrl(item: JsonObject): String {
        val images = item.get("image")?.takeIf { it.isJsonArray }?.asJsonArray ?: return ""
        val priority = listOf("extralarge", "large", "medium", "small")
        for (size in priority) {
            val url = images.firstOrNull { element ->
                if (!element.isJsonObject) return@firstOrNull false
                jsonString(element.asJsonObject, "size") == size
            }?.asJsonObject?.let { jsonString(it, "#text") }
            if (!url.isNullOrBlank()) return url
        }
        return images.firstOrNull { element ->
            if (!element.isJsonObject) return@firstOrNull false
            !jsonString(element.asJsonObject, "#text").isNullOrBlank()
        }?.asJsonObject?.let { jsonString(it, "#text") } ?: ""
    }

    private const val TAG = "LastFmProvider"
}
