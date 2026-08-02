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

/**
 * Halo radial debajo del cover que matchea el color dominante del álbum.
 * Soporta transición animada entre colores al cambiar de canción.
 */
class GlowDrawable(private var glowColor: Int = DEFAULT_GLOW) : Drawable() {

    private val evaluator = ArgbEvaluator()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

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
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()

        // Halo exterior: suave y amplio, envuelve el cover
        val outerRadius = bounds.width() * OUTER_RADIUS
        paint.shader = RadialGradient(
            cx,
            cy,
            outerRadius,
            intArrayOf(
                withAlpha(glowColor, OUTER_CENTER_ALPHA),
                withAlpha(glowColor, OUTER_MID_ALPHA),
                withAlpha(glowColor, 0x00)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, outerRadius, paint)

        // Glow interno: brillante y ajustado al cover
        val innerRadius = bounds.width() * INNER_RADIUS
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

        // Radios de las capas (fracción del ancho del cover)
        private const val INNER_RADIUS = 0.55f
        private const val OUTER_RADIUS = 0.72f

        // Alphas de la capa interna (brillante y ajustada)
        private const val INNER_CENTER_ALPHA = 0x6D // 43%
        private const val INNER_MID_ALPHA = 0x2E // 18%

        // Alphas de la capa externa (suave y amplia)
        private const val OUTER_CENTER_ALPHA = 0x3D // 24%
        private const val OUTER_MID_ALPHA = 0x19 // 10%
    }
}
