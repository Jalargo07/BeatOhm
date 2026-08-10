package com.musicdownloader.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.hypot
import kotlin.math.max

/**
 * Halo radial a pantalla completa centrado en la posición de la carátula que
 * matchea el color dominante del álbum y se difumina hasta transparente en
 * los bordes de la pantalla.
 * Soporta transición animada entre colores al cambiar de canción.
 */
class GlowDrawable(private var glowColor: Int = DEFAULT_GLOW) : Drawable() {

    private val evaluator = ArgbEvaluator()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    private var centerXFrac = 0.5f
    private var centerYFrac = 0.28f
    private var endColor: Int = END_COLOR_DEFAULT

    fun setCenter(centerXFrac: Float, centerYFrac: Float) {
        this.centerXFrac = centerXFrac.coerceIn(0f, 1f)
        this.centerYFrac = centerYFrac.coerceIn(0f, 1f)
        invalidateSelf()
    }

    fun setEndColor(color: Int) {
        if (color == endColor) return
        endColor = color
        invalidateSelf()
    }

    fun setColor(color: Int, durationMs: Long) {
        if (color == glowColor) return
        animator?.cancel()
        val start = glowColor
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            addUpdateListener { anim ->
                glowColor = evaluator.evaluate(anim.animatedFraction, start, color) as Int
                invalidateSelf()
            }
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        val cx = bounds.left + bounds.width() * centerXFrac
        val cy = bounds.top + bounds.height() * centerYFrac

        val farthest = max(
            hypot((cx - bounds.left).toDouble(), (cy - bounds.top).toDouble()),
            max(
                hypot((bounds.right - cx).toDouble(), (cy - bounds.top).toDouble()),
                max(
                    hypot((cx - bounds.left).toDouble(), (bounds.bottom - cy).toDouble()),
                    hypot((bounds.right - cx).toDouble(), (bounds.bottom - cy).toDouble())
                )
            )
        ).toFloat()
        val outerRadius = farthest * 1.05f

        paint.shader = RadialGradient(
            cx,
            cy,
            outerRadius,
            intArrayOf(
                withAlpha(glowColor, OUTER_CENTER_ALPHA),
                withAlpha(glowColor, OUTER_MID_ALPHA),
                endColor
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, outerRadius, paint)

        val innerRadius = bounds.height() * INNER_RADIUS
        paint.shader = RadialGradient(
            cx,
            cy,
            innerRadius,
            intArrayOf(
                withAlpha(glowColor, INNER_CENTER_ALPHA),
                withAlpha(glowColor, INNER_MID_ALPHA),
                withAlpha(glowColor, 0x00)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, innerRadius, paint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android API 24")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        invalidateSelf()
    }

    companion object {
        private const val DEFAULT_GLOW = 0xFF9D35FF.toInt()

        private const val INNER_RADIUS = 0.30f

        private const val INNER_CENTER_ALPHA = 0x6D
        private const val INNER_MID_ALPHA = 0x2E

        private const val OUTER_CENTER_ALPHA = 0x3D
        private const val OUTER_MID_ALPHA = 0x19

        private const val END_COLOR_DEFAULT = 0xFF0B0910.toInt()
    }
}
