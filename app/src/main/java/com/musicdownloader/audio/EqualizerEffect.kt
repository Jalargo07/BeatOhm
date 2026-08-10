package com.musicdownloader.audio

import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class EqualizerEffect : BaseAudioProcessor() {

    private val bandFrequenciesList = listOf(60, 230, 910, 3600, 14000)
    val bandFrequencies: List<Int> get() = bandFrequenciesList
    val bandCount: Int get() = BAND_COUNT
    val gainRangeMb: IntRange = GAIN_RANGE_MB

    private val gains = IntArray(BAND_COUNT)
    private val biquadFilters = Array(BAND_COUNT) { BiquadPeaking() }

    @Volatile
    private var inputSampleRate = 44100
    @Volatile
    private var inputChannelCount = 2
    @Volatile
    private var coefficientsDirty = true

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputSampleRate = inputAudioFormat.sampleRate
        inputChannelCount = inputAudioFormat.channelCount
        coefficientsDirty = true
        recalculateAllCoefficients()
        return inputAudioFormat
    }

    override fun onFlush() {
        for (filter in biquadFilters) {
            filter.reset()
        }
    }

    @Synchronized
    fun setBandGain(band: Int, gainMb: Int) {
        require(band in 0 until BAND_COUNT) { "Band $band out of range 0..${BAND_COUNT - 1}" }
        val clamped = gainMb.coerceIn(GAIN_RANGE_MB.first, GAIN_RANGE_MB.last)
        if (gains[band] == clamped) return
        gains[band] = clamped
        biquadFilters[band].updateCoefficients(
            bandFrequenciesList[band], inputSampleRate, clamped
        )
        coefficientsDirty = true
    }

    @Synchronized
    fun setGains(newGains: List<Int>) {
        require(newGains.size == BAND_COUNT) { "Expected $BAND_COUNT gains, got ${newGains.size}" }
        for (i in 0 until BAND_COUNT) {
            val clamped = newGains[i].coerceIn(GAIN_RANGE_MB.first, GAIN_RANGE_MB.last)
            if (gains[i] != clamped) {
                gains[i] = clamped
                biquadFilters[i].updateCoefficients(bandFrequenciesList[i], inputSampleRate, clamped)
            }
        }
        coefficientsDirty = true
    }

    @Synchronized
    fun resetToFlat() {
        for (i in 0 until BAND_COUNT) {
            gains[i] = 0
            biquadFilters[i].updateCoefficients(bandFrequenciesList[i], inputSampleRate, 0)
        }
        coefficientsDirty = true
    }

    @Synchronized
    fun getBandGain(band: Int): Int {
        require(band in 0 until BAND_COUNT) { "Band $band out of range 0..${BAND_COUNT - 1}" }
        return gains[band]
    }

    @Synchronized
    fun getGains(): List<Int> = gains.toList()

    private fun recalculateAllCoefficients() {
        for (i in 0 until BAND_COUNT) {
            biquadFilters[i].updateCoefficients(bandFrequenciesList[i], inputSampleRate, gains[i])
        }
        coefficientsDirty = false
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        if (coefficientsDirty) {
            recalculateAllCoefficients()
        }

        val out = replaceOutputBuffer(inputBuffer.remaining())
        val channelCount = inputChannelCount

        while (inputBuffer.hasRemaining()) {
            val sample = inputBuffer.get().toInt() and 0xFF or (inputBuffer.get().toInt() and 0xFF shl 8)
            val shortSample = (if (sample > 32767) sample - 65536 else sample).toShort()

            var processed = shortSample.toDouble()
            for (filter in biquadFilters) {
                processed = filter.process(processed, channelCount)
            }

            val clamped = processed.toInt().coerceIn(-32768, 32767).toShort()
            out.put((clamped.toInt() and 0xFF).toByte())
            out.put(((clamped.toInt() shr 8) and 0xFF).toByte())
        }
        out.flip()
    }

    companion object {
        const val BAND_COUNT = 5
        val GAIN_RANGE_MB: IntRange = -1200..1200
        internal const val Q = 1.0
    }
}

private class BiquadPeaking {
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0
    private var a1 = 0.0; private var a2 = 0.0
    private var x1 = 0.0; private var x2 = 0.0
    private var y1 = 0.0; private var y2 = 0.0
    private var gainMb = 0

    fun updateCoefficients(centerFreq: Int, sampleRate: Int, gainMb: Int) {
        this.gainMb = gainMb
        if (gainMb == 0) {
            b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
            return
        }
        val w0 = 2.0 * PI * centerFreq / sampleRate
        val alpha = sin(w0) / (2.0 * EqualizerEffect.Q)
        val a = 10.0.pow(gainMb / 4000.0)
        b0 = 1.0 + alpha * a
        b1 = -2.0 * cos(w0)
        b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        a1 = -2.0 * cos(w0)
        a2 = 1.0 - alpha / a
        b0 /= a0; b1 /= a0; b2 /= a0
        a1 /= a0; a2 /= a0
    }

    fun process(input: Double, @Suppress("UNUSED_PARAMETER") channelCount: Int): Double {
        if (gainMb == 0) return input
        val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = input
        y2 = y1; y1 = output
        return output
    }

    fun reset() {
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
    }
}
