package com.musicdownloader.metadata

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.musicdownloader.model.Song
import com.musicdownloader.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder
import java.text.Normalizer

class MetadataFetcher {

    companion object {
        private const val TAG = "MetadataFetcher"

        private val GENERIC_GENRES = setOf(
            "music",
            "musica",
            "música",
            "audio",
            "unknown",
            "unknow",
            "unknown genre",
            "unbekannt",
            "none",
            "other",
            "various",
            "miscellaneous",
            "misc",
            "general",
            "default",
            "not specified",
            "not applicable",
            "n/a",
            "na",
            "unclassified",
            "unclassifiable",
            "track",
            "tracks"
        )

        /**
         * Elimina géneros genéricos/placeholder ("Music", "Unknown", "Other", ...).
         * Devuelve "" si el género no aporta información real.
         */
        fun sanitizeGenre(genre: String): String {
            val normalized = Normalizer.normalize(genre.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")
                .replace(Regex("\\s+"), " ")
            if (normalized.isEmpty() || normalized in GENERIC_GENRES) return ""
            return genre.trim()
        }
    }

    private val client = NetworkModule.client

    private val gson = Gson()

    /**
     * Limpia el título que viene de iTunes eliminando sufijos de YouTube.
     * Ejemplos:
     *   "Bohemian Rhapsody (Official Video)" → "Bohemian Rhapsody"
     *   "Shape of You [Lyrics]" → "Shape of You"
     *   "Blinding Lights (Remix 2024)" → "Blinding Lights"
     *   "Havana ft. Young Thug" → "Havana" (ft. se maneja aparte en el artista)
     */
    fun cleanTitle(title: String): String {
        var clean = title
        // Remover paréntesis y corchetes con contenido: (Official Video), [Lyrics], (Audio), (Live), etc.
        clean = clean.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "")
        // Remover "ft.", "feat.", "featuring" y lo que siga (a menos que esté al inicio)
        clean = clean.replace(Regex("\\s+(?:ft\\.?|feat\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE), "")
        // Remover " - Official Video", " - Lyrics", etc. (guion largo o corto)
        clean = clean.replace(Regex("\\s*[-–—]\\s*(?:Official|Lyrics|Audio|Live|HD|4K|Explicit|Clean|Remix|Cover|Version|Version).*", RegexOption.IGNORE_CASE), "")
        // Remover "MV", "M/V" al final
        clean = clean.replace(Regex("\\s+(?:MV|M/V)$"), "")
        // Remover year al final si es "(2024)" o "[2024]"
        clean = clean.replace(Regex("\\s*[\\(\\[]\\d{4}[\\)\\]]$"), "")
        return clean.trim()
    }

    /**
     * Limpia el artista de iTunes: remueve " - Topic", "VEVO", etc.
     */
    fun cleanArtist(artist: String): String {
        var clean = artist
        // Remover " - Topic" (canal de YouTube genérico)
        clean = clean.replace(Regex("\\s*-?\\s*Topic$"), "")
        // Remover "VEVO"
        clean = clean.replace(Regex("\\s*VEVO$", RegexOption.IGNORE_CASE), "")
        return clean.trim()
    }

    suspend fun fetchFullMetadata(song: Song): Result<Song> = withContext(Dispatchers.IO) {
        try {
            Log.e(TAG, "fetchFullMetadata INICIO: '${song.artist}' - '${song.title}'")
            val metadata = searchItunes(song.artist, song.title)
            if (metadata != null) {
                val cleaned = song.copy(
                    title = cleanTitle(metadata.trackName ?: song.title),
                    artist = cleanArtist(metadata.artistName ?: song.artist),
                    album = metadata.collectionName ?: "",
                    genre = sanitizeGenre(metadata.primaryGenreName ?: ""),
                    year = extractYear(metadata.releaseDate ?: ""),
                    trackNumber = metadata.trackNumber ?: 0,
                    thumbnailUrl = metadata.artworkUrl ?: song.thumbnailUrl
                )
                Log.e(TAG, "fetchFullMetadata iTunes OK: '${cleaned.artist}' - '${cleaned.title}' [${cleaned.album}]")
                return@withContext Result.success(cleaned)
            }
            Log.e(TAG, "fetchFullMetadata iTunes sin resultado, intentando Deezer...")

            val dzMetadata = searchDeezer(song.artist, song.title)
            if (dzMetadata != null) {
                val cleaned = song.copy(
                    title = cleanTitle(dzMetadata.title ?: song.title),
                    artist = cleanArtist(dzMetadata.artist ?: song.artist),
                    album = dzMetadata.album ?: "",
                    genre = sanitizeGenre(dzMetadata.genre ?: "")
                )
                Log.e(TAG, "fetchFullMetadata Deezer OK: '${cleaned.artist}' - '${cleaned.title}' [${cleaned.album}]")
                return@withContext Result.success(cleaned)
            }
            Log.e(TAG, "fetchFullMetadata Deezer sin resultado, intentando MusicBrainz...")

            val mbMetadata = searchMusicBrainz(song.artist, song.title)
            if (mbMetadata != null) {
                val cleaned = song.copy(
                    title = mbMetadata.title ?: song.title,
                    artist = mbMetadata.artist ?: song.artist,
                    album = mbMetadata.album ?: "",
                    genre = mbMetadata.genre ?: "",
                    year = mbMetadata.year ?: ""
                )
                Log.e(TAG, "fetchFullMetadata MusicBrainz OK: '${cleaned.artist}' - '${cleaned.title}' [${cleaned.album}]")
                return@withContext Result.success(cleaned)
            }

            // Última instancia: buscar SOLO por título, sin exigir que el artista coincida.
            // Recupera casos donde el artista de la DB está mal (ej. "Boy Boy" → "Big Boy").
            Log.e(TAG, "fetchFullMetadata sin las 3 fuentes, intentando solo por título...")
            val looseTitle = cleanSearchTitle(song.title, song.artist)
            if (looseTitle.isNotBlank()) {
                val looseItunes = searchItunesQuery(looseTitle, "", song.title, true)
                if (looseItunes != null) {
                    val cleaned = song.copy(
                        title = cleanTitle(looseItunes.trackName ?: song.title),
                        artist = cleanArtist(looseItunes.artistName ?: song.artist),
                        album = looseItunes.collectionName ?: "",
                        genre = sanitizeGenre(looseItunes.primaryGenreName ?: ""),
                        year = extractYear(looseItunes.releaseDate ?: ""),
                        trackNumber = looseItunes.trackNumber ?: 0,
                        thumbnailUrl = looseItunes.artworkUrl ?: song.thumbnailUrl
                    )
                    Log.e(TAG, "fetchFullMetadata iTunes solo-título OK: '${cleaned.artist}' - '${cleaned.title}'")
                    return@withContext Result.success(cleaned)
                }
                val looseDeezer = searchDeezer(song.artist, song.title, requireArtist = false)
                if (looseDeezer != null) {
                    val cleaned = song.copy(
                        title = cleanTitle(looseDeezer.title ?: song.title),
                        artist = cleanArtist(looseDeezer.artist ?: song.artist),
                        album = looseDeezer.album ?: "",
                        genre = sanitizeGenre(looseDeezer.genre ?: "")
                    )
                    Log.e(TAG, "fetchFullMetadata Deezer solo-título OK: '${cleaned.artist}' - '${cleaned.title}'")
                    return@withContext Result.success(cleaned)
                }
            }

            Log.e(TAG, "fetchFullMetadata SIN RESULTADO para '${song.artist}' - '${song.title}'")

            Result.success(song)
        } catch (e: Exception) {
            Log.e(TAG, "fetchFullMetadata ERROR: ${e.message}", e)
            Result.success(song)
        }
    }

    private fun searchItunes(artist: String, title: String): ITunesResult? {
        try {
            val cleanArtist = cleanChannelName(artist)
            val cleanTitle = cleanSearchTitle(title, artist)
            val channelLike = looksLikeChannel(artist)
            Log.e(TAG, "searchItunes: '$cleanArtist' '$cleanTitle' channelLike=$channelLike")

            // Estrategia 1: artista + título
            searchItunesQuery("$cleanArtist $cleanTitle", artist, title, channelLike)?.let { return it }
            // Estrategia 2: solo título (para artistas-canal de YouTube)
            searchItunesQuery(cleanTitle, artist, title, channelLike)?.let { return it }
            // Estrategia 3: si el título combina varias canciones ("A / B"), probar solo la primera
            if (cleanTitle.contains("/")) {
                val firstPart = cleanTitle.substringBefore("/").trim()
                if (firstPart.isNotBlank() && firstPart.length >= 3) {
                    searchItunesQuery("$cleanArtist $firstPart", artist, title, channelLike)?.let { return it }
                    searchItunesQuery(firstPart, artist, title, channelLike)?.let { return it }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun searchItunesQuery(
        term: String,
        artist: String,
        title: String,
        channelLike: Boolean
    ): ITunesResult? {
        try {
            val query = URLEncoder.encode(term, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$query&entity=song&limit=10"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: run {
                Log.e(TAG, "searchItunesQuery '$term' HTTP ${response.code} body vacío")
                return null
            }
            Log.e(TAG, "searchItunesQuery '$term' HTTP ${response.code} len=${body.length}")
            val json = JsonParser.parseString(body).asJsonObject
            val results = json.getAsJsonArray("results")
            Log.e(TAG, "searchItunesQuery '$term' results=${results?.size()}")
            if (results != null) {
                for (element in results) {
                    val best = element.asJsonObject
                    val trackName = best.get("trackName")?.asString ?: continue
                    val artistName = best.get("artistName")?.asString ?: continue
                    val match = isGoodMatch(artist, artistName, title, trackName, channelLike)
                    Log.e(TAG, "  result: '$artistName' - '$trackName' match=$match")
                    if (match) {
                        val collectionName = best.get("collectionName")?.asString
                        val primaryGenreName = best.get("primaryGenreName")?.asString
                        val releaseDate = best.get("releaseDate")?.asString
                        val trackNumber = best.get("trackNumber")?.asInt
                        val artworkUrl100 = best.get("artworkUrl100")?.asString
                        Log.e(TAG, "searchItunes MATCH: '$artistName' - '$trackName'")
                        return ITunesResult(
                            trackName = trackName,
                            artistName = artistName,
                            collectionName = collectionName,
                            primaryGenreName = primaryGenreName,
                            releaseDate = releaseDate,
                            trackNumber = trackNumber,
                            artworkUrl = artworkUrl100?.replace("100x100bb", "600x600bb")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchItunesQuery ERROR '$term': ${e.message}")
        }
        return null
    }

    private suspend fun searchDeezer(artist: String, title: String, requireArtist: Boolean = true): DeezerResult? {
        return try {
            val cleanTitle = cleanSearchTitle(title, artist)
            val channelLike = looksLikeChannel(artist)
            // Buscar por título (los artistas-canal no matchean por nombre real)
            val query = URLEncoder.encode("track:\"$cleanTitle\"", "UTF-8")
            val url = "https://api.deezer.com/search?q=$query"
            Log.e(TAG, "searchDeezer query: track=\"$cleanTitle\" requireArtist=$requireArtist")
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.code != 200) {
                Log.e(TAG, "searchDeezer HTTP ${response.code}")
                return null
            }
            val body = response.body?.string() ?: return null
            val json = JsonParser.parseString(body).asJsonObject
            val data = json.getAsJsonArray("data")
            if (data == null || data.size() == 0) return null

            for (element in data) {
                val obj = element.asJsonObject
                val track = obj.get("title")?.asString ?: continue
                val deezerArtist = obj.getAsJsonObject("artist")?.get("name")?.asString ?: continue
                val albumObj = obj.getAsJsonObject("album")
                val album = albumObj?.get("title")?.asString ?: ""

                val eTitle = normalizeForMatch(title)
                val dTitle = normalizeForMatch(track)
                val titleMatch = dTitle.contains(eTitle) || eTitle.contains(dTitle)
                if (!titleMatch) continue

                // Si el artista no parece canal y se exige match, verificar que coincida
                if (requireArtist && !channelLike) {
                    val eArtist = normalizeForMatch(cleanChannelName(artist))
                    val dArtist = normalizeForMatch(deezerArtist)
                    if (!(dArtist.contains(eArtist) || eArtist.contains(dArtist))) continue
                }
                Log.e(TAG, "searchDeezer MATCH: '$deezerArtist' - '$track' [$album]")
                return DeezerResult(
                    title = track,
                    artist = deezerArtist,
                    album = album
                )
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "searchDeezer ERROR: ${e.message}")
            null
        }
    }

    private fun cleanSearchTitle(title: String, artist: String? = null): String {
        var clean = title
        // Si el título repite el artista ("Artista - Artista - Título" o "Artista - Título"),
        // quitar el/los prefijo(s) repetido(s)
        if (!artist.isNullOrBlank()) {
            val normArtist = normalizeForMatch(cleanChannelName(artist))
            var changed = true
            while (changed && clean.contains(" - ")) {
                val first = clean.substringBefore(" - ").trim()
                val rest = clean.substringAfter(" - ").trim()
                val normFirst = normalizeForMatch(first)
                val matches = normArtist.isNotBlank() && normFirst.isNotBlank() &&
                    (normFirst.contains(normArtist) || normArtist.contains(normFirst))
                if (matches && rest.isNotBlank()) {
                    clean = rest
                } else {
                    changed = false
                }
            }
        }
        clean = clean.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "")
        clean = clean.replace(
            Regex("\\s*[-–—]\\s*(?:Official|Lyrics|Audio|Live|HD|4K|MV|M/V|Single|Remix|Cover|Video).*", RegexOption.IGNORE_CASE),
            ""
        )
        clean = clean.replace(Regex("\\s+(?:MV|M/V)$"), "")
        return clean.trim()
    }

    private fun searchMusicBrainz(artist: String, title: String): MusicBrainzResult? {
        try {
            val cleanArtist = cleanChannelName(artist)
            val cleanTitle = title.replace(Regex("\\s*\\(.*?\\)$"), "").replace(Regex("\\s*\\[.*?\\]$"), "").trim()
            val query = URLEncoder.encode("artist:\"$cleanArtist\" AND recording:\"$cleanTitle\"", "UTF-8")
            val url = "https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=3"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MusicDownloader/1.0 (alejo@email.com)")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JsonParser.parseString(body).asJsonObject
            val recordings = json.getAsJsonArray("recordings")
            if (recordings != null && recordings.size() > 0) {
                val rec = recordings.first().asJsonObject
                val recTitle = rec.get("title")?.asString ?: return null
                val artistCredit = rec.getAsJsonArray("artist-credit")
                val recArtist = artistCredit?.first()?.asJsonObject?.get("name")?.asString ?: ""
                val releases = rec.getAsJsonArray("releases")
                var album = ""
                var year = ""
                var genre = ""
                if (releases != null && releases.size() > 0) {
                    val release = releases.first().asJsonObject
                    album = release.get("title")?.asString ?: ""
                    val date = release.get("date")?.asString ?: ""
                    year = extractYear(date)
                    val tags = release.getAsJsonArray("tags")
                    if (tags != null && tags.size() > 0) {
                        val bestTag = tags.first().asJsonObject
                        genre = sanitizeGenre(bestTag.get("name")?.asString?.replaceFirstChar { it.uppercase() } ?: "")
                    }
                }
                return MusicBrainzResult(
                    title = recTitle,
                    artist = recArtist,
                    album = album,
                    year = year,
                    genre = genre
                )
            }
        } catch (_: Exception) {}
        return null
    }

    private fun isGoodMatch(
        expectedArtist: String, actualArtist: String,
        expectedTitle: String, actualTitle: String,
        channelLike: Boolean
    ): Boolean {
        val aArtist = normalizeForMatch(actualArtist)
        val eTitle = normalizeForMatch(expectedTitle)
        val aTitle = normalizeForMatch(actualTitle)
        val titleMatch = aTitle.contains(eTitle) || eTitle.contains(aTitle)
        if (!titleMatch) return false
        // Si el artista parece un canal de YouTube, el título alcanza para matchear
        if (channelLike) return true
        val eArtist = normalizeForMatch(cleanChannelName(expectedArtist))
        return aArtist.contains(eArtist) || eArtist.contains(aArtist)
    }

    /**
     * Detecta artistas que parecen canales de YouTube en vez de nombres reales.
     * Ej: "velapuercavideos", "elvecindariocalle13", "AlexisyFidoOfficial", "YomoMusicOfficial".
     * Un nombre real suele tener espacios ("Gustavo Cerati", "Bad Bunny").
     */
    private fun looksLikeChannel(artist: String): Boolean {
        val clean = cleanChannelName(artist)
        if (clean.isBlank()) return true
        val hasMarker = Regex(
            "(oficial|official|vevo|music|música|musica|videos|video|channel|topic|\\btv\\b)",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(artist)
        return hasMarker || !clean.contains(" ")
    }

    /**
     * Limpia sufijos de canales de YouTube del artista para mejorar el match con iTunes.
     * Ej: "La Mosca Oficial" → "La Mosca", "BersuitTV" → "Bersuit", "elvecindariocalle13" → "calle13"
     */
    private fun cleanChannelName(artist: String): String {
        var clean = artist
        // Remover paréntesis/corchetes con contenido: "(Oficial)", "[Official]", etc.
        clean = clean.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "")
        // Remover sufijos de canal con o sin espacio: "AlexisyFidoOfficial", "YomoMusicOfficial"
        var changed = true
        while (changed) {
            val before = clean
            clean = clean.replace(
                Regex("(?:Oficial|Official|VEVO|Music|Música|Musica|Videos|Video|Audio|Channel|Topic|Realidad)$", RegexOption.IGNORE_CASE),
                ""
            )
            changed = clean != before
        }
        // Remover sufijos precedidos de espacio
        clean = clean.replace(
            Regex("\\s+(?:Oficial|Official|VEVO|Music|Música|Musica|Videos|Audio|HD|4K|Latino|Realidad|Channel|Topic)$", RegexOption.IGNORE_CASE),
            ""
        )
        // Remover "TV" al final (BersuitTV, NickyJamTV, etc.)
        clean = clean.replace(Regex("TV$"), "")
        // Remover " - Topic" (canal de YouTube genérico)
        clean = clean.replace(Regex("\\s*-?\\s*Topic$", RegexOption.IGNORE_CASE), "")
        // Remover prefijos de canales concatenados: "elvecindariocalle13", "lamoscatsetsé"
        clean = clean.replace(Regex("^(?:el|la|los|las)(?=[a-záéíóúñ])", RegexOption.IGNORE_CASE), "")
        return clean.trim()
    }

    private fun normalizeForMatch(text: String): String {
        return Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")
            .filter { it.isLetterOrDigit() }
    }

    private fun extractYear(date: String): String {
        return date.take(4).filter { it.isDigit() }
    }

    private data class ITunesResult(
        val trackName: String?,
        val artistName: String?,
        val collectionName: String?,
        val primaryGenreName: String?,
        val releaseDate: String?,
        val trackNumber: Int?,
        val artworkUrl: String?
    )

    private data class MusicBrainzResult(
        val title: String?,
        val artist: String?,
        val album: String?,
        val year: String?,
        val genre: String?
    )

    private data class DeezerResult(
        val title: String?,
        val artist: String?,
        val album: String?,
        val genre: String? = null
    )
}
