package com.musicdownloader.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WaveformExtractor {

    private const val TAG = "WaveformExtractor"

    /**
     * Calculates optimal number of bars: 1 bar per 0.1 seconds of audio.
     * Higher resolution captures transients and dynamic range better.
     */
    fun barsForDuration(durationMs: Long): Int {
        val durationSec = durationMs / 1000.0
        val bars = (durationSec / 0.1).toInt().coerceIn(50, 1000)
        Log.d(TAG, "barsForDuration: durationMs=$durationMs → durationSec=${"%.1f".format(durationSec)} → bars=$bars")
        return bars
    }

    suspend fun extract(
        filePath: String,
        numBars: Int = 300
    ): FloatArray = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "extract START: file='${filePath.substringAfterLast("/")}' numBars=$numBars")
        try {
            val result = extractInternal(filePath, numBars)
            val elapsed = System.currentTimeMillis() - startTime
            val avg = result.average()
            val min = result.minOrNull() ?: 0f
            val max = result.maxOrNull() ?: 0f
            val nonZero = result.count { it > 0.11f }
            Log.d(TAG, "extract DONE: ${elapsed}ms bars=${result.size} min=${"%.3f".format(min)} max=${"%.3f".format(max)} avg=${"%.3f".format(avg)} nonZero=$nonZero/${result.size}")
            Log.d(TAG, "extract first10: [${result.take(10).joinToString { "%.3f".format(it) }}]")
            result
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(TAG, "extract FAILED: ${elapsed}ms ${e.message}")
            FloatArray(numBars) { 0.3f }
        }
    }

    private fun extractInternal(filePath: String, numBars: Int): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(filePath)

        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = fmt
                break
            }
        }
        if (audioTrackIndex < 0 || format == null) {
            Log.e(TAG, "No audio track found")
            extractor.release()
            return FloatArray(numBars) { 0.3f }
        }

        extractor.selectTrack(audioTrackIndex)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        val totalSamples = (durationUs / 1_000_000.0 * sampleRate).toLong()
        val samplesPerBar = (totalSamples / numBars).coerceAtLeast(1)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val amplitudes = FloatArray(numBars)
        var currentBar = 0
        var samplesInCurrentBar = 0
        var maxPeakInBar = 0
        var isEOS = false

        while (currentBar < numBars) {
            if (!isEOS) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue
                val size = bufferInfo.size
                if (size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + size)

                    val bytesPerFrame = channelCount * 2

                    while (outputBuffer.remaining() >= bytesPerFrame && currentBar < numBars) {
                        val rawSample = outputBuffer.short.toInt()

                        if (channelCount == 2) {
                            outputBuffer.short
                        }

                        val absSample = kotlin.math.abs(rawSample)
                        if (absSample > maxPeakInBar) {
                            maxPeakInBar = absSample
                        }

                        samplesInCurrentBar++

                        if (samplesInCurrentBar >= samplesPerBar) {
                            amplitudes[currentBar] = (maxPeakInBar.toFloat() / Short.MAX_VALUE).coerceIn(0f, 1f)
                            currentBar++
                            samplesInCurrentBar = 0
                            maxPeakInBar = 0
                        }
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }
        }

        if (currentBar < numBars) {
            val lastVal = if (currentBar > 0) amplitudes[currentBar - 1] else 0.1f
            for (i in currentBar until numBars) {
                amplitudes[i] = lastVal
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val maxGlobalPeak = amplitudes.maxOrNull() ?: 1f
        if (maxGlobalPeak > 0f) {
            for (i in amplitudes.indices) {
                amplitudes[i] = (amplitudes[i] / maxGlobalPeak).coerceIn(0.05f, 1f)
            }
        }

        return amplitudes
    }
}
