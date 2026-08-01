package com.musicdownloader.metadata

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.musicdownloader.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MetadataFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun fetchFullMetadata(song: Song): Result<Song> = withContext(Dispatchers.IO) {
        try {
            val metadata = searchItunes(song.artist, song.title)
            if (metadata != null) {
                return@withContext Result.success(
                    song.copy(
                        title = metadata.trackName ?: song.title,
                        artist = metadata.artistName ?: song.artist,
                        album = metadata.collectionName ?: "",
                        genre = metadata.primaryGenreName ?: "",
                        year = extractYear(metadata.releaseDate ?: ""),
                        trackNumber = metadata.trackNumber ?: 0,
                        thumbnailUrl = metadata.artworkUrl ?: song.thumbnailUrl
                    )
                )
            }

            val mbMetadata = searchMusicBrainz(song.artist, song.title)
            if (mbMetadata != null) {
                return@withContext Result.success(
                    song.copy(
                        title = mbMetadata.title ?: song.title,
                        artist = mbMetadata.artist ?: song.artist,
                        album = mbMetadata.album ?: "",
                        genre = mbMetadata.genre ?: "",
                        year = mbMetadata.year ?: ""
                    )
                )
            }

            Result.success(song)
        } catch (e: Exception) {
            Result.success(song)
        }
    }

    private fun searchItunes(artist: String, title: String): ITunesResult? {
        try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
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
            val query = URLEncoder.encode("artist:\"$artist\" AND recording:\"$title\"", "UTF-8")
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
        val eArtist = expectedArtist.lowercase().filter { it.isLetterOrDigit() }
        val aArtist = actualArtist.lowercase().filter { it.isLetterOrDigit() }
        val eTitle = expectedTitle.lowercase().filter { it.isLetterOrDigit() }
        val aTitle = actualTitle.lowercase().filter { it.isLetterOrDigit() }
        return (aArtist.contains(eArtist) || eArtist.contains(aArtist)) &&
               (aTitle.contains(eTitle) || eTitle.contains(aTitle))
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
