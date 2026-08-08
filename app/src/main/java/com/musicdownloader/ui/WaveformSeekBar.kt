package com.musicdownloader.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import com.musicdownloader.R

class WaveformSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private val waveformCache = object : LruCache<String, FloatArray>(30) {
            override fun sizeOf(key: String, value: FloatArray) = 1
        }

        fun getCachedWaveform(key: String): FloatArray? = waveformCache.get(key)
        fun cacheWaveform(key: String, data: FloatArray) { waveformCache.put(key, data) }
        fun clearCache() { waveformCache.evictAll() }
    }

    var max: Int = 1000
        set(value) {
            field = value
            invalidate()
        }

    private var currentProgress = 0
    private var isDragging = false
    private var lastTouchX = 0f
    private var flingVelocity = 0f

    private val density = resources.displayMetrics.density
    private val barWidth = 12f * density
    private val barSpacing = 2f * density
    private val minBarHeight = 4f * density
    private val cornerRadius = 1.2f * density

    // Cursor position: fixed at 30% from left edge
    private val cursorPositionRatio = 0.3f

    // Paints
    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x29FFFFFF
    }

    // Paths (rebuilt each frame for scrolling)
    private val playedPath = Path()
    private val unplayedPath = Path()

    // Gradient (cached, screen-relative)
    private val primaryColor = ContextCompat.getColor(context, R.color.primary)
    private val accentEnd = ContextCompat.getColor(context, R.color.secondary)
    private var playedGradient: LinearGradient? = null

    private var bars: FloatArray = floatArrayOf()
    private var barCount = 0

    // Total waveform width in pixels
    private val totalWaveformWidth: Float
        get() = barCount * (barWidth + barSpacing)

    // Current scroll offset in pixels
    private var scrollOffset = 0f

    // Fling
    private val scroller = OverScroller(context).apply {
        setFriction(0.008f)
    }
    private var isFlinging = false
    private val flingRunnable = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                val newOffset = scroller.currX.toFloat().coerceIn(0f, maxScrollOffset())
                val deltaOffset = newOffset - scrollOffset
                scrollOffset = newOffset
                // Convert scroll delta to progress delta (inverted)
                val deltaProgress = (-deltaOffset / totalWaveformWidth * max).toInt()
                currentProgress = (currentProgress + deltaProgress).coerceIn(0, max)
                onProgressChanged?.invoke(currentProgress)
                invalidate()
                postOnAnimation(this)
            } else {
                isFlinging = false
                onProgressStop?.invoke(currentProgress)
            }
        }
    }

    var onProgressChanged: ((progress: Int) -> Unit)? = null
    var onProgressStop: ((progress: Int) -> Unit)? = null

    fun setWaveformData(data: FloatArray) {
        bars = data
        barCount = data.size
        playedPath.reset()
        unplayedPath.reset()
        scroller.forceFinished(true)
        isFlinging = false
        invalidate()
    }

    fun setProgress(progress: Int) {
        if (!isDragging && !isFlinging) {
            currentProgress = progress.coerceIn(0, max)
            updateScrollOffset()
            invalidate()
        }
    }

    private fun maxScrollOffset(): Float {
        return (totalWaveformWidth - width * (1f - cursorPositionRatio)).coerceAtLeast(0f)
    }

    private fun updateScrollOffset() {
        val progressRatio = currentProgress.toFloat() / max.coerceAtLeast(1)
        val cursorX = width * cursorPositionRatio
        scrollOffset = progressRatio * totalWaveformWidth - cursorX
        scrollOffset = scrollOffset.coerceIn(0f, maxScrollOffset())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        playedGradient = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(primaryColor, accentEnd),
            null,
            Shader.TileMode.CLAMP
        )
        playedPaint.shader = playedGradient
        updateScrollOffset()
    }

    private fun buildPaths() {
        if (width <= 0 || barCount == 0) return

        playedPath.rewind()
        unplayedPath.rewind()

        val centerY = height / 2f
        val maxHeight = height * 0.85f

        // Determine visible bar range for performance
        val visibleLeft = scrollOffset - (barWidth + barSpacing)
        val visibleRight = scrollOffset + width + (barWidth + barSpacing)
        val startBar = ((visibleLeft / (barWidth + barSpacing)).toInt()).coerceAtLeast(0)
        val endBar = ((visibleRight / (barWidth + barSpacing)).toInt() + 1).coerceAtMost(barCount)

        val progressRatio = currentProgress.toFloat() / max.coerceAtLeast(1)
        val progressWaveX = progressRatio * totalWaveformWidth

        for (i in startBar until endBar) {
            val x = i * (barWidth + barSpacing)
            val barHeight = minBarHeight + (maxHeight - minBarHeight) * bars[i]
            val top = centerY - barHeight / 2f

            // Played = bar position <= current progress position in waveform space
            val targetPath = if (x + barWidth <= progressWaveX) playedPath else unplayedPath
            targetPath.addRoundRect(
                x, top, x + barWidth, top + barHeight,
                cornerRadius, cornerRadius,
                Path.Direction.CW
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (barCount == 0 || bars.isEmpty()) return

        buildPaths()

        // Translate canvas so waveform scrolls under fixed cursor
        canvas.save()
        canvas.translate(-scrollOffset, 0f)

        // Layer 1: Unplayed bars (behind)
        canvas.drawPath(unplayedPath, unplayedPaint)

        // Layer 2: Played bars (front, with gradient)
        canvas.drawPath(playedPath, playedPaint)

        canvas.restore()

        // Draw cursor line at fixed position
        val cursorX = width * cursorPositionRatio
        val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            strokeWidth = 2f * density
            alpha = 0
        }
        canvas.drawLine(cursorX, 0f, cursorX, height.toFloat(), cursorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Cancel any active fling
                if (isFlinging) {
                    scroller.forceFinished(true)
                    isFlinging = false
                }
                isDragging = true
                lastTouchX = event.x
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaX = event.x - lastTouchX
                    lastTouchX = event.x
                    // Invert: drag right-to-left (negative deltaX) = advance (positive progress)
                    val deltaProgress = (-deltaX / totalWaveformWidth * max).toInt()
                    currentProgress = (currentProgress + deltaProgress).coerceIn(0, max)
                    onProgressChanged?.invoke(currentProgress)
                    updateScrollOffset()
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    val velocityX = event.x - lastTouchX
                    // Start fling if swipe was fast enough
                    if (Math.abs(velocityX) > 15f) {
                        startFling(-velocityX) // Invert: right-to-left swipe = positive fling direction
                    } else {
                        onProgressStop?.invoke(currentProgress)
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startFling(velocityX: Float) {
        val startOffset = scrollOffset.toInt()
        val minOffset = 0
        val maxOffset = maxScrollOffset().toInt()
        scroller.fling(
            startOffset,
            0,
            velocityX.toInt(),
            0,
            minOffset,
            maxOffset,
            0,
            0
        )
        isFlinging = true
        postOnAnimation(flingRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scroller.forceFinished(true)
        removeCallbacks(flingRunnable)
    }
}
