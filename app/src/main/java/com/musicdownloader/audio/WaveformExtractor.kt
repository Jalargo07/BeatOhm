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

        // Find audio track
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
        val durationSec = durationUs / 1_000_000.0
        val bytesPerSample = if (channelCount == 2) 4 else 2

        Log.d(TAG, "Audio: mime=$mime sampleRate=$sampleRate channels=$channelCount durationUs=$durationUs (${"%.1f".format(durationSec)}s)")
        Log.d(TAG, "Calc: totalSamples=$totalSamples numBars=$numBars samplesPerBar=$samplesPerBar bytesPerSample=$bytesPerSample")

        // Configure decoder
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val amplitudes = FloatArray(numBars)
        var currentBar = 0
        var samplesInCurrentBar = 0
        var maxPeakInBar = 0
        var isEOS = false
        var totalSamplesProcessed = 0L
        var outputCount = 0

        val decodeStart = System.currentTimeMillis()

        while (currentBar < numBars) {
            // Feed input
            if (!isEOS) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                        Log.d(TAG, "EOS sent at bar=$currentBar/$numBars")
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // Read output
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue
                val size = bufferInfo.size
                if (size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + size)
                    outputCount++

                    while (outputBuffer.remaining() >= bytesPerSample && currentBar < numBars) {
                        val sample = outputBuffer.short.toFloat() / Short.MAX_VALUE

                        // For stereo: skip the second channel
                        if (channelCount == 2 && outputBuffer.remaining() >= 2) {
                            outputBuffer.short
                        }

                        val absValue = kotlin.math.abs(sample)
                        if (absValue > maxPeakInBar) {
                            maxPeakInBar = absValue.toInt()
                        }
                        samplesInCurrentBar++
                        totalSamplesProcessed++

                        if (samplesInCurrentBar >= samplesPerBar) {
                            amplitudes[currentBar] = (maxPeakInBar.toFloat() / Short.MAX_VALUE).coerceIn(0f, 1f)
                            currentBar++
                            samplesInCurrentBar = 0
                            maxPeakInBar = 0

                            if (currentBar % 20 == 0 || currentBar == numBars) {
                                Log.d(TAG, "Progress: bar=$currentBar/$numBars samples=$totalSamplesProcessed")
                            }
                        }
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    Log.d(TAG, "EOS received at bar=$currentBar/$numBars outputBuffers=$outputCount")
                    break
                }
            }
        }

        val decodeMs = System.currentTimeMillis() - decodeStart
        Log.d(TAG, "Decode loop: ${decodeMs}ms barsFilled=$currentBar/$numBars totalSamples=$totalSamplesProcessed outputBuffers=$outputCount")

        // Fill remaining bars if any
        val remaining = numBars - currentBar
        if (remaining > 0) {
            Log.d(TAG, "Filling $remaining remaining bars (padded)")
            for (i in currentBar until numBars) {
                amplitudes[i] = if (i > 0) amplitudes[i - 1] else 0.3f
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        // Normalize to 0.0 - 1.0 range (no artificial floor)
        val maxAmp = amplitudes.maxOrNull() ?: 1f
        val minBefore = amplitudes.minOrNull() ?: 0f
        if (maxAmp > 0f) {
            for (i in amplitudes.indices) {
                amplitudes[i] = (amplitudes[i] / maxAmp).coerceIn(0f, 1f)
            }
        }
        val minAfter = amplitudes.minOrNull() ?: 0f
        val maxAfter = amplitudes.maxOrNull() ?: 0f
        Log.d(TAG, "Normalize: raw min=${"%.4f".format(minBefore)} max=${"%.4f".format(maxAmp)} → normalized min=${"%.3f".format(minAfter)} max=${"%.3f".format(maxAfter)}")

        return amplitudes
    }
}
