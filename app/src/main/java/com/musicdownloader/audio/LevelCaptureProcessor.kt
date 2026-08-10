package com.musicdownloader.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.sqrt

class LevelCaptureProcessor : BaseAudioProcessor() {

    private var channelCount = 2

    @Volatile
    var bands = FloatArray(BAND_COUNT) { 0f }
        private set

    private val smoothedBands = FloatArray(BAND_COUNT) { 0f }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val bytesAvailable = inputBuffer.remaining()
        val sampleCount = bytesAvailable / 2

        if (sampleCount >= 2) {
            val shortBuf = inputBuffer.asShortBuffer()
            val samples = ShortArray(sampleCount)
            shortBuf.get(samples)

            var sumSquares = 0.0
            for (s in samples) {
                sumSquares += s.toDouble() * s.toDouble()
            }
            val rms = sqrt(sumSquares / sampleCount).toFloat()
            // Boost: divide by 30% of max instead of 100% to amplify typical music levels
            val normalizedEnergy = (rms / (Short.MAX_VALUE * 0.3f)).coerceIn(0f, 1f)

            var crossings = 0
            for (i in 1 until sampleCount) {
                if ((samples[i] >= 0) != (samples[i - 1] >= 0)) crossings++
            }
            val zcr = crossings.toFloat() / sampleCount

            val target = FloatArray(BAND_COUNT)
            target[4] = normalizedEnergy * (1f - zcr) * 1.1f
            target[3] = normalizedEnergy * (0.8f + zcr * 0.2f)
            target[2] = normalizedEnergy * 0.9f
            target[1] = normalizedEnergy * (0.6f + zcr * 0.4f) * 0.9f
            target[0] = normalizedEnergy * zcr * 1.2f

            val alpha = 0.35f
            for (i in 0 until BAND_COUNT) {
                smoothedBands[i] = alpha * target[i].coerceIn(0f, 1f) + (1f - alpha) * smoothedBands[i]
                bands[i] = smoothedBands[i]
            }
        }

        val out = replaceOutputBuffer(bytesAvailable)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() {
        smoothedBands.fill(0f)
        bands.fill(0f)
    }

    companion object {
        const val BAND_COUNT = 5
    }
}
