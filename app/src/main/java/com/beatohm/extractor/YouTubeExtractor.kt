package com.beatohm.extractor

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.beatohm.model.SearchResult
import com.beatohm.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class YouTubeExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class AudioFormat(
        val url: String,
        val mimeType: String,
        val bitrate: Int,
        val contentLength: Long
    )

    suspend fun extractSong(url: String): Result<Song> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Extrayendo canción: $url")
            val videoId = extractVideoId(url) ?: return@withContext Result.failure(Exception("Invalid YouTube URL"))

            val innerTubeResponse = callInnerTubePlayer(videoId)
            if (innerTubeResponse == null) {
                return@withContext Result.failure(Exception("Could not get player response"))
            }

            val videoDetails = innerTubeResponse.getAsJsonObject("videoDetails")
            if (videoDetails != null) {
                val title = videoDetails.get("title")?.asString ?: "Unknown"
                val author = videoDetails.get("author")?.asString ?: "Unknown"
                val length = videoDetails.get("lengthSeconds")?.asString?.toLongOrNull() ?: 0L
                val thumbnails = videoDetails.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                val thumbnail = thumbnails?.last()?.asJsonObject?.get("url")?.asString ?: ""
                Log.d(TAG, "Video: $title by $author")
                val song = Song(
                    title = cleanTitle(title),
                    artist = author,
                    duration = length,
                    thumbnailUrl = thumbnail,
                    youtubeUrl = url,
                    youtubeId = url
                )
                Result.success(song)
            } else {
                val playability = innerTubeResponse.getAsJsonObject("playabilityStatus")
                val status = playability?.get("status")?.asString ?: "UNKNOWN"
                val reason = playability?.get("reason")?.asString ?: playability?.get("status")?.asString ?: ""
                Log.w(TAG, "Video no disponible: status=$status reason=$reason")
                val song = Song(
                    title = "Unknown - ${videoId.take(8)}",
                    artist = "YouTube",
                    duration = 0L,
                    thumbnailUrl = "",
                    youtubeUrl = url,
                    youtubeId = url
                )
                Result.success(song)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en extractSong", e)
            Result.failure(e)
        }
    }

    suspend fun extractPlaylist(url: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Extrayendo playlist: $url")

            val response = callInnerTubeBrowse(url)
            if (response == null) {
                return@withContext Result.failure(Exception("Could not get playlist data"))
            }

            val videoIds = extractVideoIdsFromInnerTubeBrowse(response)
            if (videoIds.isEmpty()) {
                Log.w(TAG, "No videoIds en InnerTube browse response")
                return@withContext Result.failure(Exception("No videos found in playlist"))
            }

            Log.d(TAG, "Videos en playlist: ${videoIds.size}")
            val songs = mutableListOf<Song>()
            for (vid in videoIds) {
                try {
                    val videoUrl = "https://www.youtube.com/watch?v=$vid"
                    val result = extractSong(videoUrl)
                    result.getOrNull()?.let { songs.add(it) }
                } catch (_: Exception) {}
            }
            Result.success(songs)
        } catch (e: Exception) {
            Log.e(TAG, "Error en extractPlaylist", e)
            Result.failure(e)
        }
    }

    suspend fun getBestAudioStream(url: String): Result<AudioFormat> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Obteniendo audio stream: $url")
            val videoId = extractVideoId(url) ?: return@withContext Result.failure(Exception("Invalid URL"))

            val innerTubeResponse = callInnerTubePlayer(videoId)
            if (innerTubeResponse == null) {
                return@withContext Result.failure(Exception("Could not get player response"))
            }

            val formats = parseFormats(innerTubeResponse)
            if (formats.isEmpty()) {
                Log.w(TAG, "No audio formats found")
                return@withContext Result.failure(Exception("No audio streams available"))
            }

            val best = formats.maxByOrNull { it.bitrate } ?: formats.first()
            val ipParam = extractQueryParam(best.url, "ip")
            Log.e(TAG, "Mejor audio: ${best.bitrate}bps - ${best.mimeType} ip=$ipParam")
            Log.e(TAG, "URL audio: ${best.url}")
            Result.success(best)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo audio", e)
            Result.failure(e)
        }
    }

    private fun extractQueryParam(url: String, param: String): String? {
        val regex = Regex("[?&]$param=([^&]+)")
        val match = regex.find(url) ?: return null
        return java.net.URLDecoder.decode(match.groupValues[1], "UTF-8")
    }

    private fun callInnerTubePlayer(videoId: String): JsonObject? {
        val jsonBody = buildClientContext().apply { addProperty("videoId", videoId) }
        return postInnerTube("player", jsonBody, IOS_USER_AGENT)
    }

    private fun callInnerTubeBrowse(url: String): JsonObject? {
        val listId = extractListId(url) ?: return null
        val jsonBody = buildClientContext().apply { addProperty("browseId", "VL$listId") }
        return postInnerTube("browse", jsonBody, IOS_USER_AGENT)
    }

    private fun buildClientContext(
        clientName: String = "IOS",
        clientVersion: String = IOS_CLIENT_VERSION,
        deviceModel: String = "iPhone16,2",
        osVersion: String = "18.7.2.22H124"
    ): JsonObject {
        return JsonObject().apply {
            add("context", JsonObject().apply {
                add("client", JsonObject().apply {
                    addProperty("clientName", clientName)
                    addProperty("clientVersion", clientVersion)
                    if (clientName == "IOS") {
                        addProperty("deviceModel", deviceModel)
                        addProperty("osVersion", osVersion)
                    }
                    addProperty("hl", "en")
                    addProperty("gl", "US")
                })
            })
        }
    }

    private fun postInnerTube(endpoint: String, jsonBody: JsonObject, userAgent: String): JsonObject? {
        return try {
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/$endpoint")
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            Log.d(TAG, "$endpoint API: ${response.code}")
            if (response.code != 200) {
                Log.w(TAG, "$endpoint response body: ${body.take(300)}")
                return null
            }
            JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            Log.e(TAG, "$endpoint API error", e)
            null
        }
    }

    suspend fun searchSongs(query: String): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val response = callInnerTubeSearch(query)
            if (response == null) {
                return@withContext Result.failure(Exception("No se pudo contactar el buscador"))
            }
            val results = parseSearchResults(response)
            if (results == null) {
                Log.w(TAG, "No se pudo parsear la respuesta del buscador (estructura inesperada)")
                return@withContext Result.failure(Exception("Respuesta inesperada del buscador"))
            }
            Log.d(TAG, "Resultados de búsqueda: ${results.size}")
            Result.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "Error en searchSongs", e)
            Result.failure(e)
        }
    }

    private fun callInnerTubeSearch(query: String): JsonObject? {
        val jsonBody = buildClientContext(
            clientName = "WEB",
            clientVersion = "2.20240611.01.00"
        ).apply {
            addProperty("query", query)
            addProperty("contentCheckOk", true)
            addProperty("racyCheckOk", true)
        }
        return postInnerTube("search", jsonBody, WEB_USER_AGENT)
    }

    private fun parseSearchResults(json: JsonObject): List<SearchResult>? {
        val results = mutableListOf<SearchResult>()
        val sectionList = try {
            json
                .getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnSearchResultsRenderer")
                ?.getAsJsonObject("primaryContents")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")
        } catch (e: Exception) {
            null
        }
        if (sectionList == null) {
            Log.w(TAG, "Estructura de respuesta inesperada. Keys: ${json.keySet()}")
            Log.d(TAG, "Body: ${json.toString().take(500)}")
            return null
        }
        try {
            for (section in sectionList) {
                val items = section.asJsonObject
                    ?.getAsJsonObject("itemSectionRenderer")
                    ?.getAsJsonArray("contents") ?: continue

                for (item in items) {
                    val videoRenderer = item.asJsonObject
                        ?.getAsJsonObject("videoRenderer") ?: continue

                    try {
                        val videoId = videoRenderer.get("videoId")?.asString ?: continue
                        if (videoId.length != 11) continue

                        val title = videoRenderer
                            .getAsJsonObject("title")?.getAsJsonArray("runs")
                            ?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "Unknown"

                        val channel = videoRenderer
                            .getAsJsonObject("ownerText")?.getAsJsonArray("runs")
                            ?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "Unknown"

                        val durationText = videoRenderer
                            .getAsJsonObject("lengthText")?.get("simpleText")?.asString ?: "0:00"

                        val thumbnails = videoRenderer
                            .getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                        val thumbnailUrl = thumbnails?.lastOrNull()?.asJsonObject
                            ?.get("url")?.asString ?: ""

                        results.add(SearchResult(
                            videoId = videoId,
                            title = title,
                            channelName = channel,
                            durationText = durationText,
                            durationSeconds = parseDuration(durationText),
                            thumbnailUrl = thumbnailUrl
                        ))
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando resultados de búsqueda", e)
        }
        return results
    }

    private fun parseDuration(text: String): Long {
        return try {
            val parts = text.split(":").map { it.toLong() }
            when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                1 -> parts[0]
                else -> 0L
            }
        } catch (_: Exception) { 0L }
    }

    private fun parseFormats(json: JsonObject): List<AudioFormat> {
        val formats = mutableListOf<AudioFormat>()
        try {
            val streamingData = json.getAsJsonObject("streamingData") ?: return formats
            val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")
            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.size()) {
                    val fmt = adaptiveFormats[i].asJsonObject
                    val mimeType = fmt.get("mimeType")?.asString ?: ""
                    if (mimeType.startsWith("audio/")) {
                        val url = resolveStreamUrl(fmt)
                        if (url != null) {
                            val bitrate = fmt.get("bitrate")?.asInt ?: 0
                            val contentLength = fmt.get("contentLength")?.asLong ?: 0L
                            formats.add(AudioFormat(url, mimeType, bitrate, contentLength))
                        }
                    }
                }
            }
            formats.sortByDescending { it.bitrate }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing formats", e)
        }
        return formats
    }

    private fun resolveStreamUrl(fmt: JsonObject): String? {
        var url = fmt.get("url")?.asString
        if (url != null) return url
        val cipher = fmt.get("signatureCipher")?.asString ?: fmt.get("cipher")?.asString ?: return null
        cipher.split("&").forEach { param ->
            if (param.startsWith("url=")) {
                url = java.net.URLDecoder.decode(param.substring(4), "UTF-8")
            }
        }
        return url
    }

    private fun extractVideoIdsFromInnerTubeBrowse(json: JsonObject): List<String> {
        val ids = mutableListOf<String>()
        try {
            val contents = json.getAsJsonObject("contents")
                ?: json.getAsJsonObject("sidebar")?.getAsJsonObject("items")
            if (contents != null) {
                findVideoIds(contents, ids)
            }
            val playlistVideoList = findInJson(json, "playlistVideoListRenderer")
            if (playlistVideoList != null) {
                findVideoIds(playlistVideoList, ids)
            }
        } catch (_: Exception) {}
        return ids.distinct()
    }

    private fun findVideoIds(obj: JsonObject, ids: MutableList<String>) {
        for (key in obj.keySet()) {
            try {
                if (key == "videoId") {
                    val id = obj.get(key).asString
                    if (id.length == 11) ids.add(id)
                } else {
                    val element = obj.get(key)
                    if (element.isJsonObject) {
                        findVideoIds(element.asJsonObject, ids)
                    } else if (element.isJsonArray) {
                        for (i in 0 until element.asJsonArray.size()) {
                            val arrElement = element.asJsonArray[i]
                            if (arrElement.isJsonObject) {
                                findVideoIds(arrElement.asJsonObject, ids)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun findInJson(obj: JsonObject, targetKey: String): JsonObject? {
        for (key in obj.keySet()) {
            try {
                if (key == targetKey) return obj.getAsJsonObject(key)
                val element = obj.get(key)
                if (element.isJsonObject) {
                    findInJson(element.asJsonObject, targetKey)?.let { return it }
                } else if (element.isJsonArray) {
                    for (i in 0 until element.asJsonArray.size()) {
                        val arrElement = element.asJsonArray[i]
                        if (arrElement.isJsonObject) {
                            findInJson(arrElement.asJsonObject, targetKey)?.let { return it }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("v=([a-zA-Z0-9_-]{11})"),
            Regex("youtu\\.be/([a-zA-Z0-9_-]{11})"),
            Regex("/embed/([a-zA-Z0-9_-]{11})"),
            Regex("/shorts/([a-zA-Z0-9_-]{11})")
        )
        for (p in patterns) {
            val match = p.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    private fun extractListId(url: String): String? {
        val match = Regex("[?&]list=([a-zA-Z0-9_-]+)").find(url)
        return match?.groupValues?.get(1)
    }

    fun isPlaylistUrl(url: String): Boolean {
        val clean = url.trim()
        return clean.contains("/playlist?list=") ||
               clean.contains("/playlists?list=") ||
               (clean.contains("list=") && !clean.contains("/watch?"))
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("\\s*\\(.*?\\)\\s*$"), "")
            .replace(Regex("\\s*\\[.*?\\]\\s*$"), "")
            .replace(Regex("(?i)-\\s*(Topic|Official\\s+(Audio|Video|Lyrics?|Music))\\s*$"), "")
            .replace(Regex("(?i)\\s*\\|.*$"), "")
            .trim()
    }

    companion object {
        private const val TAG = "BeatOhm"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; SM-G960F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"
        private const val IOS_CLIENT_VERSION = "20.10.4"
        private const val IOS_USER_AGENT = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X)"
        private const val WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}
