package com.beatohm.metadata

import android.util.Log
import com.google.gson.JsonParser
import com.beatohm.network.NetworkModule
import com.beatohm.util.ApiKeyProvider
import com.beatohm.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder
import java.text.Normalizer

class LyricsFetcher {

    data class LyricsResult(
        val plainText: String,
        val syncedLrc: String? = null
    )

    private val client = NetworkModule.client

    suspend fun fetchLyrics(artist: String, title: String): Result<LyricsResult> =
        withContext(Dispatchers.IO) {
            AppLogger.d(TAG, "fetchLyrics start: artist=${artist.length}ch, title=${title.length}ch")
            val variants = searchVariants(artist, title)
            AppLogger.d(TAG, "fetchLyrics variants: ${variants.size}")
            for ((i, variant) in variants.withIndex()) {
                val (a, t) = variant
                AppLogger.d(TAG, "fetchLyrics variant $i: ${a.length}/${t.length}ch")
                val lrclib = fetchFromLrclib(a, t)
                if (lrclib.isSuccess) {
                    AppLogger.d(TAG, "fetchLyrics OK via LRCLIB: ${lrclib.getOrThrow().plainText.length} chars")
                    return@withContext lrclib
                }
                Log.d(TAG, "fetchLyrics LRCLIB failed: ${lrclib.exceptionOrNull()?.message}")
                val genius = fetchFromGenius(a, t)
                if (genius.isSuccess) {
                    AppLogger.d(TAG, "fetchLyrics OK via Genius: ${genius.getOrThrow().plainText.length} chars")
                    return@withContext genius
                }
                Log.d(TAG, "fetchLyrics Genius failed: ${genius.exceptionOrNull()?.message}")
                val ovh = fetchFromLyricsOvh(a, t)
                if (ovh.isSuccess) {
                    AppLogger.d(TAG, "fetchLyrics OK via lyrics.ovh: ${ovh.getOrThrow().plainText.length} chars")
                    return@withContext ovh
                }
                Log.d(TAG, "fetchLyrics lyrics.ovh failed: ${ovh.exceptionOrNull()?.message}")
            }
            // Retry una vez si todos fallaron por DNS/transitorio
            AppLogger.d(TAG, "fetchLyrics retry for ${artist.length}/${title.length}ch")
            kotlinx.coroutines.delay(2000)
            val (a, t) = variants.first()
            val retryLrclib = fetchFromLrclib(a, t)
            if (retryLrclib.isSuccess) {
                AppLogger.d(TAG, "fetchLyrics retry OK via LRCLIB: ${retryLrclib.getOrThrow().plainText.length} chars")
                return@withContext retryLrclib
            }
            val retryGenius = fetchFromGenius(a, t)
            if (retryGenius.isSuccess) {
                AppLogger.d(TAG, "fetchLyrics retry OK via Genius: ${retryGenius.getOrThrow().plainText.length} chars")
                return@withContext retryGenius
            }
            val retryOvh = fetchFromLyricsOvh(a, t)
            if (retryOvh.isSuccess) {
                AppLogger.d(TAG, "fetchLyrics retry OK via lyrics.ovh: ${retryOvh.getOrThrow().plainText.length} chars")
                return@withContext retryOvh
            }
            AppLogger.d(TAG, "fetchLyrics no result for ${artist.length}/${title.length}ch")
            Result.failure(Exception("Lyrics not found"))
        }

    private fun fetchFromLrclib(artist: String, title: String): Result<LyricsResult> {
        return try {
            val url = "https://lrclib.net/api/get?artist_name=${URLEncoder.encode(artist, "UTF-8")}" +
                "&track_name=${URLEncoder.encode(title, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BeatOhm/2.0 (Android)")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    return Result.failure(Exception("LRCLIB HTTP ${response.code}"))
                }
                val body = response.body?.string()
                    ?: return Result.failure(Exception("LRCLIB empty body"))
                val json = JsonParser.parseString(body).asJsonObject
                val synced = json.get("syncedLyrics")?.asString?.takeIf { it.isNotBlank() }
                val plain = json.get("plainLyrics")?.asString?.takeIf { it.isNotBlank() }
                when {
                    synced != null -> {
                        Log.d(TAG, "LRCLIB SYNCED LRC: ${synced.length} chars")
                        Result.success(LyricsResult(plainText = synced, syncedLrc = synced))
                    }
                    plain != null -> {
                        Log.d(TAG, "LRCLIB PLAIN TEXT: ${plain.length} chars")
                        Result.success(LyricsResult(plainText = plain, syncedLrc = null))
                    }
                    else -> Result.failure(Exception("LRCLIB sin letras"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchFromGenius(artist: String, title: String): Result<LyricsResult> {
        val token = ApiKeyProvider.geniusToken()
        if (token == null) {
            return Result.failure(Exception("Genius token not configured"))
        }
        return try {
            val q = URLEncoder.encode("$artist $title", "UTF-8")
            val url = "https://api.genius.com/search?q=$q"
            val searchRequest = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            val songUrl = client.newCall(searchRequest).execute().use { response ->
                if (response.code != 200) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JsonParser.parseString(body).asJsonObject
                val hits = json.getAsJsonObject("response")?.getAsJsonArray("hits")
                hits?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("result")
                    ?.get("url")?.asString
            } ?: return Result.failure(Exception("Genius sin resultados"))

            val pageRequest = Request.Builder()
                .url(songUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                .build()
            val html = client.newCall(pageRequest).execute().use { response ->
                if (response.code != 200) return@use null
                response.body?.string()
            } ?: return Result.failure(Exception("Genius HTML no disponible"))

            val lyrics = extractGeniusLyrics(html)
            if (lyrics.isNullOrBlank()) {
                Result.failure(Exception("Genius sin letras"))
            } else {
                Result.success(LyricsResult(plainText = lyrics, syncedLrc = null))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractGeniusLyrics(html: String): String? {
        val pattern = Regex(
            """<div[^>]*data-lyrics-container="true"[^>]*>(.*?)</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val matches = pattern.findAll(html).toList()
        if (matches.isEmpty()) return null
        return matches.joinToString("\n") { it.groupValues[1] }
            .replace(Regex("<br>\\s*"), "\n")
            .replace(Regex("<br\\s*/>\\s*"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("&#\\d+;")) { match ->
                val code = match.value.removeSurrounding("&#", ";").toIntOrNull()
                if (code != null) code.toChar().toString() else match.value
            }
            .trim()
    }

    private fun fetchFromLyricsOvh(artist: String, title: String): Result<LyricsResult> {
        return try {
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val url = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    return Result.failure(Exception("lyrics.ovh HTTP ${response.code}"))
                }
                val body = response.body?.string()
                    ?: return Result.failure(Exception("lyrics.ovh empty body"))
                val lyrics = JsonParser.parseString(body).asJsonObject
                    .get("lyrics")?.asString?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(Exception("lyrics.ovh sin letras"))
                Result.success(LyricsResult(plainText = lyrics, syncedLrc = null))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun searchVariants(artist: String, title: String): List<Pair<String, String>> {
        val variants = mutableListOf(artist to title)
        val stripped = normalizeDiacritics(artist) to normalizeDiacritics(title)
        if (stripped != variants.first()) variants.add(stripped)
        val ascii = artist.replace(Regex("[^a-zA-Z0-9 ]"), "").trim() to
            title.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
        if (ascii != stripped && ascii != variants.first()) variants.add(ascii)
        return variants
    }

    private fun normalizeDiacritics(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")
    }

    companion object {
        private const val TAG = "BeatOhm"
    }
}
