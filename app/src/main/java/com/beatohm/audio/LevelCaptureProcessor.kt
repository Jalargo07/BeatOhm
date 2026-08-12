package com.beatohm.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

class LevelCaptureProcessor : BaseAudioProcessor() {

    private var channelCount = 2
    private var sampleRate = 44100

    // Alfas dinámicos
    private var bassLpAlpha = 0.02f
    private var hpAlpha = 0.08f   // High-pass 300Hz
    private var lpAlpha = 0.35f   // Low-pass 3500Hz
    private var trebleHpAlpha = 0.08f // High-pass 4000Hz

    // Estados por banda (frecuencia, no por canal)
    private var bassLpState = 0f
    private var midLpBassCut = 0f
    private var midLpTrebleCut = 0f
    private var trebleLpState = 0f

    // --- Salida (5 anclas) ---
    @Volatile
    var bands = FloatArray(5) { 0f }
        private set

    private val smoothedBands = FloatArray(5) { 0f }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate

        val bassCutoff = 150f
        val hpCutoff = 300f
        val lpCutoff = 3500f
        val trebleCutoff = 4000f

        bassLpAlpha = (1.0 - Math.exp(-2.0 * Math.PI * bassCutoff / sampleRate)).toFloat()
        hpAlpha = (1.0 - Math.exp(-2.0 * Math.PI * hpCutoff / sampleRate)).toFloat()
        lpAlpha = (1.0 - Math.exp(-2.0 * Math.PI * lpCutoff / sampleRate)).toFloat()
        trebleHpAlpha = (1.0 - Math.exp(-2.0 * Math.PI * trebleCutoff / sampleRate)).toFloat()

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

            // Picos por banda (frecuencia)
            var treblePeak = 0f
            var midPeak = 0f
            var bassPeak = 0f

            var processedSamples = 0

            for (i in 0 until sampleCount step step) {
                val sampleL = samples[i].toFloat()
                val sampleR = if (i + 1 < sampleCount) samples[i + 1].toFloat() else sampleL

                // Mezclamos L+R para obtener una señal mono (capturar todo)
                val sampleMono = (sampleL + sampleR) / 2f
                val absMono = abs(sampleMono)

                // --- 1. TREBLE (>4kHz) ---
                trebleLpState += trebleHpAlpha * (sampleMono - trebleLpState)
                val trebleOnly = sampleMono - trebleLpState
                treblePeak = max(treblePeak, abs(trebleOnly))

                // --- 2. MIDS (300-3500Hz) ---
                midLpBassCut += hpAlpha * (sampleMono - midLpBassCut)
                val voiceOnly = sampleMono - midLpBassCut
                midLpTrebleCut += lpAlpha * (voiceOnly - midLpTrebleCut)
                midPeak = max(midPeak, abs(midLpTrebleCut))

                // --- 3. BASS (<150Hz) ---
                bassLpState += bassLpAlpha * (sampleMono - bassLpState)
                bassPeak = max(bassPeak, abs(bassLpState))

                processedSamples++
            }

            if (processedSamples >= 2) {
                // Normalizar
                var trebleRaw = (treblePeak / Short.MAX_VALUE).coerceIn(0f, 1f)
                var midRaw = (midPeak / Short.MAX_VALUE).coerceIn(0f, 1f)
                var bassRaw = (bassPeak / Short.MAX_VALUE).coerceIn(0f, 1f)

                // Umbrales (noise gate)
                trebleRaw = if (trebleRaw > 0.008f) trebleRaw else 0f
                midRaw = if (midRaw > 0.015f) midRaw else 0f
                bassRaw = if (bassRaw > 0.025f) bassRaw else 0f

                // Boosts (para que cada banda se vea con fuerza)
                trebleRaw = (trebleRaw * 1.6f).coerceIn(0f, 1f)
                midRaw = (midRaw * 1.3f).coerceIn(0f, 1f)   // Voz protagonista
                bassRaw = (bassRaw * 1.2f).coerceIn(0f, 1f) // Bombo con punch

                // --- Asignamos a las 5 anclas ---
                // Ancla 0 y 1 = Treble (izquierda)
                // Ancla 2 = Mids (centro)
                // Ancla 3 y 4 = Bass (derecha)
                val target = floatArrayOf(
                    trebleRaw, trebleRaw,  // 0 y 1
                    midRaw,                // 2
                    bassRaw, bassRaw       // 3 y 4
                )

                // Suavizado asimétrico por ancla
                for (i in 0 until 5) {
                    val currentAlpha = when (i) {
                        0,1 -> if (target[i] > smoothedBands[i]) 0.95f else 0.65f  // Agudos: subida rápida, bajada media
                        2   -> if (target[i] > smoothedBands[i]) 0.92f else 0.55f  // Voz: bajada más lenta
                        3,4 -> if (target[i] > smoothedBands[i]) 0.95f else 0.70f  // Graves: bajada rápida para que el bombo "explote"
                        else -> 0.50f
                    }
                    smoothedBands[i] = currentAlpha * target[i] + (1f - currentAlpha) * smoothedBands[i]
                    bands[i] = smoothedBands[i]
                }
            }
        }

        // Pasamos el audio sin modificar
        val out = replaceOutputBuffer(bytesAvailable)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() {
        smoothedBands.fill(0f)
        bands.fill(0f)
        bassLpState = 0f
        midLpBassCut = 0f
        midLpTrebleCut = 0f
        trebleLpState = 0f
    }
}
