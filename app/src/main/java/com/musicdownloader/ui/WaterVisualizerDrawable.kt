package com.musicdownloader.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

class WaterVisualizerDrawable : Drawable() {

    private val evaluator = ArgbEvaluator()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    // --- Color state (same scheme as DynamicGradientDrawable) ---
    private var topColor: Int = DEFAULT_TOP
    private var midColor: Int = DEFAULT_MID
    private var bottomColor: Int = DEFAULT_BOTTOM
    private var darkVibrantColor: Int = DEFAULT_DARK_VIBRANT

    private var baseTop = DEFAULT_TOP
    private var baseMid = DEFAULT_MID
    private var baseBottom = DEFAULT_BOTTOM
    private var baseDarkVibrant = DEFAULT_DARK_VIBRANT

    // --- 5 anchor physics state ---
    private val anchorX = floatArrayOf(0.06f, 0.28f, 0.50f, 0.72f, 0.94f)
    private val anchorY = FloatArray(5)      // current Y (pixels)
    private val anchorVel = FloatArray(5)    // velocity (px/s)
    private val anchorTarget = FloatArray(5) // target Y (pixels)

    // --- Pre-allocated for surface path (no alloc per frame) ---
    private val anchorScreenX = FloatArray(5)
    private val anchorScreenY = FloatArray(5)
    private val segCp1x = FloatArray(4)
    private val segCp1y = FloatArray(4)
    private val segCp2x = FloatArray(4)
    private val segCp2y = FloatArray(4)

    private var active = false
    private val bands = FloatArray(5)        // incoming [0,1]
    private val bandSnapshot = FloatArray(5) // copied for rendering

    // --- Traveling ripple ---
    private var currentPhase = 0f
    private var lastPhaseNanos = 0L

    // --- Timing ---
    private var lastUpdateNanos = 0L
    private var lastInvalidateNanos = 0L

    // --- Waveform mask ---
    private var waveformMaskPath: Path? = null
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val xfermodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    // --- Dynamic water level ---
    private var currentWaterLevel = 0.565f  // matches BASELINE_RATIO initially

    // --- Reusable drawing objects (no alloc per frame) ---
    private var waterPaint: Paint? = null
    private var glowPaint: Paint? = null
    private var waterGradient: LinearGradient? = null
    private var glowGradient: LinearGradient? = null
    private var cachedWidth = 0
    private var cachedHeight = 0
    private val surfacePath = Path()
    private val glowPath = Path()
    private var cachedGradientColors = intArrayOf(0, 0, 0, 0)
    private var lastBlurRadius = -1f
    private var cachedGlowPaintFilter: BlurMaskFilter? = null

    private val frameRunnable = object : Runnable {
        override fun run() {
            invalidateSelf()
        }
    }

    // === Public API (drop-in for DynamicGradientDrawable) ===

    fun setColors(
        dominant: Int, vibrant: Int, muted: Int,
        darkVibrant: Int, @Suppress("UNUSED_PARAMETER") darkMuted: Int,
        @Suppress("UNUSED_PARAMETER") lightVibrant: Int,
        durationMs: Long
    ) {
        animateTo(
            top = blend(BASE_COLOR, dominant, 0.55f),
            mid = blend(BASE_COLOR, vibrant, 0.45f),
            bottom = blend(BASE_COLOR, muted, 0.35f),
            darkVibrant = blend(BASE_COLOR, darkVibrant, 0.40f),
            durationMs = durationMs
        )
    }

    fun setPrimaryGradient(primaryColor: Int, durationMs: Long) {
        val top = blend(BASE_COLOR, primaryColor, 0.80f)
        val mid = blend(BASE_COLOR, primaryColor, 0.70f)
        val bottom = blend(BASE_COLOR, primaryColor, 0.60f)
        animateTo(top, mid, bottom, blend(BASE_COLOR, primaryColor, 0.75f), durationMs)
    }

    fun setNeutralDark(durationMs: Long) {
        animateTo(0xFF1A1A24.toInt(), 0xFF131320.toInt(), BASE_COLOR, 0xFF0F0F18.toInt(), durationMs)
    }

    fun resetToDefault(durationMs: Long) {
        animateTo(DEFAULT_TOP, DEFAULT_MID, DEFAULT_BOTTOM, DEFAULT_DARK_VIBRANT, durationMs)
    }

    /**
     * 5 frequency bands [0,1], index 0 = high-frequency 8-20kHz (leftmost anchor),
     * index 4 = sub-bass 20-120Hz (rightmost anchor).
     */
    fun setBands(newBands: FloatArray) {
        val count = min(newBands.size, 5)
        for (i in 0 until count) {
            bands[i] = newBands[i].coerceIn(0f, 1f)
        }
        if (active) {
            scheduleNextFrame()
        }
    }

    /**
     * When false, anchors decay to 0 and ripple slows to cruise speed.
     */
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        scheduleNextFrame()
    }

    /**
     * Sets the waveform bar silhouette path for masking.
     * The water will only be visible inside this path.
     */
    fun setWaveformMask(path: Path?) {
        waveformMaskPath = path
        invalidateSelf()
    }

    // === Color animation ===

    private fun animateTo(top: Int, mid: Int, bottom: Int, darkVibrant: Int, durationMs: Long) {
        if (top == topColor && mid == midColor && bottom == bottomColor && darkVibrant == darkVibrantColor) return
        animator?.cancel()
        baseTop = top; baseMid = mid; baseBottom = bottom; baseDarkVibrant = darkVibrant
        val sTop = topColor; val sMid = midColor; val sBot = bottomColor; val sDV = darkVibrantColor
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                topColor = evaluator.evaluate(f, sTop, top) as Int
                midColor = evaluator.evaluate(f, sMid, mid) as Int
                bottomColor = evaluator.evaluate(f, sBot, bottom) as Int
                darkVibrantColor = evaluator.evaluate(f, sDV, darkVibrant) as Int
                rebuildGradients(cachedHeight)
                invalidateSelf()
            }
            start()
        }
    }

    // === Anchor physics (damped spring) ===

    private fun updatePhysics() {
        val now = System.nanoTime()
        val dtMs = if (lastUpdateNanos == 0L) 0L
        else min((now - lastUpdateNanos) / 1_000_000L, MAX_DT_MS)
        lastUpdateNanos = now
        if (dtMs == 0L) return
        val dtSec = dtMs / 1000f

        // Copy bands snapshot for rendering
        if (active) {
            for (i in 0 until 5) bandSnapshot[i] = bands[i]
        }

        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        val baselineY = currentWaterLevel
        val maxClimb = h * MAX_CLIMB_RATIO

        for (i in 0 until 5) {
            // Compute target: active uses band value, inactive = 0
            val bandVal = if (active) bandSnapshot[i] else 0f
            val targetDisplacement = bandVal * maxClimb
            anchorTarget[i] = baselineY - targetDisplacement

            // Spring integration: F = -k*(y - target) - c*v
            val displacement = anchorY[i] - anchorTarget[i]
            val springForce = -SPRING_K * displacement
            val dampingForce = -DAMPING_C * anchorVel[i]
            val accel = springForce + dampingForce

            anchorVel[i] += accel * dtSec
            anchorY[i] += anchorVel[i] * dtSec

            // Clamp within bounds
            val minY = (baselineY - maxClimb).coerceAtLeast(0f)
            anchorY[i] = anchorY[i].coerceIn(minY, h)
        }
    }

    // === Surface path via Catmull-Rom → cubic Bézier, single-pass ===

    private fun buildSurfacePath(w: Float, h: Float, rippleAmplitude: Float) {
        surfacePath.rewind()
        surfacePath.moveTo(0f, h)

        // Pre-compute screen positions (zero alloc)
        for (i in 0 until ANCHOR_COUNT) {
            anchorScreenX[i] = anchorX[i] * w
            anchorScreenY[i] = anchorY[i]
        }

        // Edge extrapolation points
        val leftY = anchorScreenY[0] + (anchorScreenY[0] - anchorScreenY[1]) * 0.1f
        val rightY = anchorScreenY[ANCHOR_COUNT - 1] +
            (anchorScreenY[ANCHOR_COUNT - 1] - anchorScreenY[ANCHOR_COUNT - 2]) * 0.1f

        // Compute Catmull-Rom control points for each segment
        for (i in 0 until ANCHOR_COUNT - 1) {
            val p0 = if (i > 0) anchorScreenY[i - 1] else anchorScreenY[0]
            val p1 = anchorScreenY[i]
            val p2 = anchorScreenY[i + 1]
            val p3 = if (i + 2 < ANCHOR_COUNT) anchorScreenY[i + 2] else anchorScreenY[ANCHOR_COUNT - 1]

            segCp1x[i] = anchorScreenX[i] + (anchorScreenX[i + 1] - anchorScreenX[i]) * TENSION
            segCp1y[i] = p1 + (p2 - p0) * TENSION * 0.5f
            segCp2x[i] = anchorScreenX[i + 1] - (anchorScreenX[i + 1] - anchorScreenX[i]) * TENSION
            segCp2y[i] = p2 - (p3 - p1) * TENSION * 0.5f
        }

        val addRipple = rippleAmplitude > 0.5f

        // Left edge: climb from bottom to surface
        surfacePath.lineTo(0f, leftY.coerceIn(0f, h))

        // Sample at fixed X intervals — Bézier polynomial + ripple in one pass
        var x = 0f
        while (x < w) {
            val y = sampleBezierY(x)
            val finalY = if (addRipple) {
                val fraction = x / w
                val ripple = sin(fraction * 2.0 * Math.PI * RIPPLE_WAVELENGTHS + currentPhase).toFloat()
                (y + ripple * rippleAmplitude).coerceIn(0f, h)
            } else {
                y.coerceIn(0f, h)
            }
            surfacePath.lineTo(x, finalY)
            x += STEP_SIZE_PX
        }

        // Right edge extrapolation + closing
        surfacePath.lineTo(w, rightY.coerceIn(0f, h))
        surfacePath.lineTo(w, h)
        surfacePath.close()
    }

    private fun sampleBezierY(x: Float): Float {
        if (x <= anchorScreenX[0]) return anchorScreenY[0]
        if (x >= anchorScreenX[ANCHOR_COUNT - 1]) return anchorScreenY[ANCHOR_COUNT - 1]

        for (i in 0 until ANCHOR_COUNT - 1) {
            if (x >= anchorScreenX[i] && x <= anchorScreenX[i + 1]) {
                return evalCubicBezierY(
                    x,
                    anchorScreenX[i], anchorScreenY[i],
                    segCp1x[i], segCp1y[i],
                    segCp2x[i], segCp2y[i],
                    anchorScreenX[i + 1], anchorScreenY[i + 1]
                )
            }
        }
        return anchorScreenY[ANCHOR_COUNT - 1]
    }

    private fun evalCubicBezierY(
        targetX: Float,
        x0: Float, y0: Float,
        cp1x: Float, cp1y: Float,
        cp2x: Float, cp2y: Float,
        x1: Float, y1: Float
    ): Float {
        val spanX = x1 - x0
        if (spanX < 1e-6f) return y0

        // Newton-Raphson: find t where Bx(t) = targetX
        var t = ((targetX - x0) / spanX).coerceIn(0f, 1f)
        repeat(4) {
            val omt = 1f - t
            val omt2 = omt * omt
            val t2 = t * t
            val bx = omt2 * omt * x0 + 3f * omt2 * t * cp1x + 3f * omt * t2 * cp2x + t2 * t * x1
            val dx = 3f * omt2 * (cp1x - x0) + 6f * omt * t * (cp2x - cp1x) + 3f * t2 * (x1 - cp2x)
            if (dx > 1e-6f) {
                t = (t - (bx - targetX) / dx).coerceIn(0f, 1f)
            }
        }

        // Evaluate Y at converged t
        val omt = 1f - t
        return omt * omt * omt * y0 + 3f * omt * omt * t * cp1y + 3f * omt * t * t * cp2y + t * t * t * y1
    }

    // === Traveling ripple ===

    private fun updateRipple(dtSec: Float) {
        val energy = averageEnergy()
        val speed = if (active) {
            RIPPLE_SPEED_BASE + energy * RIPPLE_SPEED_ENERGY_MULT
        } else {
            CRUISE_RIPPLE_SPEED
        }
        currentPhase += speed * dtSec
    }

    private fun averageEnergy(): Float {
        var sum = 0f
        for (i in 0 until 5) sum += bandSnapshot[i]
        return sum / 5f
    }

    // === Gradients ===

    private fun rebuildGradients(height: Int) {
        if (height <= 0) return
        if (topColor == cachedGradientColors[0] && midColor == cachedGradientColors[1] &&
            bottomColor == cachedGradientColors[2] && darkVibrantColor == cachedGradientColors[3]) return
        cachedGradientColors = intArrayOf(topColor, midColor, bottomColor, darkVibrantColor)
        waterGradient = buildWaterGradient(height)
        waterPaint?.shader = waterGradient
        glowGradient = buildGlowGradient(height)
        glowPaint?.shader = glowGradient
    }

    private fun buildWaterGradient(height: Int) = LinearGradient(
        0f, 0f, 0f, height.toFloat(),
        intArrayOf(topColor, midColor, bottomColor, darkVibrantColor),
        null, Shader.TileMode.CLAMP
    )

    private fun buildGlowGradient(height: Int): LinearGradient {
        val gTop = blend(topColor, 0xFFFFFFFF.toInt(), 0.15f)
        val gMid = blend(midColor, 0xFFFFFFFF.toInt(), 0.10f)
        val gBot = blend(bottomColor, 0xFFFFFFFF.toInt(), 0.08f)
        return LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(gTop, gMid, gBot, gTop),
            null, Shader.TileMode.CLAMP
        )
    }

    // === Ensure paint objects exist ===

    private fun ensurePaints(w: Int, h: Int) {
        if (waterPaint == null || cachedWidth != w || cachedHeight != h) {
            waterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            rebuildGradients(h)
            cachedWidth = w
            cachedHeight = h

            // Initialize anchor positions at baseline
            val baseline = h * BASELINE_RATIO
            currentWaterLevel = baseline
            for (i in 0 until 5) {
                anchorY[i] = baseline
                anchorVel[i] = 0f
            }
        }
    }

    // === Glow on crest ===

    private fun drawGlow(canvas: Canvas, h: Float) {
        val energy = averageEnergy()
        if (energy < 0.01f && !active) return
        val glowAlpha = (20 + (energy * 60 * if (active) 1f else 0.3f)).toInt().coerceIn(0, 70)
        if (glowAlpha <= 0) return

        glowPaint?.apply {
            alpha = glowAlpha
            if (lastBlurRadius != GLOW_BLUR_RADIUS) {
                cachedGlowPaintFilter = BlurMaskFilter(GLOW_BLUR_RADIUS, BlurMaskFilter.Blur.NORMAL)
                lastBlurRadius = GLOW_BLUR_RADIUS
            }
            maskFilter = cachedGlowPaintFilter
        }
        canvas.drawPath(surfacePath, glowPaint!!)
    }

    // === Frame scheduling ===

    private fun shouldContinueRendering(): Boolean {
        if (active) return true
        // Check if anchors are still settling (any velocity above threshold)
        for (i in 0 until 5) {
            if (abs(anchorVel[i]) > SETTLE_THRESHOLD) return true
        }
        return false
    }

    private fun scheduleNextFrame() {
        if (!shouldContinueRendering()) return
        unscheduleSelf(frameRunnable)
        val now = System.nanoTime()
        val elapsed = now - lastInvalidateNanos
        if (elapsed >= FRAME_INTERVAL_NANOS) {
            lastInvalidateNanos = now
            invalidateSelf()
        }
        val delayMs = ((FRAME_INTERVAL_NANOS - elapsed.coerceAtMost(FRAME_INTERVAL_NANOS)) / 1_000_000L)
            .coerceAtLeast(0L)
        scheduleSelf(frameRunnable, delayMs)
    }

    // === draw() main ===

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()

        ensurePaints(w.toInt(), h.toInt())
        canvas.drawColor(BASE_COLOR)

        updatePhysics()

        val dtMs: Long
        val now = System.nanoTime()
        dtMs = if (lastPhaseNanos == 0L) 0L
        else min((now - lastPhaseNanos) / 1_000_000L, MAX_DT_MS)
        lastPhaseNanos = now
        val dtSec = dtMs / 1000f

        updateRipple(dtSec)

        val energy = averageEnergy()

        // Dynamic water level: interpolate baseline from midY (sereno) to topY (pico)
        val midY = h * BASELINE_RATIO
        val topY = h * MIN_LEVEL_RATIO
        currentWaterLevel = midY - energy * (midY - topY)

        val rippleAmplitude = AVERAGE_AMPLITUDE * energy *
            (if (active) 1f else CRUISE_AMPLITUDE_FACTOR)

        buildSurfacePath(w, h, rippleAmplitude)

        val mask = waveformMaskPath
        if (mask != null && !mask.isEmpty) {
            // Masked mode: water only visible inside waveform bars
            val saveCount = canvas.saveLayer(0f, 0f, w, h, null)
            // Step 1: Draw mask destination (waveform silhouette)
            canvas.drawPath(mask, maskPaint)
            // Step 2: Draw water source with SRC_IN
            canvas.drawPath(surfacePath, xfermodePaint)
            canvas.restoreToCount(saveCount)
        } else {
            // Normal mode: water fills entire background
            canvas.drawPath(surfacePath, waterPaint!!)
        }

        drawGlow(canvas, h)

        if (shouldContinueRendering()) scheduleNextFrame()
    }

    // === Drawable overrides ===

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android API 24") override fun getOpacity(): Int = PixelFormat.OPAQUE
    override fun onBoundsChange(bounds: Rect) { super.onBoundsChange(bounds); invalidateSelf() }

    // === Utilities ===

    private fun blend(base: Int, overlay: Int, fraction: Float): Int {
        val r = (Color.red(base) * (1f - fraction) + Color.red(overlay) * fraction).toInt()
        val g = (Color.green(base) * (1f - fraction) + Color.green(overlay) * fraction).toInt()
        val b = (Color.blue(base) * (1f - fraction) + Color.blue(overlay) * fraction).toInt()
        return Color.rgb(r, g, b)
    }

    companion object {
        private const val BASE_COLOR_DARK = 0xFF0B0910.toInt()
        private const val DEFAULT_TOP_DARK = 0xFF241033.toInt()
        private const val DEFAULT_MID_DARK = 0xFF150D1E.toInt()
        private const val DEFAULT_BOTTOM_DARK = 0xFF0B0910.toInt()
        private const val DEFAULT_DARK_VIBRANT_DARK = 0xFF120D1C.toInt()

        private const val BASE_COLOR_LIGHT = 0xFFF5F3F7.toInt()
        private const val DEFAULT_TOP_LIGHT = 0xFFE8D5F5.toInt()
        private const val DEFAULT_MID_LIGHT = 0xFFF0E6FA.toInt()
        private const val DEFAULT_BOTTOM_LIGHT = 0xFFF5F3F7.toInt()
        private const val DEFAULT_DARK_VIBRANT_LIGHT = 0xFFE0D0F0.toInt()

        private var BASE_COLOR = BASE_COLOR_DARK
        private var DEFAULT_TOP = DEFAULT_TOP_DARK
        private var DEFAULT_MID = DEFAULT_MID_DARK
        private var DEFAULT_BOTTOM = DEFAULT_BOTTOM_DARK
        private var DEFAULT_DARK_VIBRANT = DEFAULT_DARK_VIBRANT_DARK

        // Physics constants
        private const val MAX_DT_MS = 50L
        private const val SPRING_K = 70f        // spring stiffness (rad/s²)
        private const val DAMPING_C = 14f        // damping coefficient
        private const val SETTLE_THRESHOLD = 0.5f // px/s

        // Geometry
        private const val BASELINE_RATIO = 0.565f  // water baseline ~56.5% from top
        private const val MAX_CLIMB_RATIO = 0.55f   // max climb = 55% of height
        private const val MIN_LEVEL_RATIO = 0.15f   // water can rise to 15% from top
        private const val TENSION = 0.3f             // Catmull-Rom tension
        private const val ANCHOR_COUNT = 5

        // Ripple
        private const val RIPPLE_SPEED_BASE = 1.2f
        private const val RIPPLE_SPEED_ENERGY_MULT = 2.5f
        private const val CRUISE_RIPPLE_SPEED = 0.4f
        private const val RIPPLE_WAVELENGTHS = 1.5f
        private const val AVERAGE_AMPLITUDE = 12f   // pixels base amplitude
        private const val CRUISE_AMPLITUDE_FACTOR = 0.25f

        // Rendering
        private const val FRAME_INTERVAL_NANOS = 16_666_666L // ~60fps
        private const val GLOW_BLUR_RADIUS = 8f
        private const val STEP_SIZE_PX = 4f

        fun setThemeMode(isDark: Boolean) {
            BASE_COLOR = if (isDark) BASE_COLOR_DARK else BASE_COLOR_LIGHT
            DEFAULT_TOP = if (isDark) DEFAULT_TOP_DARK else DEFAULT_TOP_LIGHT
            DEFAULT_MID = if (isDark) DEFAULT_MID_DARK else DEFAULT_MID_LIGHT
            DEFAULT_BOTTOM = if (isDark) DEFAULT_BOTTOM_DARK else DEFAULT_BOTTOM_LIGHT
            DEFAULT_DARK_VIBRANT = if (isDark) DEFAULT_DARK_VIBRANT_DARK else DEFAULT_DARK_VIBRANT_LIGHT
        }

        fun currentBaseColor(): Int = BASE_COLOR
    }
}
