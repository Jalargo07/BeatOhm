package com.beatohm.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import com.beatohm.R

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
        private const val CURSOR_POSITION_RATIO = 0.3f
        private const val DRAG_SENSITIVITY = 12f
        private const val FLING_THRESHOLD = 15f
        private const val WATER_SPRING_K = 120f
        private const val WATER_DAMPING_C = 10f
    }

    var max: Int = 1000
        set(value) { field = value; invalidate() }

    var onProgressChanged: ((progress: Int) -> Unit)? = null
    var onProgressStop: ((progress: Int) -> Unit)? = null

    // === Estado ===
    private var currentProgress = 0
    private var isDragging = false
    private var isFlinging = false
    private var lastTouchX = 0f
    private var scrollOffset = 0f
    private var isPlaceholder = false

    // === Dimensiones ===
    private val density = resources.displayMetrics.density
    private val barWidth = 15f * density
    private val barSpacing = 6f * density
    private val minBarHeight = 3f * density
    private val cornerRadius = 3.5f * density
    private val totalWaveformWidth: Float get() = barCount * (barWidth + barSpacing)

    // === Datos ===
    private var bars: FloatArray = floatArrayOf()
    private var barCount = 0

    // === Paints ===
    private val primaryColor = ContextCompat.getColor(context, R.color.primary)
    private val accentEnd = ContextCompat.getColor(context, R.color.secondary)
    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x50C0C0C0 }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        strokeWidth = 2f * density
        alpha = 0
    }
    private var playedGradient: LinearGradient? = null
    private var playedColor = primaryColor
    private var isDarkMode = true

    // === Paths ===
    private val playedPath = Path()
    private val unplayedPath = Path()

    // === Water rendering ===
    private var waterBands = FloatArray(3) { 0f }
    private var waterAnchorY = FloatArray(3) { 0f }
    private var waterAnchorVel = FloatArray(3) { 0f }
    private var waterAnchorTarget = FloatArray(3) { 0f }
    private var waterActive = false
    private var waterBaselineRatio = 0.5f
    private var currentWaterLevel = 0.5f

    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waterMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val waterXfermodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    private val waterPath = Path()
    private var waterGradient: LinearGradient? = null

    private var waterTopColor = 0xFF1A6B5A.toInt()
    private var waterBottomColor = 0xFF0D3B30.toInt()

    // === Fling ===
    private val scroller = OverScroller(context).apply { setFriction(0.008f) }
    private val flingRunnable = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                val newOffset = scroller.currX.toFloat().coerceIn(0f, maxScrollOffset())
                val delta = newOffset - scrollOffset
                scrollOffset = newOffset
                val deltaProgress = (-delta / totalWaveformWidth * max).toInt()
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

    // === API pública ===

    fun setThemeMode(dark: Boolean) {
        isDarkMode = dark
        unplayedPaint.color = if (dark) 0x50C0C0C0 else 0x50606060
        playedGradient = null
        invalidate()
    }

    fun setDominantColor(color: Int) {
        playedColor = color
        val darkColor = android.graphics.Color.rgb(
            android.graphics.Color.red(color) / 3,
            android.graphics.Color.green(color) / 3,
            android.graphics.Color.blue(color) / 3
        )
        playedGradient = LinearGradient(0f, 0f, width.toFloat(), 0f,
            intArrayOf(darkColor, color), null, Shader.TileMode.CLAMP)
        playedPaint.shader = playedGradient
        invalidate()
    }

    fun setWaveformData(data: FloatArray) {
        bars = smoothAmplitudes(data)
        barCount = bars.size
        isPlaceholder = false
        unplayedPaint.color = 0xFFC0BFBC.toInt()
        unplayedPaint.alpha = 255
        resetPaths()
        scroller.forceFinished(true)
        invalidate()
    }

    fun setPlaceholder(numBars: Int = 120) {
        if (barCount > 0 && !isPlaceholder) return
        isPlaceholder = true
        unplayedPaint.alpha = 255
        bars = FloatArray(numBars) { 0.15f + java.util.Random(42).nextFloat() * 0.7f }
        barCount = numBars
        resetPaths()
        invalidate()
    }

    fun clearPlaceholder() {
        if (!isPlaceholder) return
        isPlaceholder = false
        bars = floatArrayOf()
        barCount = 0
        resetPaths()
        invalidate()
    }

    fun setProgress(progress: Int) {
        if (!isDragging && !isFlinging) {
            currentProgress = progress.coerceIn(0, max)
            updateScrollOffset()
            invalidate()
        }
    }

    fun setWaterBands(bands: FloatArray) {
        val count = kotlin.math.min(bands.size, 3)
        for (i in 0 until count) waterBands[i] = bands[i].coerceIn(0f, 1f)
        invalidate()
    }

    fun setWaterActive(active: Boolean) {
        waterActive = active
        invalidate()
    }

    fun setWaterColors(top: Int, bottom: Int) {
        waterTopColor = top
        waterBottomColor = bottom
        waterGradient = null
        invalidate()
    }

    // === Energy query ===

    fun getEnergyAtProgress(progress: Int): Float {
        if (bars.isEmpty() || max == 0) return 0f
        return getEnergyAtIndex(getProgressToBarIndex(progress))
    }

    fun getBarAtIndex(index: Int): Float =
        if (bars.isEmpty() || index < 0 || index >= bars.size) 0f else bars[index]

    fun getBarCount(): Int = bars.size

    fun getNearbyBars(progress: Int, count: Int): FloatArray {
        if (bars.isEmpty() || max == 0) return floatArrayOf()
        val center = getProgressToBarIndex(progress)
        val half = count / 2
        val start = (center - half).coerceAtLeast(0)
        val end = (center + half + 1).coerceAtMost(bars.size)
        return bars.copyOfRange(start, end)
    }

    fun getProgressToBarIndex(progress: Int): Int {
        if (bars.isEmpty() || max == 0) return 0
        return ((progress.toFloat() / max) * (bars.size - 1)).toInt().coerceIn(0, bars.size - 1)
    }

    fun getBarFraction(progress: Int): Float {
        if (bars.isEmpty() || max == 0) return 0f
        val pos = (progress.toFloat() / max) * (bars.size - 1)
        return pos - pos.toInt().toFloat()
    }

    fun getEnergyAtNextBar(progress: Int): Float {
        val next = (getProgressToBarIndex(progress) + 1).coerceAtMost(bars.size - 1)
        return getEnergyAtIndex(next)
    }

    private fun getEnergyAtIndex(index: Int): Float {
        if (bars.isEmpty()) return 0f
        var sum = 0f; var count = 0
        for (i in (index - 3)..(index + 3)) {
            if (i in bars.indices) { sum += bars[i]; count++ }
        }
        return if (count > 0) sum / count else 0f
    }

    // === Scroll ===

    private fun maxScrollOffset() = (totalWaveformWidth - width * (1f - CURSOR_POSITION_RATIO)).coerceAtLeast(0f)

    private fun updateScrollOffset() {
        val progressRatio = currentProgress.toFloat() / max.coerceAtLeast(1)
        scrollOffset = (progressRatio * totalWaveformWidth - width * CURSOR_POSITION_RATIO)
            .coerceIn(0f, maxScrollOffset())
    }

    private fun resetPaths() {
        playedPath.reset()
        unplayedPath.reset()
    }

    // === Smoothing ===

    private fun smoothAmplitudes(raw: FloatArray): FloatArray {
        return FloatArray(raw.size) { i ->
            val c = kotlin.math.sqrt(raw[i].toDouble()).toFloat().coerceIn(0f, 1f)
            val p = if (i > 0) kotlin.math.sqrt(raw[i - 1].toDouble()).toFloat() else c
            val n = if (i < raw.size - 1) kotlin.math.sqrt(raw[i + 1].toDouble()).toFloat() else c
            (p * 0.2f) + (c * 0.6f) + (n * 0.2f)
        }
    }

    // === Water physics ===

    private fun updateWaterPhysics() {
        val h = height.toFloat()
        if (h <= 0f) return

        val barMaxHeight = h * 0.85f
        val barCenterY = h / 2f

        var energy = 0f
        for (i in 0 until 3) energy += waterBands[i]
        energy /= 3f

        val sereneLevel = barCenterY + barMaxHeight * 0.25f
        val maxLevel = barCenterY - barMaxHeight * 0.30f

        for (i in 0 until 3) {
            val bandVal = if (waterActive) waterBands[i] else 0f
            waterAnchorTarget[i] = sereneLevel - bandVal * (sereneLevel - maxLevel)

            val displacement = waterAnchorY[i] - waterAnchorTarget[i]
            val springForce = -WATER_SPRING_K * displacement
            val dampingForce = -WATER_DAMPING_C * waterAnchorVel[i]
            waterAnchorVel[i] += (springForce + dampingForce) * 0.016f
            waterAnchorY[i] += waterAnchorVel[i] * 0.016f
            waterAnchorY[i] = waterAnchorY[i].coerceIn(maxLevel, sereneLevel)
        }
    }

    private fun buildWaterPath(w: Float, h: Float) {
        waterPath.rewind()
        waterPath.moveTo(0f, h)

        val anchorX = floatArrayOf(0.08f * w, 0.50f * w, 0.92f * w)

        val leftY = waterAnchorY[0] + (waterAnchorY[0] - waterAnchorY[1]) * 0.1f
        waterPath.lineTo(0f, leftY.coerceIn(0f, h))

        for (i in 0 until 2) {
            val p0 = if (i > 0) waterAnchorY[i - 1] else waterAnchorY[0]
            val p1 = waterAnchorY[i]
            val p2 = waterAnchorY[i + 1]
            val p3 = if (i + 2 < 3) waterAnchorY[i + 2] else waterAnchorY[2]

            val cp1y = p1 + (p2 - p0) * 0.15f
            val cp2y = p2 - (p3 - p1) * 0.15f

            var x = anchorX[i]
            val endX = anchorX[i + 1]
            while (x < endX) {
                val t = ((x - anchorX[i]) / (endX - anchorX[i])).coerceIn(0f, 1f)
                val omt = 1f - t
                val y = omt * omt * omt * p1 + 3f * omt * omt * t * cp1y + 3f * omt * t * t * cp2y + t * t * t * p2
                waterPath.lineTo(x, y.coerceIn(0f, h))
                x += 4f
            }
        }

        val rightY = waterAnchorY[2] + (waterAnchorY[2] - waterAnchorY[1]) * 0.1f
        waterPath.lineTo(w, rightY.coerceIn(0f, h))
        waterPath.lineTo(w, h)
        waterPath.close()
    }

    private fun hasWaterVelocity(): Boolean {
        for (v in waterAnchorVel) { if (kotlin.math.abs(v) > 0.5f) return true }
        return false
    }

    // === Geometría de barras ===

    private fun buildBarPaths() {
        if (width <= 0 || barCount == 0) return
        playedPath.rewind()
        unplayedPath.rewind()

        val centerY = height / 2f
        val maxHeight = height * 0.85f
        val visibleLeft = scrollOffset - (barWidth + barSpacing)
        val visibleRight = scrollOffset + width + (barWidth + barSpacing)
        val startBar = ((visibleLeft / (barWidth + barSpacing)).toInt()).coerceAtLeast(0)
        val endBar = ((visibleRight / (barWidth + barSpacing)).toInt() + 1).coerceAtMost(barCount)
        val progressWaveX = (currentProgress.toFloat() / max.coerceAtLeast(1)) * totalWaveformWidth

        for (i in startBar until endBar) {
            val x = i * (barWidth + barSpacing)
            val barH = minBarHeight + (maxHeight - minBarHeight) * bars[i]
            val top = centerY - barH / 2f
            val barEnd = x + barWidth

            when {
                barEnd <= progressWaveX ->
                    playedPath.addRoundRect(x, top, barEnd, top + barH, cornerRadius, cornerRadius, Path.Direction.CW)
                x >= progressWaveX ->
                    unplayedPath.addRoundRect(x, top, barEnd, top + barH, cornerRadius, cornerRadius, Path.Direction.CW)
                else -> {
                    val splitX = progressWaveX.coerceIn(x, barEnd)
                    playedPath.addRoundRect(x, top, splitX, top + barH, cornerRadius, cornerRadius, Path.Direction.CW)
                    unplayedPath.addRoundRect(splitX, top, barEnd, top + barH, cornerRadius, cornerRadius, Path.Direction.CW)
                }
            }
        }
    }

    // === Renderizado ===

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        playedGradient = LinearGradient(0f, 0f, w.toFloat(), 0f,
            intArrayOf(playedColor, accentEnd), null, Shader.TileMode.CLAMP)
        playedPaint.shader = playedGradient
        updateScrollOffset()

        val barCenterY = h / 2f
        val barMaxHeight = h * 0.85f
        val sereneY = barCenterY + barMaxHeight * 0.25f
        for (i in 0 until 3) {
            waterAnchorY[i] = sereneY
            waterAnchorVel[i] = 0f
        }
        waterGradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(waterTopColor, waterBottomColor), null, Shader.TileMode.CLAMP)
        waterPaint.shader = waterGradient
        waterPaint.alpha = 80
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (barCount == 0 || bars.isEmpty()) return

        buildBarPaths()
        updateWaterPhysics()
        buildWaterPath(width.toFloat(), height.toFloat())

        // CAPA 1+2: Waveform base + Progreso
        canvas.save()
        canvas.translate(-scrollOffset, 0f)
        canvas.drawPath(unplayedPath, unplayedPaint)
        canvas.drawPath(playedPath, playedPaint)
        canvas.restore()

        // CAPA 3: Agua enmascarada dentro de la silueta del waveform
        canvas.save()
        val fullWaveformSilhouette = Path().apply {
            addPath(playedPath)
            addPath(unplayedPath)
            offset(-scrollOffset, 0f)
        }
        canvas.clipPath(fullWaveformSilhouette)
        canvas.drawPath(waterPath, waterPaint)
        canvas.restore()

        // CAPA 4: Cursor
        val cursorX = width * CURSOR_POSITION_RATIO
        canvas.drawLine(cursorX, 0f, cursorX, height.toFloat(), cursorPaint)

        if (waterActive || hasWaterVelocity()) invalidate()
    }

    // === Touch handling ===

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isFlinging) { scroller.forceFinished(true); isFlinging = false }
                isDragging = true
                lastTouchX = event.x
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) { handleDragMove(event.x); return true }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) { handleDragEnd(event.x); return true }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleDragMove(x: Float) {
        val deltaX = (x - lastTouchX) * DRAG_SENSITIVITY
        lastTouchX = x
        val deltaProgress = (-deltaX / totalWaveformWidth * max).toInt()
        currentProgress = (currentProgress + deltaProgress).coerceIn(0, max)
        onProgressChanged?.invoke(currentProgress)
        updateScrollOffset()
        invalidate()
    }

    private fun handleDragEnd(x: Float) {
        isDragging = false
        val velocity = x - lastTouchX
        if (kotlin.math.abs(velocity) > FLING_THRESHOLD) {
            startFling(-velocity)
        } else {
            onProgressStop?.invoke(currentProgress)
        }
    }

    private fun startFling(velocityX: Float) {
        scroller.fling(scrollOffset.toInt(), 0, velocityX.toInt(), 0,
            0, maxScrollOffset().toInt(), 0, 0)
        isFlinging = true
        postOnAnimation(flingRunnable)
    }

    // === Silhouette paths (for WaterVisualizerDrawable masking) ===

    /**
     * Returns a copy of the played bar silhouette path, translated to the
     * parent layout's coordinate space (for masking in WaterVisualizerDrawable).
     */
    fun getPlayedSilhouettePath(@Suppress("UNUSED_PARAMETER") parentLayoutTop: Int): Path {
        buildBarPaths()
        val translatedPath = Path(playedPath)
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val parentLoc = IntArray(2)
        (parent as? android.view.View)?.getLocationInWindow(parentLoc)
        val offsetX = (loc[0] - parentLoc[0]).toFloat()
        val offsetY = (loc[1] - parentLoc[1]).toFloat()
        translatedPath.offset(-scrollOffset + offsetX, offsetY)
        return translatedPath
    }

    fun getUnplayedSilhouettePath(@Suppress("UNUSED_PARAMETER") parentLayoutTop: Int): Path {
        buildBarPaths()
        val translatedPath = Path(unplayedPath)
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val parentLoc = IntArray(2)
        (parent as? android.view.View)?.getLocationInWindow(parentLoc)
        val offsetX = (loc[0] - parentLoc[0]).toFloat()
        val offsetY = (loc[1] - parentLoc[1]).toFloat()
        translatedPath.offset(-scrollOffset + offsetX, offsetY)
        return translatedPath
    }

    fun getBothBarsSilhouettePath(parentLayoutTop: Int): Path {
        val combined = getPlayedSilhouettePath(parentLayoutTop)
        combined.addPath(getUnplayedSilhouettePath(parentLayoutTop))
        return combined
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scroller.forceFinished(true)
        removeCallbacks(flingRunnable)
    }
}
