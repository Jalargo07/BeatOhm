package com.musicdownloader.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WaveformExtractor {

    private const val TAG = "WaveformExtractor"

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
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val rawPeaks = FloatArray(numBars)

        val stepUs = durationUs / numBars
        val subSamplesPerBar = 3
        val subStepUs = stepUs / subSamplesPerBar

        for (bar in 0 until numBars) {
            val barStartUs = bar * stepUs
            
            var sumSquares = 0.0
            var sampleCount = 0L
            var maxPeak = 0f

            for (sub in 0 until subSamplesPerBar) {
                val targetUs = barStartUs + (sub * subStepUs)
                extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                val inputIndex = codec.dequeueInputBuffer(5_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: break
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize > 0) {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 5_000)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue
                    val shortBuf = outputBuffer.asShortBuffer()

                    while (shortBuf.hasRemaining()) {
                        val sample = abs(shortBuf.get().toInt()).toFloat()
                        if (sample > maxPeak) maxPeak = sample
                        sumSquares += sample.toDouble() * sample.toDouble()
                        sampleCount++
                        if (channelCount == 2 && shortBuf.hasRemaining()) {
                            shortBuf.get()
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }

            val rms = if (sampleCount > 0) sqrt(sumSquares / sampleCount).toFloat() else 0f
            rawPeaks[bar] = (0.6f * rms) + (0.4f * maxPeak)
        }

        codec.stop()
        codec.release()
        extractor.release()

        val absoluteMax = rawPeaks.maxOrNull() ?: 1f
        val contrastedPeaks = FloatArray(numBars) { i ->
            val norm = if (absoluteMax > 0f) rawPeaks[i] / absoluteMax else 0f
            norm.toDouble().pow(1.5).toFloat()
        }

        val smoothedPeaks = FloatArray(numBars) { i ->
            when (i) {
                0 -> contrastedPeaks[0]
                numBars - 1 -> contrastedPeaks[numBars - 1]
                else -> {
                    val smooth = (contrastedPeaks[i - 1] * 0.2f) +
                        (contrastedPeaks[i] * 0.6f) +
                        (contrastedPeaks[i + 1] * 0.2f)
                    smooth
                }
            }
        }

        return FloatArray(numBars) { i -> smoothedPeaks[i].coerceIn(0.08f, 1f) }
    }
}
