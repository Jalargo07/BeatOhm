package com.beatohm.metadata

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.beatohm.model.Song
import com.beatohm.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder
import java.text.Normalizer

class MetadataFetcher {

    companion object {
        private const val TAG = "MetadataFetcher"
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
        // Remover texto después de "|" (pipe de canales: "Song | The Cypher Effect")
        clean = clean.replace(Regex("\\s*\\|.*$"), "")
        // Remover nombres de canales/series conocidos
        clean = clean.replace(Regex("\\s*(?:The\\s+)?(?:Cypher\\s+Effect|Mic\\s+Check\\s+Session|Freestyle|Batalla|Red\\s+Bull|Audiomack|SoundCloud).*", RegexOption.IGNORE_CASE), "")
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

            // Attempt 1: artist + title (cleaned)
            val metadata = searchItunes(song.artist, song.title)
            if (metadata != null) {
                val cleaned = song.copy(
                    title = cleanTitle(metadata.trackName ?: song.title),
                    artist = cleanArtist(metadata.artistName ?: song.artist),
                    album = metadata.collectionName ?: "",
                    genre = metadata.primaryGenreName ?: "",
                    year = extractYear(metadata.releaseDate ?: ""),
                    trackNumber = metadata.trackNumber ?: 0,
                    thumbnailUrl = metadata.artworkUrl ?: song.thumbnailUrl
                )
                Log.e(TAG, "fetchFullMetadata iTunes OK: '${cleaned.artist}' - '${cleaned.title}' [${cleaned.album}]")
                return@withContext Result.success(cleaned)
            }

            // Attempt 2: MusicBrainz with artist + title
            Log.e(TAG, "fetchFullMetadata iTunes sin resultado, intentando MusicBrainz...")
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

            // Attempt 3: iTunes with ONLY title (no artist) — fallback cuando el artista es basura del canal
            Log.e(TAG, "fetchFullMetadata ambos fallaron, intentando iTunes solo con título...")
            val titleOnlyResult = searchItunes("", song.title)
            if (titleOnlyResult != null) {
                val cleaned = song.copy(
                    title = cleanTitle(titleOnlyResult.trackName ?: song.title),
                    artist = cleanArtist(titleOnlyResult.artistName ?: song.artist),
                    album = titleOnlyResult.collectionName ?: "",
                    genre = titleOnlyResult.primaryGenreName ?: "",
                    year = extractYear(titleOnlyResult.releaseDate ?: ""),
                    trackNumber = titleOnlyResult.trackNumber ?: 0,
                    thumbnailUrl = titleOnlyResult.artworkUrl ?: song.thumbnailUrl
                )
                Log.e(TAG, "fetchFullMetadata iTunes (title-only) OK: '${cleaned.artist}' - '${cleaned.title}' [${cleaned.album}]")
                return@withContext Result.success(cleaned)
            }

            // Attempt 4: MusicBrainz with ONLY title
            Log.e(TAG, "fetchFullMetadata iTunes title-only falló, intentando MusicBrainz solo con título...")
            val mbTitleOnly = searchMusicBrainz("", song.title)
            if (mbTitleOnly != null) {
                val cleaned = song.copy(
                    title = mbTitleOnly.title ?: song.title,
                    artist = mbTitleOnly.artist ?: song.artist,
                    album = mbTitleOnly.album ?: "",
                    genre = mbTitleOnly.genre ?: "",
                    year = mbTitleOnly.year ?: ""
                )
                Log.e(TAG, "fetchFullMetadata MusicBrainz (title-only) OK: '${cleaned.artist}' - '${cleaned.title}' [${cleaned.album}]")
                return@withContext Result.success(cleaned)
            }

            Log.e(TAG, "fetchFullMetadata SIN RESULTADO para '${song.artist}' - '${song.title}'")
            // Apply cleaning even when no metadata found — cleans channel names, playlists, etc.
            val cleanedSong = song.copy(
                artist = cleanArtist(cleanChannelName(song.artist)),
                title = cleanTitle(song.title)
            )
            Log.e(TAG, "fetchFullMetadata cleaned fallback: '${cleanedSong.artist}' - '${cleanedSong.title}'")
            Result.success(cleanedSong)
        } catch (e: Exception) {
            Log.e(TAG, "fetchFullMetadata ERROR: ${e.message}", e)
            Result.success(song)
        }
    }

    private fun searchItunes(artist: String, title: String): ITunesResult? {
        try {
            val cleanArtist = cleanChannelName(artist)
            val cleanTitle = cleanTitle(title)
            Log.e(TAG, "searchItunes: artist='$artist' → '$cleanArtist', title='$title' → '$cleanTitle'")
            val query = if (cleanArtist.isNotBlank()) {
                URLEncoder.encode("$cleanArtist $cleanTitle", "UTF-8")
            } else {
                URLEncoder.encode(cleanTitle, "UTF-8")
            }
            Log.e(TAG, "searchItunes query: '$query'")
            val url = "https://itunes.apple.com/search?term=$query&entity=song&limit=3"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JsonParser.parseString(body).asJsonObject
            val results = json.getAsJsonArray("results")
            if (results != null && results.size() > 0) {
                val best = results.first().asJsonObject
                val trackName = best.get("trackName")?.asString
                val artistName = best.get("artistName")?.asString
                val collectionName = best.get("collectionName")?.asString
                val primaryGenreName = best.get("primaryGenreName")?.asString
                val releaseDate = best.get("releaseDate")?.asString
                val trackNumber = best.get("trackNumber")?.asInt
                val artworkUrl100 = best.get("artworkUrl100")?.asString

                if (isGoodMatch(artist, artistName ?: "", title, trackName ?: "")) {
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
        } catch (_: Exception) {}
        return null
    }

    private fun searchMusicBrainz(artist: String, title: String): MusicBrainzResult? {
        try {
            val cleanArtist = cleanChannelName(artist)
            val cleanTitle = cleanTitle(title)
            Log.e(TAG, "searchMusicBrainz: artist='$artist' → '$cleanArtist', title='$title' → '$cleanTitle'")
            val query = if (cleanArtist.isNotBlank()) {
                URLEncoder.encode("artist:\"$cleanArtist\" AND recording:\"$cleanTitle\"", "UTF-8")
            } else {
                URLEncoder.encode("recording:\"$cleanTitle\"", "UTF-8")
            }
            Log.e(TAG, "searchMusicBrainz query: '$query'")
            val url = "https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=3"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BeatOhm/1.0 (alejo@email.com)")
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
                        genre = bestTag.get("name")?.asString?.replaceFirstChar { it.uppercase() } ?: ""
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
        expectedTitle: String, actualTitle: String
    ): Boolean {
        val eArtist = normalizeForMatch(cleanChannelName(expectedArtist))
        val aArtist = normalizeForMatch(actualArtist)
        val eTitle = normalizeForMatch(expectedTitle)
        val aTitle = normalizeForMatch(actualTitle)
        return (aArtist.contains(eArtist) || eArtist.contains(aArtist)) &&
               (aTitle.contains(eTitle) || eTitle.contains(aTitle))
    }

    /**
     * Limpia sufijos de canales de YouTube del artista para mejorar el match con iTunes.
     * Ej: "La Mosca Oficial" → "La Mosca", "BersuitTV" → "Bersuit", "elvecindariocalle13" → "calle13"
     */
    private fun cleanChannelName(artist: String): String {
        var clean = artist
        // Remover paréntesis/corchetes con contenido: "(Oficial)", "[Official]", etc.
        clean = clean.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "")
        // Remover nombres de playlists: "Letras Trap & Más", "Lyrics & Vibes", etc.
        clean = clean.replace(Regex("^\\s*(?:Letras?|Lyrics?|Canciones?|Songs?|Músicas?|Music)\\b.*", RegexOption.IGNORE_CASE), "")
        // Remover "En Español", "En vivo", "En Directo", "En Concierto" al final
        clean = clean.replace(Regex("\\s+En\\s+(?:Español|Espanol|Vivo|Directo|Concierto)$", RegexOption.IGNORE_CASE), "")
        // Remover sufijos comunes de canales
        clean = clean.replace(Regex("\\s+(?:Oficial|Official|VEVO|Music|Videos|Audio|HD|4K|Latino|Realidad|Records|Entertainment|Productions|Studios)$", RegexOption.IGNORE_CASE), "")
        // Remover "TV" al final (BersuitTV, NickyJamTV, etc.)
        clean = clean.replace(Regex("TV$"), "")
        // Remover prefijos de canales concatenados: "elvecindariocalle13", "lamoscatsetsé"
        clean = clean.replace(Regex("^(?:el|la|los|las)(?=[a-záéíóúñ])", RegexOption.IGNORE_CASE), "")
        Log.e(TAG, "cleanChannelName: '$artist' → '$clean'")
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
}
