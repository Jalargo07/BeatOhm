package com.musicdownloader.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs

class LevelCaptureProcessor : BaseAudioProcessor() {

    private var channelCount = 2
    private var prevSample: Short = 0
    private var bassLpState = 0f
    private var midLpBassCut = 0f
    private var midLpTrebleCut = 0f

    @Volatile
    var bands = FloatArray(BAND_COUNT) { 0f }
        private set

    private val smoothedBands = FloatArray(BAND_COUNT) { 0f }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val bytesAvailable = inputBuffer.remaining()
        val sampleCount = bytesAvailable / 2
        val step = if (channelCount > 0) channelCount else 1

        if (sampleCount >= 2) {
            val shortBuf = inputBuffer.asShortBuffer()
            val samples = ShortArray(sampleCount)
            shortBuf.get(samples)

            var trebleSum = 0.0
            var midSum = 0.0
            var bassSum = 0.0
            var processedSamples = 0

            for (i in 0 until sampleCount step step) {
                val sampleF = samples[i].toFloat()

                // 1. TREBLE: primera derivada
                val diff = abs(samples[i].toInt() - prevSample.toInt())
                trebleSum += diff
                prevSample = samples[i]

                // 2. MIDS: Pasa-banda IIR (HP ~300Hz + LP ~3.5kHz)
                midLpBassCut += HP_ALPHA * (sampleF - midLpBassCut)
                val voiceOnly = sampleF - midLpBassCut
                midLpTrebleCut += LP_ALPHA * (voiceOnly - midLpTrebleCut)
                midSum += abs(midLpTrebleCut)

                // 3. BASS: Low-pass estricto (<150Hz)
                bassLpState += BASS_LP_ALPHA * (sampleF - bassLpState)
                bassSum += abs(bassLpState)

                processedSamples++
            }

            if (processedSamples > 0) {
                var trebleRaw = (trebleSum / processedSamples / (Short.MAX_VALUE * 0.12f)).toFloat()
                var midsRaw = (midSum / processedSamples / (Short.MAX_VALUE * 0.15f)).toFloat()
                var bassRaw = (bassSum / processedSamples / (Short.MAX_VALUE * 0.18f)).toFloat()

                trebleRaw = if (trebleRaw > 0.03f) (trebleRaw - 0.03f) * 2.2f else 0f
                midsRaw = if (midsRaw > 0.05f) (midsRaw - 0.05f) * 1.8f else 0f
                bassRaw = if (bassRaw > 0.18f) (bassRaw - 0.18f) * 2.0f else 0f

                trebleRaw = trebleRaw.coerceIn(0f, 1f)
                midsRaw = midsRaw.coerceIn(0f, 1f)
                bassRaw = bassRaw.coerceIn(0f, 1f)

                // === SIDECHAIN: Atenuación proporcional continua ===

                // Bass vs Mids: si el bajo domina >1.5x, suprime voz
                val bassToMidRatio = if (midsRaw > 0.01f) bassRaw / midsRaw else 1f
                val bassToMidSuppression = if (bassToMidRatio > 1.5f) {
                    (1f - (bassToMidRatio - 1.5f) * 0.4f).coerceAtLeast(0.3f)
                } else 1f

                // Treble vs Mids: si los agudos dominan >1.5x, suprime voz
                val trebleToMidRatio = if (midsRaw > 0.01f) trebleRaw / midsRaw else 1f
                val trebleToMidSuppression = if (trebleToMidRatio > 1.5f) {
                    (1f - (trebleToMidRatio - 1.5f) * 0.3f).coerceAtLeast(0.4f)
                } else 1f

                midsRaw *= (bassToMidSuppression * trebleToMidSuppression)

                // Bass vs Treble: si el bajo explota >2x, limpia brillo
                val bassToTrebleRatio = if (trebleRaw > 0.01f) bassRaw / trebleRaw else 1f
                val bassToTrebleSuppression = if (bassToTrebleRatio > 2.0f) {
                    (1f - (bassToTrebleRatio - 2.0f) * 0.25f).coerceAtLeast(0.5f)
                } else 1f
                trebleRaw *= bassToTrebleSuppression

                // Voz vs Bajos: si la voz domina >1.8x, atenúa bajo
                val midToBassRatio = if (bassRaw > 0.01f) midsRaw / bassRaw else 1f
                val midToBassSuppression = if (midToBassRatio > 1.8f) {
                    (1f - (midToBassRatio - 1.8f) * 0.3f).coerceAtLeast(0.6f)
                } else 1f
                bassRaw *= midToBassSuppression

                val target = FloatArray(BAND_COUNT)
                target[0] = trebleRaw
                target[1] = midsRaw
                target[2] = bassRaw

                // Suavizado asimétrico por nodo
                for (i in 0 until BAND_COUNT) {
                    val currentAlpha = when (i) {
                        0 -> if (target[0] > smoothedBands[0]) 0.90f else 0.54f
                        2 -> if (target[2] > smoothedBands[2]) 0.95f else 0.66f
                        else -> 0.42f
                    }
                    smoothedBands[i] = currentAlpha * target[i] + (1f - currentAlpha) * smoothedBands[i]
                    bands[i] = smoothedBands[i]
                }
            }
        }

        val out = replaceOutputBuffer(bytesAvailable)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() {
        smoothedBands.fill(0f)
        bands.fill(0f)
        prevSample = 0
        bassLpState = 0f
        midLpBassCut = 0f
        midLpTrebleCut = 0f
    }

    companion object {
        const val BAND_COUNT = 3
        private const val BASS_LP_ALPHA = 0.02f  // Filtro estricto: solo <100Hz
        private const val HP_ALPHA = 0.08f
        private const val LP_ALPHA = 0.35f
    }
}
