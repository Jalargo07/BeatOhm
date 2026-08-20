package com.beatohm.metadata

import android.util.Log
import com.beatohm.network.NetworkModule
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

/**
 * Provider de metadata de la MusicBrainz Recording API (sin key).
 *
 * - Endpoint: `https://musicbrainz.org/ws/2/recording/?query=...&fmt=json&limit=3`
 * - Key: no requiere, pero exige User-Agent válido con email de contacto:
 *   `BeatOhm/1.0 (alejo@email.com)`.
 * - Campos que trae: title, artist-credit[0].name (artista), releases[0].title (album),
 *   releases[0].date (año via [extractYear]), releases[0].tags (género).
 * - NO trae artwork: `artworkUrl` queda "" (mismo comportamiento que el antiguo
 *   `MetadataFetcher.searchMusicBrainz()`). Si no hay releases, album/year/genre quedan "".
 * - Limpieza: reutiliza [cleanChannelName]/[cleanTitle] para armar la query. Los campos
 *   del candidato se usan tal cual vienen de la API (catálogo musical, no YouTube).
 */
object MusicBrainzProvider : MetadataProvider {

    override val source = MetadataSource.MUSICBRAINZ

    private val client = NetworkModule.client

    override suspend fun search(artist: String, title: String, extraTags: ExtraTags): List<MetadataCandidate> =
        withContext(Dispatchers.IO) {
            try {
                val cleanArtist = cleanChannelName(artist)
                val cleanTitle = cleanTitle(title)
                Log.d(TAG, "search: artist='$artist' → '$cleanArtist', title='$title' → '$cleanTitle'")
                if (cleanTitle.isBlank()) return@withContext emptyList()
                // Sin artista pero con album extra (Fase 2): el album entra al query
                // Lucene (`release:`) para acotar la búsqueda por solo título.
                val rawQuery = when {
                    cleanArtist.isNotBlank() -> "artist:\"$cleanArtist\" AND recording:\"$cleanTitle\""
                    extraTags.album.isNotBlank() -> "release:\"${extraTags.album}\" AND recording:\"$cleanTitle\""
                    else -> "recording:\"$cleanTitle\""
                }
                val query = URLEncoder.encode(rawQuery, "UTF-8")
                Log.d(TAG, "search query: '$query'")
                val url = "https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=3"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "BeatOhm/1.0 (alejo@email.com)")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val body = response.use { it.body?.string() } ?: return@withContext emptyList()
                val json = JsonParser.parseString(body).asJsonObject
                val recordings = json.getAsJsonArray("recordings") ?: return@withContext emptyList()

                recordings.mapNotNull { element ->
                    if (!element.isJsonObject) return@mapNotNull null
                    val rec = element.asJsonObject
                    val recTitle = jsonString(rec, "title")?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val artistCredit = rec.get("artist-credit")?.takeIf { it.isJsonArray }?.asJsonArray
                    val recArtist = artistCredit?.firstOrNull()
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?.let { jsonString(it, "name") } ?: ""
                    val releases = rec.get("releases")?.takeIf { it.isJsonArray }?.asJsonArray
                    var album = ""
                    var year = ""
                    var genre = ""
                    if (releases != null && releases.size() > 0) {
                        val release = releases.first().asJsonObject
                        album = jsonString(release, "title") ?: ""
                        year = extractYear(jsonString(release, "date") ?: "")
                        val tags = release.get("tags")?.takeIf { it.isJsonArray }?.asJsonArray
                        if (tags != null && tags.size() > 0) {
                            genre = tags.firstOrNull { it.isJsonObject }?.asJsonObject
                                ?.let { jsonString(it, "name") }
                                ?.replaceFirstChar { it.uppercase() } ?: ""
                        }
                    }
                    MetadataCandidate(
                        title = recTitle,
                        artist = recArtist,
                        album = album,
                        year = year,
                        genre = genre,
                        artworkUrl = "",
                        source = source
                    )
                }.take(3)
            } catch (e: Exception) {
                Log.e(TAG, "search ERROR: ${e.message}", e)
                emptyList()
            }
        }

    private const val TAG = "MusicBrainzProvider"
}
