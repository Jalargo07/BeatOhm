package com.musicdownloader.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.musicdownloader.lrc.LrcLine
import com.musicdownloader.lrc.LrcParser

class SyncedLyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var lines: List<LrcLine> = emptyList()
    private var plainText: String = ""
    private var isSynced = false
    private var currentIndex = -1
    private var scrollOffset = 0f
    private var targetScrollOffset = 0f
    private var scrollAnimator: ValueAnimator? = null

    private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }

    private val lineSpacing = 56f
    private val verticalPadding = 120f

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
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scrollOffset = start + diff * it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f

        if (!isSynced) {
            drawPlainText(canvas, centerX, centerY)
            return
        }

        val firstLineY = centerY - scrollOffset

        for (i in lines.indices) {
            val y = firstLineY + i * lineSpacing
            if (y < -lineSpacing || y > height + lineSpacing) continue

            val paint = if (i == currentIndex) highlightPaint else normalPaint
            val alpha = if (i == currentIndex) 1f else 0.5f
            paint.alpha = (alpha * 255).toInt()
            canvas.drawText(lines[i].text, centerX, y, paint)
        }
    }

    private fun drawPlainText(canvas: Canvas, centerX: Float, centerY: Float) {
        val allLines = plainText.lines()
        val totalHeight = allLines.size * lineSpacing
        var y = centerY - totalHeight / 2f + lineSpacing
        for (line in allLines) {
            if (y < -lineSpacing || y > height + lineSpacing) {
                y += lineSpacing
                continue
            }
            canvas.drawText(line.trim(), centerX, y, normalPaint)
            y += lineSpacing
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scrollAnimator?.cancel()
    }
}
