package com.musicdownloader.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.musicdownloader.DeviceUtils
import com.musicdownloader.audio.WaveformExtractor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class WaveformRepository(private val context: Context) : IWaveformRepository {

    private val dao = AppDatabase.getInstance(context).songDao()

    override suspend fun extractMissingWaveforms(
        songs: List<LocalSong>,
        onProgress: ((done: Int, total: Int) -> Unit)?
    ) {
        withContext(Dispatchers.IO) {
            val songsNeedingWaveform = songs.filter { it.waveformData.isBlank() }
            if (songsNeedingWaveform.isEmpty()) return@withContext

            val threads = DeviceUtils.getOptimalThreadCount(context)
            val semaphore = Semaphore(threads)
            val total = songsNeedingWaveform.size
            val done = AtomicInteger(0)

            Log.d(TAG, "Extracting $total waveforms with $threads threads (batched by 10)")

            val batches = songsNeedingWaveform.chunked(10)

            batches.map { batch ->
                async {
                    semaphore.withPermit {
                        for (song in batch) {
                            try {
                                val realDurationMs = getRealDurationMs(song)
                                val numBars = WaveformExtractor.barsForDuration(realDurationMs)
                                val data = WaveformExtractor.extract(song.filePath, numBars)
                                val json = Gson().toJson(data.toList())
                                dao.updateWaveform(song.id, json)
                            } catch (_: Exception) {}
                            val current = done.incrementAndGet()
                            onProgress?.invoke(current, total)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    override suspend fun resetWaveform(song: LocalSong) {
        val realDurationMs = getRealDurationMs(song)
        Log.d(TAG, "resetWaveform: '${song.title}' dbDuration=${song.duration} realDurationMs=$realDurationMs")
        dao.clearWaveform(song.id)
        val numBars = WaveformExtractor.barsForDuration(realDurationMs)
        val data = WaveformExtractor.extract(song.filePath, numBars)
        val json = Gson().toJson(data.toList())
        dao.updateWaveform(song.id, json)
        Log.d(TAG, "resetWaveform: saved ${data.size} bars to DB")
    }

    override suspend fun updateWaveform(songId: String, json: String) {
        dao.updateWaveform(songId, json)
    }

    private fun getRealDurationMs(song: LocalSong): Long {
        val extractor = android.media.MediaExtractor()
        return try {
            extractor.setDataSource(song.filePath)
            var durationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    if (fmt.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                        durationUs = fmt.getLong(android.media.MediaFormat.KEY_DURATION)
                    }
                    break
                }
            }
            if (durationUs > 0L) durationUs / 1000L else song.duration
        } catch (_: Exception) { song.duration } finally { extractor.release() }
    }

    companion object {
        private const val TAG = "WaveformRepository"
    }
}
