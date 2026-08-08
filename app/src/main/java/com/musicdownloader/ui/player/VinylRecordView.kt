package com.musicdownloader.ui.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.musicdownloader.R

class VinylRecordView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var artworkBitmap: Bitmap? = null
    private var isPlaying = false
    private var rotationAnimator: ValueAnimator? = null

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(28, 28, 28)
        style = Paint.Style.FILL
    }

    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(50, 50, 50)
        style = Paint.Style.STROKE
        strokeWidth = 1f
        alpha = 40
    }

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 60, 60)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val clipPath = android.graphics.Path()
    private val labelClipPath = android.graphics.Path()

    private val discRect = RectF()
    private val labelRect = RectF()

    private var iconDrawable: Drawable? = null

    private val grooveCount = 18

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val outerRadius = minOf(cx, cy)

        clipPath.reset()
        clipPath.addCircle(cx, cy, outerRadius, android.graphics.Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        discRect.set(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius)
        canvas.drawCircle(cx, cy, outerRadius, discPaint)

        val labelRadius = outerRadius / 3f
        val innerRadius = labelRadius + 4f
        val grooveStart = innerRadius + 6f
        val grooveEnd = outerRadius - 6f
        val grooveSpacing = (grooveEnd - grooveStart) / grooveCount

        for (i in 0 until grooveCount) {
            val r = grooveStart + i * grooveSpacing
            canvas.drawCircle(cx, cy, r, groovePaint)
        }

        canvas.drawCircle(cx, cy, outerRadius, edgePaint)

        val artwork = artworkBitmap
        if (artwork != null && !artwork.isRecycled) {
            labelRect.set(cx - labelRadius, cy - labelRadius, cx + labelRadius, cy + labelRadius)
            canvas.save()
            labelClipPath.reset()
            labelClipPath.addCircle(cx, cy, labelRadius, android.graphics.Path.Direction.CW)
            canvas.clipPath(labelClipPath)
            canvas.drawBitmap(artwork, null, labelRect, bitmapPaint)
            canvas.restore()
        } else {
            if (iconDrawable == null) {
                iconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_music_note)
            }
            iconDrawable?.let { drawable ->
                val size = (labelRadius * 1.2f).toInt()
                val left = (cx - size / 2f).toInt()
                val top = (cy - size / 2f).toInt()
                drawable.setBounds(left, top, left + size, top + size)
                drawable.draw(canvas)
            }
        }

        canvas.restore()
    }

    fun setArtwork(bitmap: Bitmap?) {
        artworkBitmap = bitmap
        invalidate()
    }

    fun setPlaying(playing: Boolean) {
        if (playing == isPlaying) return
        isPlaying = playing
        if (playing) {
            startRotation()
        } else {
            stopRotation()
        }
    }

    private fun startRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = ValueAnimator.ofFloat(rotation, rotation + 360f).apply {
            duration = 8000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { rotation = it.animatedValue as Float }
            start()
        }
    }

    private fun stopRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    fun cleanup() {
        stopRotation()
        artworkBitmap = null
        iconDrawable = null
    }
}
