package com.musicdownloader.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WaveformExtractor {

    private const val TAG = "WaveformExtractor"
    private const val MAX_ITERATIONS = 1_000_000L

    /**
     * Calculates optimal number of bars: 1 bar per 2.5 seconds of audio.
     * Coarser resolution reduces waveform complexity while keeping it readable.
     */
    fun barsForDuration(durationMs: Long): Int {
        val durationSec = durationMs / 1000.0
        val bars = (durationSec / 2.5).toInt().coerceIn(30, 400)
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
            extractor.release()
            return FloatArray(numBars) { 0.3f }
        }

        extractor.selectTrack(audioTrackIndex)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        val totalFrames = (durationUs / 1_000_000.0 * sampleRate).toLong()
        val framesPerBar = (totalFrames / numBars).coerceAtLeast(1)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val rawPeaks = FloatArray(numBars)
        var currentBar = 0
        var framesInCurrentBar = 0L
        var maxPeakInBar = 0f
        var isEOS = false
        var iterations = 0L

        while (currentBar < numBars) {
            iterations++
            if (iterations > MAX_ITERATIONS) break

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
                    val shortBuf = outputBuffer.asShortBuffer()

                    while (shortBuf.hasRemaining() && currentBar < numBars) {
                        val sampleL = abs(shortBuf.get().toInt())
                        if (channelCount == 2 && shortBuf.hasRemaining()) {
                            shortBuf.get()
                        }

                        if (sampleL > maxPeakInBar) {
                            maxPeakInBar = sampleL.toFloat()
                        }

                        framesInCurrentBar++

                        if (framesInCurrentBar >= framesPerBar) {
                            rawPeaks[currentBar] = maxPeakInBar
                            currentBar++
                            framesInCurrentBar = 0
                            maxPeakInBar = 0f
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
            val lastVal = if (currentBar > 0) rawPeaks[currentBar - 1] else 1f
            for (i in currentBar until numBars) {
                rawPeaks[i] = lastVal
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val absoluteMax = rawPeaks.maxOrNull() ?: 1f

        return if (absoluteMax > 0f) {
            FloatArray(numBars) { i ->
                val normalized = rawPeaks[i] / absoluteMax
                normalized.coerceIn(0.08f, 1f)
            }
        } else {
            FloatArray(numBars) { 0.3f }
        }
    }
}
