package com.beatohm.metadata

import android.util.Log
import com.beatohm.network.NetworkModule
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

/**
 * Provider de metadata de la Deezer API (sin key).
 *
 * - Endpoint: `https://api.deezer.com/search?q=...&limit=3`
 * - Key: no requiere (API pública, con rate limits ~20 req/min).
 * - User-Agent: `BeatOhm/1.0`.
 * - Campos que trae: title, artist.name, album.title, release_date (YYYY-MM-DD → año
 *   via [extractYear]), cover_big (carátula 500x500). No trae género en search → "".
 * - Limpieza: reutiliza [cleanChannelName]/[cleanTitle] para armar la query. Los
 *   campos del candidato se usan tal cual vienen de la API (ya son datos de un catálogo
 *   musical, no de YouTube).
 */
object DeezerProvider : MetadataProvider {

    override val source = MetadataSource.DEEZER

    private val client = NetworkModule.client

    override suspend fun search(artist: String, title: String, extraTags: ExtraTags): List<MetadataCandidate> =
        withContext(Dispatchers.IO) {
            try {
                val cleanArtist = cleanChannelName(artist)
                val cleanTitle = cleanTitle(title)
                // Sin artista pero con album extra (Fase 2): el album se suma al term
                // para acotar la búsqueda y evitar falsos positivos por solo título.
                val rawQuery = when {
                    cleanArtist.isNotBlank() && cleanTitle.isNotBlank() -> "$cleanArtist $cleanTitle"
                    cleanArtist.isNotBlank() -> cleanArtist
                    extraTags.album.isNotBlank() && cleanTitle.isNotBlank() -> "$cleanTitle ${extraTags.album}"
                    else -> cleanTitle
                }
                if (rawQuery.isBlank()) return@withContext emptyList()
                val query = URLEncoder.encode(rawQuery, "UTF-8")
                Log.d(TAG, "search: query='$query'")

                val url = "https://api.deezer.com/search?q=$query&limit=3"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "BeatOhm/1.0")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val body = response.use { it.body?.string() } ?: return@withContext emptyList()
                val json = JsonParser.parseString(body).asJsonObject
                val items = json.getAsJsonArray("data") ?: return@withContext emptyList()

                items.mapNotNull { element ->
                    if (!element.isJsonObject) return@mapNotNull null
                    val item = element.asJsonObject
                    val titleValue = jsonString(item, "title")?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val artistObj = item.get("artist")?.takeIf { it.isJsonObject }?.asJsonObject
                    val albumObj = item.get("album")?.takeIf { it.isJsonObject }?.asJsonObject
                    MetadataCandidate(
                        title = titleValue,
                        artist = artistObj?.let { jsonString(it, "name") } ?: "",
                        album = albumObj?.let { jsonString(it, "title") } ?: "",
                        year = extractYear(jsonString(item, "release_date") ?: ""),
                        genre = "",
                        artworkUrl = jsonString(item, "cover_big") ?: "",
                        duration = item.get("duration")?.let {
                            if (it.isJsonPrimitive) it.asLong * 1000 else 0L
                        } ?: 0L,
                        source = source
                    )
                }.take(3)
            } catch (e: Exception) {
                Log.e(TAG, "search ERROR: ${e.message}", e)
                emptyList()
            }
        }

    private const val TAG = "DeezerProvider"
}
