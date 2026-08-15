package com.beatohm.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.PathParser

/**
 * Builds drawables for "color-aware" icon packs using the active theme's accent color.
 * Theme is runtime (Room DB), so colors are applied programmatically.
 *
 * Packs that use tint (Material, DarkNova, Heroic, future Lucide) do NOT use this —
 * they rely on resource IDs + app:tint instead.
 */
object IconPackDrawableFactory {

    private const val CACHE_SIZE = 50

    private const val GLOW_ALPHA = 0x33
    private const val GLASS_BG_ALPHA = 0x59
    private const val GLASS_BORDER_ALPHA = 0xCC
    private const val GLASS_SHINE_ALPHA = 0x59
    private const val DUOTONE_BG_ALPHA = 0x33

    const val PACK_NEON = "neon"
    const val PACK_GLASS = "glass"
    const val PACK_GRADIENT = "gradient"
    const val PACK_DUOTONE = "phosphor"

    // Pack ID → (iconKey → SVG pathData). Populated from *Paths objects at init.
    val PATH_REGISTRY: MutableMap<String, MutableMap<String, String>> = mutableMapOf(
        PACK_NEON to mutableMapOf(),
        PACK_GLASS to mutableMapOf(),
        PACK_GRADIENT to mutableMapOf(),
        PACK_DUOTONE to mutableMapOf(),
    )

    private val cache = LruCache<String, Drawable>(CACHE_SIZE)

    init {
        PATH_REGISTRY[PACK_NEON] = NeonPaths.PATHS.toMutableMap()
        PATH_REGISTRY[PACK_GLASS] = GlassPaths.PATHS.toMutableMap()
        PATH_REGISTRY[PACK_GRADIENT] = GradientPaths.PATHS.toMutableMap()
    }

    /**
     * Entry point. Returns null if [iconKey] is not supported by [packId].
     * [accentColor] is the main color for color-aware packs (neon, glass, gradient, duotone).
     */
    fun getDrawable(
        packId: String,
        iconKey: String,
        context: Context,
        accentColor: Int,
        secondaryColor: Int,
    ): Drawable? {
        val cacheKey = "$packId|$iconKey|$accentColor|$secondaryColor"
        cache.get(cacheKey)?.let { return it }

        val drawable = when (packId) {
            PACK_DUOTONE -> {
                val pair = PhosphorPaths.PATHS[iconKey] ?: return null
                buildDuotoneIcon(context, pair.first, pair.second, accentColor)
            }
            PACK_NEON -> {
                val pathData = PATH_REGISTRY[packId]?.get(iconKey) ?: return null
                buildNeonIcon(context, pathData, accentColor)
            }
            PACK_GLASS -> {
                val pathData = PATH_REGISTRY[packId]?.get(iconKey) ?: return null
                buildGlassIcon(context, pathData, accentColor, secondaryColor)
            }
            PACK_GRADIENT -> {
                val pathData = PATH_REGISTRY[packId]?.get(iconKey) ?: return null
                buildGradientIcon(context, pathData, accentColor, secondaryColor)
            }
            else -> null
        }
        drawable?.let { cache.put(cacheKey, it) }
        return drawable
    }

    fun clearCache() {
        cache.evictAll()
    }

    // ── Builders ─────────────────────────────────────────────────

    private fun buildNeonIcon(
        context: Context,
        pathData: String,
        accentColor: Int,
    ): Drawable? {
        val size = viewportSize(context)
        val path = scaledPath(pathData, densityScale(context)) ?: return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = accentColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fillPaint)

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = accentColor
            alpha = GLOW_ALPHA
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * densityScale(context)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, glowPaint)

        return buildBitmapDrawable(context, bitmap)
    }

    private fun buildGlassIcon(
        context: Context,
        pathData: String,
        accentColor: Int,
        secondaryColor: Int,
    ): Drawable? {
        val size = viewportSize(context)
        val path = scaledPath(pathData, densityScale(context)) ?: return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = densityScale(context)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = accentColor
            alpha = GLASS_BG_ALPHA
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = accentColor
            alpha = GLASS_BORDER_ALPHA
            style = Paint.Style.STROKE
            strokeWidth = 0.75f * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, borderPaint)

        canvas.save()
        canvas.translate(0f, -0.5f * scale)
        val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = secondaryColor
            alpha = GLASS_SHINE_ALPHA
            style = Paint.Style.STROKE
            strokeWidth = 0.5f * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, shinePaint)
        canvas.restore()

        return buildBitmapDrawable(context, bitmap)
    }

    private fun buildGradientIcon(
        context: Context,
        pathData: String,
        accentColor: Int,
        secondaryColor: Int,
    ): Drawable? {
        val size = viewportSize(context)
        val path = scaledPath(pathData, densityScale(context)) ?: return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f,
                size.toFloat(), size.toFloat(),
                accentColor, secondaryColor,
                Shader.TileMode.CLAMP,
            )
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, gradientPaint)

        return buildBitmapDrawable(context, bitmap)
    }

    private fun buildDuotoneIcon(
        context: Context,
        bgPathData: String,
        fgPathData: String,
        accentColor: Int,
    ): Drawable? {
        val size = viewportSize(context)
        val scale = densityScale(context)
        val bgPath = scaledPath(bgPathData, densityScale(context)) ?: return null
        val fgPath = scaledPath(fgPathData, densityScale(context)) ?: return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.save()
        canvas.translate(0.75f * scale, 0.75f * scale)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = accentColor
            alpha = DUOTONE_BG_ALPHA
            style = Paint.Style.FILL
        }
        canvas.drawPath(bgPath, bgPaint)
        canvas.restore()

        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = accentColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(fgPath, fgPaint)

        return buildBitmapDrawable(context, bitmap)
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun pathDataToPath(pathData: String): Path? {
        return try {
            PathParser.createPathFromPathData(pathData)
        } catch (_: Exception) {
            null
        }
    }

    private fun scaledPath(pathData: String, scale: Float): Path? {
        val path = pathDataToPath(pathData) ?: return null
        val matrix = Matrix().apply { setScale(scale, scale) }
        path.transform(matrix)
        return path
    }

    private fun viewportSize(context: Context): Int {
        val scale = densityScale(context)
        return (24 * scale).toInt()
    }

    private fun densityScale(context: Context): Float {
        return context.resources.displayMetrics.densityDpi / 160f
    }

    private fun buildBitmapDrawable(context: Context, bitmap: Bitmap): BitmapDrawable {
        return BitmapDrawable(context.resources, bitmap).apply {
            setTargetDensity(context.resources.displayMetrics.densityDpi)
        }
    }
}
