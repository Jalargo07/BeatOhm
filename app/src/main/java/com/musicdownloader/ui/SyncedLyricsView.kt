package com.musicdownloader.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
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

    private var lines: List<LrcLine> = emptyList()
    private var plainText: String = ""
    private var isSynced = false
    private var currentIndex = -1
    private var scrollOffset = 0f
    private var targetScrollOffset = 0f
    private var scrollAnimator: ValueAnimator? = null

    // Touch state
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var lastMoveTime = 0L
    private var hasMoved = false
    private var isUserScrolling = false
    private var dragStartOffset = 0f
    private var velocity = 0f

    // Auto-scroll control
    private var autoScrollEnabled = true

    // Long press for close
    private var isLongPressing = false
    private var longPressRunnable: Runnable? = null
    private val longPressTimeout = 500L
    private val longPressThreshold = 20f * resources.displayMetrics.density

    // Swipe to close
    private var isSwiping = false
    private val swipeThreshold = 100f * resources.displayMetrics.density

    // Fling
    private val scroller = OverScroller(context)
    private var isFlinging = false
    private val flingRunnable = Runnable { computeFling() }

    // Auto-scroll zone: 1/3 of screen from center
    private val autoScrollZone: Float
        get() = height / 3f

    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val lineSpacing = 42f * scaledDensity
    private val maxTextWidthMargin = 16f * scaledDensity
    private val scrollTouchSlop = 8f * resources.displayMetrics.density

    private var cumulativeHeights: FloatArray = floatArrayOf()
    private var totalContentHeight = 0f
    private var needsRecompute = false

    private val normalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_surface)
        textSize = 18f * scaledDensity
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private val highlightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 18f * scaledDensity
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setShadowLayer(32f, 0f, 0f, 0xCCFFFFFF.toInt())
    }

    private fun precomputeHeights(maxWidth: Int) {
        val texts: List<String> = when {
            isSynced && lines.isNotEmpty() -> lines.map { it.text }
            !isSynced && plainText.isNotBlank() -> plainText.lines().map { it.trim() }
            else -> {
                cumulativeHeights = floatArrayOf()
                totalContentHeight = 0f
                return
            }
        }
        if (maxWidth <= 0) {
            cumulativeHeights = floatArrayOf()
            totalContentHeight = 0f
            return
        }
        val refLayout = StaticLayout.Builder.obtain(
            "A", 0, 1, normalPaint, maxWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1f)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .build()
        val gap = (lineSpacing - refLayout.height.toFloat()).coerceAtLeast(0f)
        cumulativeHeights = FloatArray(texts.size)
        var accumulated = 0f
        for (i in texts.indices) {
            val layout = StaticLayout.Builder.obtain(
                texts[i], 0, texts[i].length, normalPaint, maxWidth
            )
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .build()
            cumulativeHeights[i] = accumulated
            accumulated += layout.height + gap
        }
        totalContentHeight = if (texts.isNotEmpty()) accumulated - gap else 0f
    }

    fun setLyrics(lrcText: String) {
        lines = LrcParser.parse(lrcText)
        isSynced = lines.isNotEmpty()
        if (!isSynced) {
            plainText = lrcText
        }
        currentIndex = -1
        scrollOffset = 0f
        targetScrollOffset = 0f
        if (width > 0) {
            val maxTextWidth = width - paddingLeft - paddingRight - maxTextWidthMargin
            precomputeHeights(maxTextWidth.toInt())
        } else {
            needsRecompute = true
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && (needsRecompute || w != oldw)) {
            needsRecompute = false
            val maxTextWidth = w - paddingLeft - paddingRight - maxTextWidthMargin
            precomputeHeights(maxTextWidth.toInt())
            invalidate()
        }
    }

    fun updatePosition(positionMs: Long) {
        if (!isSynced || lines.isEmpty()) return
        val newIndex = findCurrentLine(positionMs)
        if (newIndex != currentIndex) {
            currentIndex = newIndex
            Log.d("LyricsScroll", "updatePosition: line=$currentIndex, autoScroll=$autoScrollEnabled, scrollOffset=$scrollOffset")
            // ALWAYS invalidate to update highlight, regardless of auto-scroll
            invalidate()
            // Auto-scroll only if enabled
            if (autoScrollEnabled) {
                val heights = cumulativeHeights.getOrElse(currentIndex) { currentIndex * lineSpacing }
                targetScrollOffset = heights + height / 2f - height / 3f
                Log.d("LyricsScroll", "  → AUTO-SCROLL to target=$targetScrollOffset (1/3 rest)")
                animateScroll()
            } else {
                Log.d("LyricsScroll", "  → NO SCROLL (auto-scroll disabled, but highlight updated)")
            }
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
        val localY = y - firstLineY
        if (cumulativeHeights.isNotEmpty()) {
            for (i in cumulativeHeights.size - 1 downTo 0) {
                if (localY >= cumulativeHeights[i]) return i
            }
            return 0
        }
        val index = ((y - firstLineY) / lineSpacing).toInt()
        return if (index in lines.indices) index else -1
    }

    private fun getMaxScroll(): Float {
        if (cumulativeHeights.isNotEmpty()) {
            return cumulativeHeights.last().coerceAtLeast(0f)
        }
        return ((lines.size - 1) * lineSpacing).coerceAtLeast(0f)
    }

    // ── Fling ──────────────────────────────────────────────────────────

    private fun startFling(velY: Float): Boolean {
        if (Math.abs(velY) < 50f) {
            Log.d("LyricsScroll", "FLING ignored: velocity too low ($velY)")
            return false
        }
        isFlinging = true
        val start = scrollOffset.toInt()
        val min = 0
        val max = getMaxScroll().toInt()
        Log.d("LyricsScroll", "FLING START: start=$start, velocity=$velY, max=$max")
        scroller.fling(
            0, start,
            0, velY.toInt(),
            0, 0, min, max
        )
        removeCallbacks(flingRunnable)
        postOnAnimation(flingRunnable)
        return true
    }

    private fun computeFling() {
        if (!isFlinging) return
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currY.toFloat()
            invalidate()
            postOnAnimation(flingRunnable)
        } else {
            Log.d("LyricsScroll", "FLING END: scrollOffset=$scrollOffset")
            isFlinging = false
            checkAutoScrollReactivation()
        }
    }

    private fun stopFling() {
        if (isFlinging) {
            scroller.forceFinished(true)
            isFlinging = false
            removeCallbacks(flingRunnable)
        }
    }

    // ── Auto-scroll zone ───────────────────────────────────────────────

    private fun checkAutoScrollReactivation() {
        if (currentIndex < 0) return
        val currentLineHeights = cumulativeHeights.getOrElse(currentIndex) { currentIndex * lineSpacing }
        val currentLineScreenPos = height / 2f - scrollOffset + currentLineHeights
        val restPoint = height / 3f
        val zoneTop = restPoint - autoScrollZone / 2f
        val zoneBottom = restPoint + autoScrollZone / 2f
        val inZone = currentLineScreenPos in zoneTop..zoneBottom
        Log.d("LyricsScroll", "CHECK AUTO-SCROLL: currentIndex=$currentIndex, scrollOffset=$scrollOffset, screenPos=$currentLineScreenPos, inZone=$inZone")
        if (inZone) {
            autoScrollEnabled = true
            targetScrollOffset = currentLineHeights + height / 2f - height / 3f
            Log.d("LyricsScroll", "  → REACTIVATING auto-scroll to target=$targetScrollOffset (1/3 rest)")
            animateScroll()
        } else {
            Log.d("LyricsScroll", "  → NOT in zone, staying at scrollOffset=$scrollOffset, autoScroll stays disabled")
        }
    }

    // ── Touch ──────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isSynced || lines.isEmpty()) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                Log.d("LyricsScroll", "ACTION_DOWN: y=${event.y}, scrollOffset=$scrollOffset, autoScroll=$autoScrollEnabled")
                downX = event.x
                downY = event.y
                lastY = event.y
                lastMoveTime = System.currentTimeMillis()
                hasMoved = false
                isUserScrolling = false
                isLongPressing = false
                isSwiping = false
                velocity = 0f
                dragStartOffset = scrollOffset
                scrollAnimator?.cancel()
                stopFling()
                // Disable auto-scroll on touch
                autoScrollEnabled = false
                Log.d("LyricsScroll", "  → AUTO-SCROLL DISABLED")

                longPressRunnable = Runnable {
                    if (!hasMoved) {
                        isLongPressing = true
                        Log.d("LyricsScroll", "LONG-PRESS detected")
                    }
                }
                postDelayed(longPressRunnable, longPressTimeout)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.y - downY
                val deltaX = event.x - downX
                val absDY = Math.abs(deltaY)
                val absDX = Math.abs(deltaX)

                if (absDY > scrollTouchSlop || absDX > scrollTouchSlop) {
                    hasMoved = true
                    longPressRunnable?.let { removeCallbacks(it) }
                }

                // Track velocity
                val now = System.currentTimeMillis()
                val dt = (now - lastMoveTime).coerceAtLeast(1)
                velocity = (event.y - lastY) / dt * 1000f // pixels/sec
                lastY = event.y
                lastMoveTime = now

                if (isLongPressing && absDY > scrollTouchSlop && absDY > absDX) {
                    isSwiping = true
                    isUserScrolling = false
                    Log.d("LyricsScroll", "SWIPE-DOWN detected (long press + drag)")
                } else if (absDY > scrollTouchSlop && absDY > absDX) {
                    isUserScrolling = true
                    scrollOffset = dragStartOffset - deltaY
                    Log.d("LyricsScroll", "SCROLL: deltaY=$deltaY, scrollOffset=$scrollOffset, velocity=$velocity")
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { removeCallbacks(it) }

                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    Log.d("LyricsScroll", "ACTION_CANCEL")
                    isSwiping = false
                    isUserScrolling = false
                    isLongPressing = false
                    return true
                }

                Log.d("LyricsScroll", "ACTION_UP: hasMoved=$hasMoved, isSwiping=$isSwiping, isUserScrolling=$isUserScrolling, velocity=$velocity")

                if (isSwiping) {
                    val deltaY = event.y - downY
                    Log.d("LyricsScroll", "  → CLOSE gesture, deltaY=$deltaY")
                    if (deltaY > swipeThreshold) {
                        onSwipeDown?.invoke()
                    }
                } else if (isUserScrolling) {
                    isUserScrolling = false
                    // Invert velocity: swipe up = negative velocity = should scroll DOWN (positive fling)
                    Log.d("LyricsScroll", "  → FLING with velocity=${-velocity} (inverted from $velocity)")
                    val flung = startFling(-velocity)
                    if (!flung) {
                        checkAutoScrollReactivation()
                    }
                } else if (!hasMoved) {
                    // Tap → seek to line; re-enable auto-scroll ONLY if tapping the current line
                    val index = getLineAtY(event.y)
                    Log.d("LyricsScroll", "  → TAP on line=$index (current=$currentIndex)")
                    if (index >= 0) {
                        if (index == currentIndex) {
                            autoScrollEnabled = true
                            Log.d("LyricsScroll", "  → AUTO-SCROLL RE-ENABLED (tap on current line)")
                        } else {
                            Log.d("LyricsScroll", "  → Tap on non-current line, seeking without re-enabling auto-scroll")
                        }
                        onLineClicked?.invoke(lines[index].timeMs)
                    }
                }
                isLongPressing = false
                isSwiping = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // ── Draw ───────────────────────────────────────────────────────────

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
            val y = firstLineY + cumulativeHeights.getOrElse(i) { i * lineSpacing }
            val blockBottom = firstLineY + cumulativeHeights.getOrElse(i + 1) { (i + 1) * lineSpacing }
            if (blockBottom < 0 || y > height) continue

            if (i == currentIndex) {
                drawCenteredText(canvas, lines[i].text, centerX, y, highlightPaint, maxTextWidth)
            } else {
                drawCenteredText(canvas, lines[i].text, centerX, y, normalPaint, maxTextWidth)
            }
        }
    }

    private fun drawPlainText(canvas: Canvas, centerX: Float, centerY: Float, maxTextWidth: Float) {
        val allLines = plainText.lines()
        normalPaint.alpha = 170
        val startY = centerY - totalContentHeight / 2f
        for (i in allLines.indices) {
            val y = startY + cumulativeHeights.getOrElse(i) { i * lineSpacing }
            val blockBottom = startY + cumulativeHeights.getOrElse(i + 1) { (i + 1) * lineSpacing }
            if (blockBottom < 0 || y > height) continue
            drawCenteredText(canvas, allLines[i].trim(), centerX, y, normalPaint, maxTextWidth)
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, y: Float, paint: TextPaint, maxWidth: Float) {
        val staticLayout = StaticLayout.Builder.obtain(
            text, 0, text.length, paint, maxWidth.toInt()
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1f)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .build()
        canvas.save()
        canvas.clipRect(paddingLeft.toFloat(), 0f, (width - paddingRight).toFloat(), height.toFloat())
        canvas.translate(paddingLeft.toFloat(), y - staticLayout.getLineBaseline(0))
        staticLayout.draw(canvas)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scrollAnimator?.cancel()
        longPressRunnable?.let { removeCallbacks(it) }
        stopFling()
    }
}
