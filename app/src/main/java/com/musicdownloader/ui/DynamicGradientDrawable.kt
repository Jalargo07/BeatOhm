package com.musicdownloader.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt

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
    private var cachedGradient: LinearGradient? = null
    private var lastBoundsWidth = 0
    private var lastBoundsHeight = 0

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

    fun resetToDefault(durationMs: Long) {
        animateTo(DEFAULT_TOP, DEFAULT_MID, DEFAULT_BOTTOM, DEFAULT_DARK_VIBRANT, durationMs)
    }

    private fun animateTo(top: Int, mid: Int, bottom: Int, darkVibrant: Int, durationMs: Long) {
        if (top == topColor && mid == midColor && bottom == bottomColor && darkVibrant == darkVibrantColor) return
        animator?.cancel()
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
                cachedGradient = null
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

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        val w = bounds.width()
        val h = bounds.height()
        if (cachedGradient == null || w != lastBoundsWidth || h != lastBoundsHeight) {
            cachedGradient = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(topColor, midColor, bottomColor, darkVibrantColor),
                null, Shader.TileMode.CLAMP
            )
            lastBoundsWidth = w
            lastBoundsHeight = h
        }
        paint.shader = cachedGradient
        canvas.drawRect(bounds, paint)
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
        private const val BASE_COLOR = 0xFF0B0910.toInt()
        private const val DEFAULT_TOP = 0xFF241033.toInt()
        private const val DEFAULT_MID = 0xFF150D1E.toInt()
        private const val DEFAULT_BOTTOM = 0xFF0B0910.toInt()
        private const val DEFAULT_DARK_VIBRANT = 0xFF120D1C.toInt()
    }
}
