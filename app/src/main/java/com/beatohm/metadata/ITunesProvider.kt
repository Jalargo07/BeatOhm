package com.beatohm.metadata

import android.util.Log
import com.beatohm.network.NetworkModule
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

/**
 * Provider de metadata de la iTunes Search API (sin key).
 *
 * - Endpoint: `https://itunes.apple.com/search?term=...&entity=song&limit=3`
 * - Key: no requiere.
 * - Campos que trae: trackName (título), artistName (artista), collectionName (album),
 *   primaryGenreName (género), releaseDate (fecha → año via [extractYear]),
 *   artworkUrl100 (carátula escalada a 600x600).
 * - Limpieza: reutiliza [cleanChannelName]/[cleanTitle] para armar la query y
 *   [cleanTitle]/[cleanArtist] sobre los campos del candidato (mismo comportamiento
 *   que el antiguo `MetadataFetcher.searchItunes()`).
 */
object ITunesProvider : MetadataProvider {

    override val source = MetadataSource.ITUNES

    private val client = NetworkModule.client

    override suspend fun search(artist: String, title: String, extraTags: ExtraTags): List<MetadataCandidate> =
        withContext(Dispatchers.IO) {
            try {
                val cleanArtist = cleanChannelName(artist)
                val cleanTitle = cleanTitle(title)
                Log.d(TAG, "search: artist=${cleanArtist.length}ch, title=${cleanTitle.length}ch")
                if (cleanArtist.isBlank() && cleanTitle.isBlank()) return@withContext emptyList()
                // Sin artista pero con album extra (Fase 2): el album se suma al term
                // para acotar la búsqueda y evitar falsos positivos por solo título.
                val query = when {
                    cleanArtist.isNotBlank() -> URLEncoder.encode("$cleanArtist $cleanTitle", "UTF-8")
                    extraTags.album.isNotBlank() -> URLEncoder.encode("$cleanTitle ${extraTags.album}", "UTF-8")
                    else -> URLEncoder.encode(cleanTitle, "UTF-8")
                }
                Log.d(TAG, "search query: ${query.length}ch")
                val url = "https://itunes.apple.com/search?term=$query&entity=song&limit=3"
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.use { it.body?.string() } ?: return@withContext emptyList()
                val json = JsonParser.parseString(body).asJsonObject
                val results = json.getAsJsonArray("results") ?: return@withContext emptyList()

                results.mapNotNull { element ->
                    if (!element.isJsonObject) return@mapNotNull null
                    val item = element.asJsonObject
                    val trackName = jsonString(item, "trackName")?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    MetadataCandidate(
                        title = cleanTitle(trackName),
                        artist = cleanArtist(jsonString(item, "artistName") ?: ""),
                        album = jsonString(item, "collectionName") ?: "",
                        year = extractYear(jsonString(item, "releaseDate") ?: ""),
                        genre = jsonString(item, "primaryGenreName") ?: "",
                        artworkUrl = jsonString(item, "artworkUrl100")
                            ?.replace("100x100bb", "600x600bb") ?: "",
                        duration = item.get("trackTimeMillis")?.let {
                            if (it.isJsonPrimitive) it.asLong else 0L
                        } ?: 0L,
                        source = source
                    )
                }.take(3)
            } catch (e: Exception) {
                Log.e(TAG, "search ERROR: ${e.message}", e)
                emptyList()
            }
        }

    private const val TAG = "ITunesProvider"
}
