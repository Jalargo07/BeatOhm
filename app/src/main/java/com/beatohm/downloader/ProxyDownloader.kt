package com.beatohm.downloader

import android.util.Log
import com.google.gson.JsonParser
import com.beatohm.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

class ProxyDownloader {

    private val client = NetworkModule.client

    data class ProxyUrl(val url: String, val filename: String)

    suspend fun getDownloadUrl(youtubeUrl: String): Result<ProxyUrl> = withContext(Dispatchers.IO) {
        try {
            val normalUrl = youtubeUrl.replace("music.youtube.com", "www.youtube.com")
            Log.e(TAG, "Proxy para: $normalUrl")
            tryLoaderTo(normalUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Proxy error", e)
            Result.failure(e)
        }
    }

    private fun tryLoaderTo(youtubeUrl: String): Result<ProxyUrl> {
        return try {
            val encoded = URLEncoder.encode(youtubeUrl, "UTF-8")
            val apiUrl = "https://loader.to/ajax/download.php?url=$encoded&format=mp3"

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val initBody: String
            val initCode: Int
            client.newCall(request).execute().use { response ->
                initBody = response.body?.string() ?: ""
                initCode = response.code
            }

            if (initCode != 200) {
                return Result.failure(Exception("Loader.to HTTP $initCode"))
            }

            val initJson = JsonParser.parseString(initBody).asJsonObject
            val success = initJson.get("success")?.asBoolean ?: false
            if (!success) {
                return Result.failure(Exception("Loader.to: success=false"))
            }

            val title = initJson.get("title")?.asString ?: "audio.mp3"

            val progressUrl = initJson.get("progress_url")?.asString
            if (progressUrl == null) {
                val directUrl = initJson.get("url")?.asString
                if (directUrl != null && directUrl.isNotEmpty()) {
                    Log.e(TAG, "Loader.to direct: $directUrl")
                    return Result.success(ProxyUrl(directUrl, title))
                }
                return Result.failure(Exception("Loader.to: no URL or progress_url"))
            }

            Log.e(TAG, "Loader.to polling: $progressUrl")
            for (i in 0 until 60) {
                Thread.sleep(1000)
                val pollRequest = Request.Builder()
                    .url(progressUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val pollBody: String
                client.newCall(pollRequest).execute().use { pollResponse ->
                    pollBody = pollResponse.body?.string() ?: ""
                }

                try {
                    val pollJson = JsonParser.parseString(pollBody).asJsonObject
                    val downloadUrl = pollJson.get("download_url")?.asString
                        ?: pollJson.get("url")?.asString

                    if (downloadUrl != null && downloadUrl.isNotEmpty()) {
                        Log.e(TAG, "Loader.to OK: $downloadUrl")
                        return Result.success(ProxyUrl(downloadUrl, title))
                    }

                    val progress = pollJson.get("progress")?.asInt ?: 0
                    if (progress >= 1000) {
                        val text = pollJson.get("text")?.asString ?: ""
                        return Result.failure(Exception("Loader.to error: $text"))
                    }
                } catch (_: Exception) {}
            }

            Result.failure(Exception("Loader.to: polling timeout"))
        } catch (e: Exception) {
            Log.e(TAG, "Loader.to exception: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "BeatOhm"
    }
}
