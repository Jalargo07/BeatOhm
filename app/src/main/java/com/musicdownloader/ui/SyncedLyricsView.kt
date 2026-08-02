package com.musicdownloader.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.musicdownloader.R
import com.musicdownloader.lrc.LrcLine
import com.musicdownloader.lrc.LrcParser

class SyncedLyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onLineClicked: ((positionMs: Long) -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var isSwiping = false
    private val swipeThreshold = 100f * resources.displayMetrics.density

    private var lines: List<LrcLine> = emptyList()
    private var plainText: String = ""
    private var isSynced = false
    private var currentIndex = -1
    private var scrollOffset = 0f
    private var targetScrollOffset = 0f
    private var scrollAnimator: ValueAnimator? = null

    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val lineSpacing = 28f * scaledDensity
    private val maxTextWidthMargin = 16f * scaledDensity

    private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_surface)
        textSize = 18f * scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 32f * scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setShadowLayer(32f, 0f, 0f, 0xCCFFFFFF.toInt())
    }

    private val verticalPadding = 60f

    fun setLyrics(lrcText: String) {
        lines = LrcParser.parse(lrcText)
        isSynced = lines.isNotEmpty()
        if (!isSynced) {
            plainText = lrcText
        }
        currentIndex = -1
        scrollOffset = 0f
        targetScrollOffset = 0f
        invalidate()
    }

    fun updatePosition(positionMs: Long) {
        if (!isSynced || lines.isEmpty()) return
        val newIndex = findCurrentLine(positionMs)
        if (newIndex != currentIndex) {
            currentIndex = newIndex
            targetScrollOffset = currentIndex * lineSpacing
            animateScroll()
            invalidate()
        }
    }

    private fun findCurrentLine(positionMs: Long): Int {
        var result = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) {
                result = i
            } else break
        }
        return result
    }

    private fun animateScroll() {
        scrollAnimator?.cancel()
        val start = scrollOffset
        val diff = targetScrollOffset - start
        if (Math.abs(diff) < 1f) {
            scrollOffset = targetScrollOffset
            return
        }
        scrollAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scrollOffset = start + diff * it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    private fun getLineAtY(y: Float): Int {
        if (!isSynced || lines.isEmpty()) return -1
        val firstLineY = height / 2f - scrollOffset
        val index = ((y - firstLineY) / lineSpacing).toInt()
        return if (index in lines.indices) index else -1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isSynced || lines.isEmpty()) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isSwiping = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.y - downY
                val deltaX = event.x - downX
                if (Math.abs(deltaY) > swipeThreshold && Math.abs(deltaY) > Math.abs(deltaX)) {
                    isSwiping = true
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isSwiping) {
                    val deltaY = event.y - downY
                    if (deltaY > swipeThreshold) {
                        onSwipeDown?.invoke()
                    }
                } else {
                    val index = getLineAtY(event.y)
                    if (index >= 0) {
                        onLineClicked?.invoke(lines[index].timeMs)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val maxTextWidth = width - paddingLeft - paddingRight - maxTextWidthMargin

        if (!isSynced) {
            drawPlainText(canvas, centerX, centerY, maxTextWidth)
            return
        }

        normalPaint.alpha = 102
        highlightPaint.alpha = 255

        val firstLineY = centerY - scrollOffset

        for (i in lines.indices) {
            val y = firstLineY + i * lineSpacing
            if (y < -lineSpacing || y > height + lineSpacing) continue

            if (i == currentIndex) {
                drawCenteredText(canvas, lines[i].text, centerX, y, highlightPaint, maxTextWidth)
            } else {
                drawCenteredText(canvas, lines[i].text, centerX, y, normalPaint, maxTextWidth)
            }
        }
    }

    private fun drawPlainText(canvas: Canvas, centerX: Float, centerY: Float, maxTextWidth: Float) {
        val allLines = plainText.lines()
        val totalHeight = allLines.size * lineSpacing
        normalPaint.alpha = 170
        var y = centerY - totalHeight / 2f + lineSpacing
        for (line in allLines) {
            if (y < -lineSpacing || y > height + lineSpacing) {
                y += lineSpacing
                continue
            }
            drawCenteredText(canvas, line.trim(), centerX, y, normalPaint, maxTextWidth)
            y += lineSpacing
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, y: Float, paint: Paint, maxWidth: Float) {
        val measured = paint.measureText(text)
        if (measured <= maxWidth || measured <= 0f) {
            canvas.drawText(text, cx, y, paint)
            return
        }
        val originalSize = paint.textSize
        paint.textSize = originalSize * (maxWidth / measured)
        canvas.drawText(text, cx, y, paint)
        paint.textSize = originalSize
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scrollAnimator?.cancel()
    }
}
