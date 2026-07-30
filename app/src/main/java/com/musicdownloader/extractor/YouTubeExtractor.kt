package com.musicdownloader.extractor

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.musicdownloader.model.Song
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

            val videoDetails = innerTubeResponse.asJsonObject.getAsJsonObject("videoDetails")
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
                val playability = innerTubeResponse.asJsonObject.getAsJsonObject("playabilityStatus")
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

            val videoIds = extractVideoIdsFromInnerTubeBrowse(response.asJsonObject)
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

            val formats = parseFormats(innerTubeResponse.asJsonObject)
            if (formats.isEmpty()) {
                Log.w(TAG, "No audio formats found")
                return@withContext Result.failure(Exception("No audio streams available"))
            }

            val best = formats.maxByOrNull { it.bitrate } ?: formats.first()
            Log.d(TAG, "Mejor audio: ${best.bitrate}bps - ${best.mimeType}")
            Result.success(best)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo audio", e)
            Result.failure(e)
        }
    }

    private fun callInnerTubePlayer(videoId: String): JsonElement? {
        return try {
            val jsonBody = JsonObject().apply {
                add("context", JsonObject().apply {
                    add("client", JsonObject().apply {
                        addProperty("clientName", "IOS")
                        addProperty("clientVersion", "21.03.2")
                        addProperty("deviceModel", "iPhone16,2")
                        addProperty("osVersion", "18.7.2.22H124")
                        addProperty("hl", "en")
                        addProperty("gl", "US")
                    })
                })
                addProperty("videoId", videoId)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .header("User-Agent", "com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X)")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            Log.d(TAG, "Player API: ${response.code}")
            if (response.code != 200) {
                Log.w(TAG, "Player response body: ${body.take(300)}")
            }
            JsonParser.parseString(body)
        } catch (e: Exception) {
            Log.e(TAG, "Player API error", e)
            null
        }
    }

    private fun callInnerTubeBrowse(url: String): JsonElement? {
        return try {
            val listId = extractListId(url) ?: return null
            val jsonBody = JsonObject().apply {
                add("context", JsonObject().apply {
                    add("client", JsonObject().apply {
                        addProperty("clientName", "IOS")
                        addProperty("clientVersion", "21.03.2")
                        addProperty("deviceModel", "iPhone16,2")
                        addProperty("osVersion", "18.7.2.22H124")
                        addProperty("hl", "en")
                        addProperty("gl", "US")
                    })
                })
                addProperty("browseId", "VL$listId")
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse")
                .header("User-Agent", "com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X)")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            Log.d(TAG, "Browse API: ${response.code}")
            if (response.code != 200) {
                Log.w(TAG, "Browse response body: ${body.take(300)}")
            }
            JsonParser.parseString(body)
        } catch (e: Exception) {
            Log.e(TAG, "Browse API error", e)
            null
        }
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
        private const val TAG = "MusicDownloader"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; SM-G960F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"
    }
}
