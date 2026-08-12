package com.beatohm.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.beatohm.R

class VerticalSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.outline)
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.primary)
        style = Paint.Style.FILL
    }

    private val trackRect = RectF()
    private val fillRect = RectF()

    private var minProgress = 0
    private var maxProgress = 2000
    private var isProgrammaticUpdate = false
    var progress: Int = 1000
        set(value) {
            val clamped = value.coerceIn(minProgress, maxProgress)
            if (field != clamped) {
                field = clamped
                if (!isProgrammaticUpdate) {
                    onProgressChangedCallback?.invoke(mapProgressToGain(clamped))
                }
                invalidate()
            }
        }

    private val trackWidth = 8f
    private val thumbRadius = 10f

    var onProgressChangedCallback: ((Int) -> Unit)? = null

    fun setProgressWithoutCallback(progress: Int) {
        isProgrammaticUpdate = true
        this.progress = progress
        isProgrammaticUpdate = false
    }

    private val primaryColor = context.getColor(R.color.primary)
    private val secondaryColor = context.getColor(R.color.secondary)

    init {
        setOnTouchListener(object : OnTouchListener {
            private var downY = 0f
            private var lastProgress = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.y
                        lastProgress = progress
                        updateProgressFromTouch(event.y)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        updateProgressFromTouch(event.y)
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        return true
                    }
                }
                return false
            }

            private fun updateProgressFromTouch(y: Float) {
                val height = height.toFloat()
                if (height <= 0) return
                val ratio = 1f - (y / height)
                val newProgress = (minProgress + ratio * (maxProgress - minProgress)).toInt()
                progress = newProgress
            }
        })
    }

    private fun mapProgressToGain(progress: Int): Int {
        val ratio = (progress - minProgress).toFloat() / (maxProgress - minProgress)
        return (ratio * 2400 - 1200).toInt()
    }

    fun setGainRange(@Suppress("UNUSED_PARAMETER") minMb: Int, @Suppress("UNUSED_PARAMETER") maxMb: Int) {
        minProgress = 0
        maxProgress = 2000
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f

        trackRect.set(centerX - trackWidth / 2f, thumbRadius, centerX + trackWidth / 2f, h - thumbRadius)
        canvas.drawRoundRect(trackRect, trackWidth / 2f, trackWidth / 2f, trackPaint)

        val ratio = (progress - minProgress).toFloat() / (maxProgress - minProgress)
        val fillTop = h - thumbRadius - ratio * (h - 2 * thumbRadius)
        fillRect.set(trackRect.left, fillTop, trackRect.right, trackRect.bottom)

        fillPaint.shader = LinearGradient(
            centerX, fillTop, centerX, h - thumbRadius,
            primaryColor, secondaryColor, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(fillRect, trackWidth / 2f, trackWidth / 2f, fillPaint)

        val thumbY = h - thumbRadius - ratio * (h - 2 * thumbRadius)
        canvas.drawCircle(centerX, thumbY, thumbRadius, thumbPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 40
        val desiredHeight = 200
        val w = resolveSize(desiredWidth, widthMeasureSpec)
        val h = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }
}
