package com.musicdownloader.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sqrt

object WaveformExtractor {

    /**
     * Extracts real audio waveform data from a file.
     * Returns a FloatArray of [numBars] values normalized 0.0-1.0.
     * Must be called from a background thread (Dispatchers.IO).
     */
    /**
     * Calculates optimal number of bars: 1 bar per 1.5 seconds of audio.
     */
    fun barsForDuration(durationMs: Long): Int {
        val durationSec = durationMs / 1000.0
        return (durationSec / 3.0).toInt().coerceIn(20, 200)
    }

    suspend fun extract(
        filePath: String,
        numBars: Int = 300
    ): FloatArray = withContext(Dispatchers.IO) {
        try {
            extractInternal(filePath, numBars)
        } catch (e: Exception) {
            FloatArray(numBars) { 0.3f } // fallback: flat bars
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
            extractor.release()
            return FloatArray(numBars) { 0.3f }
        }

        extractor.selectTrack(audioTrackIndex)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)

        // Calculate samples per bar
        val totalSamples = (durationUs / 1_000_000.0 * sampleRate).toLong()
        val samplesPerBar = (totalSamples / numBars).coerceAtLeast(1)

        // Configure decoder
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val amplitudes = FloatArray(numBars)
        val pcmBuffer = ByteBuffer.allocate(16384)
        var currentBar = 0
        var samplesInCurrentBar = 0
        var sumSquares = 0.0
        var isEOS = false

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

                    // Process PCM samples (16-bit = 2 bytes per sample)
                    while (outputBuffer.remaining() >= 2 && currentBar < numBars) {
                        val sample = outputBuffer.short.toFloat() / Short.MAX_VALUE
                        sumSquares += sample * sample
                        samplesInCurrentBar++

                        if (samplesInCurrentBar >= samplesPerBar) {
                            val rms = sqrt(sumSquares / samplesInCurrentBar).toFloat()
                            amplitudes[currentBar] = rms.coerceIn(0f, 1f)
                            currentBar++
                            samplesInCurrentBar = 0
                            sumSquares = 0.0
                        }
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }

        // Fill remaining bars if any
        for (i in currentBar until numBars) {
            amplitudes[i] = if (i > 0) amplitudes[i - 1] else 0.3f
        }

        codec.stop()
        codec.release()
        extractor.release()

        // Normalize to 0.1 - 1.0 range
        val maxAmp = amplitudes.maxOrNull() ?: 1f
        if (maxAmp > 0f) {
            for (i in amplitudes.indices) {
                amplitudes[i] = (0.1f + 0.9f * (amplitudes[i] / maxAmp))
            }
        }

        return amplitudes
    }
}
