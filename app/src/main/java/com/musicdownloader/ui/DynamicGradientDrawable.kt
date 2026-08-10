package com.musicdownloader.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt
import kotlin.math.sin

class DynamicGradientDrawable(
    private var topColor: Int = DEFAULT_TOP,
    private var midColor: Int = DEFAULT_MID,
    private var bottomColor: Int = DEFAULT_BOTTOM,
    private var darkVibrantColor: Int = DEFAULT_DARK_VIBRANT
) : Drawable() {

    private val evaluator = ArgbEvaluator()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    var currentPhase: Float = 0f
    private var energyModulator = 0f
    private var targetEnergy = 0f

    private var wavePaint: Paint? = null
    private var glowPaint: Paint? = null
    private var waveGradient: LinearGradient? = null
    private var glowGradient: LinearGradient? = null
    private var cachedWaveWidth = 0
    private var cachedWaveHeight = 0
    private val wavePath = Path()

    private var lastBlurRadius = -1f
    private var cachedGlowPaintFilter: BlurMaskFilter? = null

    private var baseTop = DEFAULT_TOP
    private var baseMid = DEFAULT_MID
    private var baseBottom = DEFAULT_BOTTOM
    private var baseDarkVibrant = DEFAULT_DARK_VIBRANT

    fun setColors(
        dominant: Int, vibrant: Int, muted: Int,
        darkVibrant: Int, @Suppress("UNUSED_PARAMETER") darkMuted: Int,
        @Suppress("UNUSED_PARAMETER") lightVibrant: Int,
        durationMs: Long
    ) {
        animateTo(
            top = blend(BASE_COLOR, dominant, 0.55f),
            mid = blend(BASE_COLOR, vibrant, 0.45f),
            bottom = blend(BASE_COLOR, muted, 0.35f),
            darkVibrant = blend(BASE_COLOR, darkVibrant, 0.40f),
            durationMs = durationMs
        )
    }

    fun setPrimaryGradient(primaryColor: Int, durationMs: Long) {
        val top = blend(BASE_COLOR, primaryColor, 0.45f)
        val mid = blend(BASE_COLOR, primaryColor, 0.25f)
        val bottom = BASE_COLOR
        animateTo(top, mid, bottom, blend(BASE_COLOR, primaryColor, 0.30f), durationMs)
    }

    fun setNeutralDark(durationMs: Long) {
        animateTo(0xFF1A1A24.toInt(), 0xFF131320.toInt(), BASE_COLOR, 0xFF0F0F18.toInt(), durationMs)
    }

    fun resetToDefault(durationMs: Long) {
        animateTo(DEFAULT_TOP, DEFAULT_MID, DEFAULT_BOTTOM, DEFAULT_DARK_VIBRANT, durationMs)
    }

    fun modulateByEnergy(energy: Float) {
        val newTarget = energy.coerceIn(0f, 1f)
        if (targetEnergy != newTarget) {
            targetEnergy = newTarget
            if (targetEnergy > 0f) {
                invalidateSelf()
            }
        }
    }

    private fun animateTo(top: Int, mid: Int, bottom: Int, darkVibrant: Int, durationMs: Long) {
        if (top == topColor && mid == midColor && bottom == bottomColor && darkVibrant == darkVibrantColor) return
        animator?.cancel()
        baseTop = top; baseMid = mid; baseBottom = bottom; baseDarkVibrant = darkVibrant
        val startTop = topColor; val startMid = midColor; val startBottom = bottomColor; val startDarkVibrant = darkVibrantColor
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                topColor = evaluator.evaluate(f, startTop, top) as Int
                midColor = evaluator.evaluate(f, startMid, mid) as Int
                bottomColor = evaluator.evaluate(f, startBottom, bottom) as Int
                darkVibrantColor = evaluator.evaluate(f, startDarkVibrant, darkVibrant) as Int
                rebuildWaveGradient(cachedWaveHeight)
                invalidateSelf()
            }
            start()
        }
    }

    private fun blend(base: Int, overlay: Int, fraction: Float): Int {
        val r = (Color.red(base) * (1f - fraction) + Color.red(overlay) * fraction).roundToInt()
        val g = (Color.green(base) * (1f - fraction) + Color.green(overlay) * fraction).roundToInt()
        val b = (Color.blue(base) * (1f - fraction) + Color.blue(overlay) * fraction).roundToInt()
        return Color.rgb(r, g, b)
    }

    private fun rebuildWaveGradient(height: Int) {
        if (height <= 0) return
        waveGradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(topColor, midColor, bottomColor, darkVibrantColor),
            null, Shader.TileMode.CLAMP
        )
        wavePaint?.shader = waveGradient

        // Glow gradient: colores del tema con más brillo (no blanco)
        val glowTop = blend(baseTop, 0xFFFFFFFF.toInt(), 0.3f)
        val glowMid = blend(baseMid, 0xFFFFFFFF.toInt(), 0.2f)
        val glowBottom = blend(baseBottom, 0xFFFFFFFF.toInt(), 0.15f)
        glowGradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(glowTop, glowMid, glowBottom, glowTop),
            null, Shader.TileMode.CLAMP
        )
        glowPaint?.shader = glowGradient
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()

        if (wavePaint == null || cachedWaveWidth != w.toInt() || cachedWaveHeight != h.toInt()) {
            wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            rebuildWaveGradient(h.toInt())
            cachedWaveWidth = w.toInt()
            cachedWaveHeight = h.toInt()
        }

        canvas.drawColor(BASE_COLOR)

        // 1. INTERPOLACIÓN ASIMÉTRICA DE ENERGÍA
        if (targetEnergy == 0f) {
            energyModulator += (targetEnergy - energyModulator) * 0.3f
        } else {
            energyModulator += (targetEnergy - energyModulator) * 0.05f
        }

        if (energyModulator < 0.001f && targetEnergy == 0f) {
            energyModulator = 0f
        }

        val factor = 0.7f + 0.3f * energyModulator
        topColor = blend(BASE_COLOR, baseTop, 0.55f * factor)
        midColor = blend(BASE_COLOR, baseMid, 0.45f * factor)
        bottomColor = blend(BASE_COLOR, baseBottom, 0.35f * factor)
        darkVibrantColor = blend(BASE_COLOR, baseDarkVibrant, 0.40f * factor)
        rebuildWaveGradient(cachedWaveHeight)

        // Si no hay energía, solo dibujar fondo (sin ola ni cálculos)
        if (energyModulator <= 0f && targetEnergy <= 0f) {
            canvas.drawColor(BASE_COLOR)
            return
        }

        // 2. FASE CONTINUA (sin ping-pong, solo avanza)
        val isMoving = energyModulator > 0f && targetEnergy > 0f
        if (isMoving) {
            currentPhase += 0.008f + (energyModulator * 0.010f)
        }

        // 3. PARÁMETROS DE ALTURA
        val midScreenY = h * 0.55f
        val floorLimitY = h * 0.65f
        val maxClimbHeight = h * 0.25f * energyModulator
        val baseY = (midScreenY + (h * 0.05f * (1f - energyModulator))).coerceAtMost(floorLimitY)

        wavePath.rewind()
        wavePath.moveTo(0f, h)

        var maxPeakY = baseY

        // 4. MUESTREO: ola viajera continua
        for (x in 0..w.toInt() step 3) {
            val fraction = x / w
            val angle = (fraction * Math.PI * 1.8) - currentPhase

            val combinedWave = sin(angle).toFloat() * energyModulator
            val calculatedY = baseY - (combinedWave * maxClimbHeight)
            val y = calculatedY.coerceAtMost(floorLimitY)

            if (y < maxPeakY) maxPeakY = y
            wavePath.lineTo(x.toFloat(), y)
        }

        wavePath.lineTo(w, h)
        wavePath.close()

        canvas.drawPath(wavePath, wavePaint!!)

        // 5. GLOW OPTIMIZADO Y MÁS INTENSO
        val peakAmplitude = (baseY - maxPeakY).coerceAtLeast(0f)
        val normalizedPeak = (peakAmplitude / (h * 0.12f)).coerceIn(0f, 1f)

        if (normalizedPeak > 0.01f) {
            val targetRadius = (20f + (normalizedPeak * 45f)).coerceAtLeast(1f)
            val roundedRadius = kotlin.math.round(targetRadius)

            if (roundedRadius != lastBlurRadius) {
                lastBlurRadius = roundedRadius
                cachedGlowPaintFilter = BlurMaskFilter(roundedRadius, BlurMaskFilter.Blur.NORMAL)
                glowPaint?.maskFilter = cachedGlowPaintFilter
            }

            val glowAlpha = (100 + (normalizedPeak * 155 * (0.5f + 0.5f * energyModulator))).toInt().coerceIn(0, 255)
            glowPaint?.alpha = glowAlpha
            canvas.drawPath(wavePath, glowPaint!!)
        } else {
            lastBlurRadius = -1f
            glowPaint?.maskFilter = null
            cachedGlowPaintFilter = null
        }

        // 6. CONTROL DEL BUCLE
        // Solo renderizar si hay energía activa o si la interpolación está en transición
        val needsRender = energyModulator > 0f || 
            (targetEnergy == 0f && energyModulator > 0f) ||
            kotlin.math.abs(targetEnergy - energyModulator) > 0.001f
        if (needsRender) {
            invalidateSelf()
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android API 24") override fun getOpacity(): Int = PixelFormat.OPAQUE
    override fun onBoundsChange(bounds: Rect) { super.onBoundsChange(bounds); invalidateSelf() }

    companion object {
        private const val BASE_COLOR_DARK = 0xFF0B0910.toInt()
        private const val DEFAULT_TOP_DARK = 0xFF241033.toInt()
        private const val DEFAULT_MID_DARK = 0xFF150D1E.toInt()
        private const val DEFAULT_BOTTOM_DARK = 0xFF0B0910.toInt()
        private const val DEFAULT_DARK_VIBRANT_DARK = 0xFF120D1C.toInt()

        private const val BASE_COLOR_LIGHT = 0xFFF5F3F7.toInt()
        private const val DEFAULT_TOP_LIGHT = 0xFFE8D5F5.toInt()
        private const val DEFAULT_MID_LIGHT = 0xFFF0E6FA.toInt()
        private const val DEFAULT_BOTTOM_LIGHT = 0xFFF5F3F7.toInt()
        private const val DEFAULT_DARK_VIBRANT_LIGHT = 0xFFE0D0F0.toInt()

        private var BASE_COLOR = BASE_COLOR_DARK
        private var DEFAULT_TOP = DEFAULT_TOP_DARK
        private var DEFAULT_MID = DEFAULT_MID_DARK
        private var DEFAULT_BOTTOM = DEFAULT_BOTTOM_DARK
        private var DEFAULT_DARK_VIBRANT = DEFAULT_DARK_VIBRANT_DARK

        fun setThemeMode(isDark: Boolean) {
            BASE_COLOR = if (isDark) BASE_COLOR_DARK else BASE_COLOR_LIGHT
            DEFAULT_TOP = if (isDark) DEFAULT_TOP_DARK else DEFAULT_TOP_LIGHT
            DEFAULT_MID = if (isDark) DEFAULT_MID_DARK else DEFAULT_MID_LIGHT
            DEFAULT_BOTTOM = if (isDark) DEFAULT_BOTTOM_DARK else DEFAULT_BOTTOM_LIGHT
            DEFAULT_DARK_VIBRANT = if (isDark) DEFAULT_DARK_VIBRANT_DARK else DEFAULT_DARK_VIBRANT_LIGHT
        }
    }
}
