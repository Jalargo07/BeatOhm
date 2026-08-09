package com.musicdownloader.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
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

/**
 * Fondo degradado diagonal del reproductor.
 * Recibe los 6 colores extraídos con Palette (dominant, vibrant, muted,
 * darkVibrant, darkMuted, lightVibrant), los mezcla con el fondo oscuro base
 * y anima la transición entre canciones.
 */
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

    // Cached drawing objects
    private var wavePaint: Paint? = null
    private var waveGradient: LinearGradient? = null
    private var cachedWaveWidth = 0
    private var cachedWaveHeight = 0
    private val wavePath = Path()

    // Base colors for energy modulation
    private var baseTop = DEFAULT_TOP
    private var baseMid = DEFAULT_MID
    private var baseBottom = DEFAULT_BOTTOM
    private var baseDarkVibrant = DEFAULT_DARK_VIBRANT
    private var energyModulator = 0f
    private var targetEnergy = 0f

    fun setColors(
        dominant: Int,
        vibrant: Int,
        muted: Int,
        darkVibrant: Int,
        darkMuted: Int,
        lightVibrant: Int,
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

    private fun animateTo(top: Int, mid: Int, bottom: Int, darkVibrant: Int, durationMs: Long) {
        if (top == topColor && mid == midColor && bottom == bottomColor && darkVibrant == darkVibrantColor) return
        animator?.cancel()
        baseTop = top
        baseMid = mid
        baseBottom = bottom
        baseDarkVibrant = darkVibrant
        val startTop = topColor
        val startMid = midColor
        val startBottom = bottomColor
        val startDarkVibrant = darkVibrantColor
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

    fun modulateByEnergy(energy: Float) {
        targetEnergy = energy.coerceIn(0f, 1f)
        invalidateSelf()
    }

    private fun rebuildWaveGradient(height: Int) {
        if (height <= 0) return
        waveGradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(topColor, midColor, bottomColor, darkVibrantColor),
            null, Shader.TileMode.CLAMP
        )
        wavePaint?.shader = waveGradient
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()

        if (wavePaint == null || cachedWaveWidth != w.toInt() || cachedWaveHeight != h.toInt()) {
            wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            rebuildWaveGradient(h.toInt())
            cachedWaveWidth = w.toInt()
            cachedWaveHeight = h.toInt()
        }

        canvas.drawColor(BASE_COLOR)

        energyModulator += (targetEnergy - energyModulator) * 0.15f
        if (kotlin.math.abs(targetEnergy - energyModulator) > 0.001f) invalidateSelf()

        val factor = 0.7f + 0.3f * energyModulator
        topColor = blend(BASE_COLOR, baseTop, 0.55f * factor)
        midColor = blend(BASE_COLOR, baseMid, 0.45f * factor)
        bottomColor = blend(BASE_COLOR, baseBottom, 0.35f * factor)
        darkVibrantColor = blend(BASE_COLOR, baseDarkVibrant, 0.40f * factor)
        rebuildWaveGradient(cachedWaveHeight)

        val waveHeight = h * 0.3f * (0.3f + energyModulator * 0.7f)
        val baseY = h * 0.45f

        wavePath.rewind()
        wavePath.moveTo(0f, h)

        for (x in 0..w.toInt() step 2) {
            val fraction = x / w
            val y = baseY - sin(fraction * Math.PI).toFloat() * waveHeight
            wavePath.lineTo(x.toFloat(), y)
        }

        wavePath.lineTo(w, h)
        wavePath.close()

        canvas.drawPath(wavePath, wavePaint!!)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android API 24")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        invalidateSelf()
    }

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
