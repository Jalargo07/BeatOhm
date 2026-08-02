package com.musicdownloader.ui

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
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

    private val sd = resources.displayMetrics.scaledDensity
    private val lineGap = 8f * sd
    private val swipeTh = 100f * sd
    private val touchSlop = 8f * sd

    private var lines = listOf<LrcLine>()
    private var plainText = ""
    private var synced = false
    private var curIdx = -1
    private var curProg = 0f

    // Scroll state
    private var scroll = 0f
    private var autoMode = true

    // Touch state
    private var touching = false
    private var dragging = false
    private var swiping = false
    private var downY = 0f
    private var downX = 0f
    private var lastY = 0f
    private var dragDist = 0f

    // Cached positions
    private var posY = floatArrayOf()
    private var posH = intArrayOf()
    private var contentH = 0f
    private var dirty = true

    private val dimColor = ContextCompat.getColor(context, R.color.on_surface)
    private val hiColor = android.graphics.Color.WHITE
    private val evaluator = ArgbEvaluator()

    private val nPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dimColor; textSize = 20f * sd; textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val hPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = hiColor; textSize = 24f * sd; textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setShadowLayer(24f, 0f, 0f, 0xCCFFFFFF.toInt())
    }

    private val scroller = OverScroller(context).apply { setFriction(0.08f) }
    private var flinging = false

    private val tickRun = Runnable { tick() }
    private val resumeRun = Runnable {
        if (!touching && !flinging && nearCurrent()) {
            autoMode = true
            postTick()
        }
    }
    private val flingRun = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                scroll = scroller.currY.toFloat().coerceIn(0f, maxS())
                invalidate()
                postOnAnimation(this)
            } else {
                flinging = false
                if (!autoMode && nearCurrent()) {
                    autoMode = true
                    postTick()
                }
            }
        }
    }

    private fun sl(text: String, paint: TextPaint, w: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1f).setIncludePad(false).build()

    private fun lh(layout: StaticLayout): Int =
        if (layout.lineCount > 0) layout.getLineBottom(layout.lineCount - 1) - layout.getLineTop(0) else 0

    private fun recompute() {
        if (lines.isEmpty() || width == 0) {
            posY = floatArrayOf(); posH = intArrayOf(); contentH = 0f; dirty = false; return
        }
        val tw = (width - paddingLeft - paddingRight - 32f * sd).toInt()
        val sy = height * 0.45f
        val y = FloatArray(lines.size)
        val h = IntArray(lines.size)
        var cy = sy
        for (i in lines.indices) {
            val n = lh(sl(lines[i].text, nPaint, tw))
            val hh = lh(sl(lines[i].text, hPaint, tw))
            val slot = maxOf(n, hh) + lineGap.toInt()
            y[i] = cy; h[i] = slot; cy += slot
        }
        posY = y; posH = h
        contentH = cy - sy + height * 0.5f
        dirty = false
    }

    private fun maxS() = (contentH - height).coerceAtLeast(0f)

    private fun lineCenter(idx: Int): Float {
        if (idx < 0 || idx >= posY.size) return 0f
        return posY[idx] + posH[idx] / 2f
    }

    private fun targetForCurrent(): Float {
        if (curIdx < 0 || curIdx >= posY.size) return scroll
        return (lineCenter(curIdx) - height * 0.5f).coerceIn(0f, maxS())
    }

    private fun postTick() {
        removeCallbacks(tickRun)
        postOnAnimation(tickRun)
    }

    private fun tick() {
        if (!autoMode || touching || flinging || dirty) return
        if (curIdx >= 0) {
            val target = targetForCurrent()
            val d = target - scroll
            if (Math.abs(d) < 1f) {
                scroll = target
            } else {
                scroll += d * 0.15f
            }
        }
        invalidate()
        postOnAnimation(tickRun)
    }

    private fun nearCurrent(): Boolean {
        if (curIdx < 0 || curIdx >= posY.size) return false
        val lc = lineCenter(curIdx)
        val vc = scroll + height * 0.5f
        return Math.abs(lc - vc) < posH[curIdx] * 2f
    }

    private fun startFling(vel: Int) {
        flinging = true
        scroller.fling(0, scroll.toInt(), 0, vel, 0, 0, 0, maxS().toInt())
        postOnAnimation(flingRun)
    }

    fun setLyrics(lrcText: String) {
        lines = LrcParser.parse(lrcText)
        synced = lines.isNotEmpty()
        if (!synced) plainText = lrcText
        curIdx = -1; curProg = 0f
        scroll = 0f; autoMode = true
        touching = false; dragging = false; flinging = false
        scroller.forceFinished(true)
        removeCallbacks(tickRun); removeCallbacks(resumeRun); removeCallbacks(flingRun)
        dirty = true; requestLayout()
    }

    fun updatePosition(positionMs: Long) {
        if (!synced || lines.isEmpty()) return
        val ni = findLine(positionMs) ?: return
        val ls = lines[ni].timeMs
        val le = if (ni + 1 < lines.size) lines[ni + 1].timeMs else ls + 4000L
        curProg = ((positionMs - ls).toFloat() / (le - ls).coerceAtLeast(1L)).coerceIn(0f, 1f)
        curIdx = ni
        if (autoMode) postTick()
        invalidate()
    }

    private fun findLine(ms: Long): Int? {
        var r: Int? = null
        for (i in lines.indices) { if (lines[i].timeMs <= ms) r = i else break }
        return r
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh); dirty = true; recompute()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed || dirty) {
            recompute()
            if (autoMode && curIdx >= 0) {
                scroll = targetForCurrent()
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!synced || lines.isEmpty()) return super.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true; dragging = false; swiping = false
                autoMode = false
                scroller.forceFinished(true); flinging = false
                removeCallbacks(tickRun); removeCallbacks(resumeRun); removeCallbacks(flingRun)
                downY = e.y; downX = e.x; lastY = e.y; dragDist = 0f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastY - e.y
                val ady = Math.abs(e.y - downY)
                val adx = Math.abs(e.x - downX)
                if (!dragging && ady > touchSlop && ady > adx) dragging = true
                if (ady > swipeTh && ady > adx * 1.5f) swiping = true
                if (dragging) {
                    scroll = (scroll + dy).coerceIn(0f, maxS())
                    dragDist += Math.abs(dy)
                    lastY = e.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                if (swiping && (e.y - downY) > swipeTh) {
                    onSwipeDown?.invoke(); return true
                }
                if (dragging && dragDist < touchSlop) {
                    val ti = lineAt(e.y)
                    if (ti == curIdx && ti >= 0) {
                        autoMode = true; postTick()
                    } else if (ti != null) {
                        onLineClicked?.invoke(lines[ti].timeMs)
                    }
                } else if (dragging) {
                    val vel = -(e.y - downY)
                    if (Math.abs(vel) > 50f) startFling(vel.toInt())
                    else {
                        autoMode = nearCurrent()
                        if (autoMode) postTick()
                        else { removeCallbacks(resumeRun); postDelayed(resumeRun, 4000) }
                    }
                } else {
                    val ti = lineAt(e.y)
                    if (ti == curIdx && ti >= 0) {
                        autoMode = true; postTick()
                    } else if (ti != null) {
                        onLineClicked?.invoke(lines[ti].timeMs)
                    } else {
                        autoMode = nearCurrent()
                        if (autoMode) postTick()
                    }
                }
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun lineAt(y: Float): Int? {
        if (dirty) return null
        for (i in posY.indices) {
            val vy = posY[i] - scroll
            if (y >= vy && y <= vy + posH[i]) return i
        }
        return null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!synced) { drawPlain(canvas); return }
        if (dirty || lines.isEmpty()) return

        val cx = width / 2f
        val tw = (width - paddingLeft - paddingRight - 32f * sd).toInt()

        for (i in lines.indices) {
            val vy = posY[i] - scroll
            val slot = posH[i]
            if (vy + slot < -100 || vy > height + 100) continue

            val p: TextPaint
            val l: StaticLayout

            if (i == curIdx) {
                p = TextPaint(hPaint)
                p.color = evaluator.evaluate(curProg, dimColor, hiColor) as Int
                p.alpha = (128 + 127 * curProg).toInt()
                l = sl(lines[i].text, p, tw)
            } else {
                p = TextPaint(nPaint)
                p.alpha = if (i < curIdx) 180 else 140
                l = sl(lines[i].text, p, tw)
            }

            val th = lh(l)
            val pad = ((slot - lineGap.toInt()) - th) / 2f

            canvas.save()
            canvas.translate(cx - tw / 2f, vy + pad)
            l.draw(canvas)
            canvas.restore()
        }
    }

    private fun drawPlain(canvas: Canvas) {
        val cx = width / 2f
        val tw = (width - paddingLeft - paddingRight - 32f * sd).toInt()
        nPaint.alpha = 170
        var y = 40f * sd
        for (line in plainText.lines()) {
            val l = sl(line.trim(), nPaint, tw)
            val h = lh(l) + lineGap.toInt()
            if (y + h > 0 && y < height) {
                canvas.save(); canvas.translate(cx - tw / 2f, y); l.draw(canvas); canvas.restore()
            }
            y += h
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scroller.forceFinished(true)
        removeCallbacks(tickRun); removeCallbacks(resumeRun); removeCallbacks(flingRun)
    }
}
