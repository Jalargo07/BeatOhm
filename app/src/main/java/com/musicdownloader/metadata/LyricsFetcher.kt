package com.musicdownloader.metadata

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LyricsFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLyrics(artist: String, title: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanArtist = artist.replace(" & ", " and ")
                .replace(Regex("[^a-zA-Z0-9 &]"), "")
                .trim()
            val cleanTitle = title.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()

            val encodedArtist = java.net.URLEncoder.encode(cleanArtist, "UTF-8")
            val encodedTitle = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
            val url = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"

            Log.e(TAG, "Buscando letras: $url")
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (response.code != 200) {
                response.close()
                return@withContext Result.failure(Exception("Lyrics not found: ${response.code}"))
            }

            val body = response.body?.string() ?: ""
            response.close()

            val json = JsonParser.parseString(body).asJsonObject
            val lyrics = json.get("lyrics")?.asString
                ?: return@withContext Result.failure(Exception("No lyrics field"))

            Log.e(TAG, "Letras encontradas: ${lyrics.length} chars")
            Result.success(lyrics)
        } catch (e: Exception) {
            Log.e(TAG, "Lyrics fetch error: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "MusicDownloader"
    }
}
