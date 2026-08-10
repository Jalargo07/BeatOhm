package com.musicdownloader.ui.player

import android.graphics.Bitmap
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.musicdownloader.R
import com.musicdownloader.ui.ThemeManager

/**
 * Dynamically adapts the player layout based on the current theme's player style.
 * Works with the existing fragment_player.xml to apply style variations without
 * requiring separate layout files.
 */
object PlayerLayoutManager {

    private const val BASE_SIZE_DP = 280

    var currentStyle: String = ThemeManager.currentPlayerLayout
        set(value) {
            field = value
            currentScaleFactor = when (value) {
                "compact" -> 180f / BASE_SIZE_DP
                "vinyl" -> 240f / BASE_SIZE_DP
                else -> 260f / BASE_SIZE_DP
            }
        }

    var currentScaleFactor = 1f
        private set

    private var vinylRecordView: VinylRecordView? = null
    private var lastVinylArtwork: Bitmap? = null

    /**
     * Apply the current theme's player style to the player layout.
     * Called from PlayerFragment.onViewCreated() after binding is set up.
     */
    fun applyStyle(root: ConstraintLayout) {
        currentStyle = ThemeManager.currentPlayerLayout
        when (currentStyle) {
            "compact" -> applyCompactStyle(root)
            "vinyl" -> applyVinylStyle(root)
            else -> applyClassicStyle(root)
        }
    }

    private fun applyClassicStyle(root: ConstraintLayout) {
        currentScaleFactor = 260f / BASE_SIZE_DP
        removeVinylView(root)

        root.findViewById<View>(R.id.cover_container)?.let { cover ->
            cover.scaleX = currentScaleFactor
            cover.scaleY = currentScaleFactor
            cover.rotation = 0f
            cover.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(24).toFloat())
                }
            }
            cover.clipToOutline = true
        }

        root.findViewById<View>(R.id.iv_cover)?.let { iv ->
            iv.visibility = View.VISIBLE
            iv.scaleX = 1f
            iv.scaleY = 1f
            iv.rotation = 0f
            iv.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(24).toFloat())
                }
            }
            iv.clipToOutline = true
        }

        root.findViewById<View>(R.id.iv_glow)?.let { glow ->
            glow.alpha = 0.6f
            glow.scaleX = 1f
            glow.scaleY = 1f
        }

        root.findViewById<View>(R.id.controls_container)?.let { controls ->
            controls.scaleX = 1f
            controls.scaleY = 1f
        }
    }

    private fun applyCompactStyle(root: ConstraintLayout) {
        currentScaleFactor = 180f / BASE_SIZE_DP
        removeVinylView(root)

        root.findViewById<View>(R.id.cover_container)?.let { cover ->
            cover.scaleX = currentScaleFactor
            cover.scaleY = currentScaleFactor
            cover.rotation = 0f
            cover.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(16).toFloat())
                }
            }
            cover.clipToOutline = true
        }

        root.findViewById<View>(R.id.iv_cover)?.let { iv ->
            iv.visibility = View.VISIBLE
            iv.scaleX = 1f
            iv.scaleY = 1f
            iv.rotation = 0f
            iv.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(16).toFloat())
                }
            }
            iv.clipToOutline = true
        }

        root.findViewById<View>(R.id.iv_glow)?.let { glow ->
            glow.alpha = 0.35f
            glow.scaleX = 1f
            glow.scaleY = 1f
        }

        root.findViewById<View>(R.id.controls_container)?.let { controls ->
            controls.scaleX = 0.9f
            controls.scaleY = 0.9f
        }
    }

    private fun applyVinylStyle(root: ConstraintLayout) {
        currentScaleFactor = 240f / BASE_SIZE_DP

        root.findViewById<FrameLayout>(R.id.cover_container)?.let { cover ->
            cover.scaleX = currentScaleFactor
            cover.scaleY = currentScaleFactor
            cover.rotation = 0f
            cover.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val size = minOf(view.width, view.height)
                    outline.setOval(0, 0, size, size)
                }
            }
            cover.clipToOutline = true

            if (vinylRecordView == null) {
                val vinyl = VinylRecordView(root.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                cover.addView(vinyl)
                vinylRecordView = vinyl
                lastVinylArtwork?.let { vinyl.setArtwork(it) }
                if (lastVinylArtwork == null) {
                    val bmp = (root.findViewById<android.widget.ImageView>(R.id.iv_cover)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    vinyl.setArtwork(bmp)
                }
            }
        }

        root.findViewById<ImageView>(R.id.iv_cover)?.let { iv ->
            iv.visibility = View.GONE
        }

        root.findViewById<View>(R.id.iv_glow)?.let { glow ->
            glow.alpha = 0.5f
            glow.scaleX = 1f
            glow.scaleY = 1f
        }

        root.findViewById<View>(R.id.controls_container)?.let { controls ->
            controls.scaleX = 1f
            controls.scaleY = 1f
        }
    }

    /**
     * Start vinyl rotation animation if style is "vinyl" and playing.
     */
    fun startVinylRotation(view: View) {
        if (currentStyle != "vinyl") return
        vinylRecordView?.setPlaying(true)
    }

    fun stopVinylRotation(view: View) {
        if (currentStyle != "vinyl") return
        vinylRecordView?.setPlaying(false)
    }

    /**
     * Animate cover on song change.
     */
    fun animateSongChange(root: ConstraintLayout) {
        val cover = root.findViewById<View>(R.id.cover_container) ?: return
        cover.alpha = 0f
        cover.scaleX = currentScaleFactor * 0.8f
        cover.scaleY = currentScaleFactor * 0.8f
        cover.animate()
            .alpha(1f)
            .scaleX(currentScaleFactor)
            .scaleY(currentScaleFactor)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
    }

    /**
     * Animate cover "breathe" effect when playing (subtle scale pulsation).
     */
    fun animateBreathe(root: ConstraintLayout) {
        val cover = root.findViewById<View>(R.id.iv_cover) ?: return
        cover.animate().cancel()
        cover.animate()
            .scaleX(1.03f)
            .scaleY(1.03f)
            .setDuration(2000)
            .setInterpolator(OvershootInterpolator(2f))
            .withEndAction {
                cover.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(2000)
                    .setInterpolator(OvershootInterpolator(2f))
                    .withEndAction {
                        // Repeat handled by PlayerFragment
                    }
                    .start()
            }
            .start()
    }

    /**
     * Stop cover breathing animation.
     */
    fun stopBreathe(root: ConstraintLayout) {
        val cover = root.findViewById<View>(R.id.iv_cover) ?: return
        cover.animate().cancel()
        cover.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .start()
    }

    fun updateVinylArtwork(bitmap: Bitmap?) {
        lastVinylArtwork = bitmap
        vinylRecordView?.setArtwork(bitmap)
    }

    fun removeVinylViewIfAny(root: ConstraintLayout) {
        removeVinylView(root)
    }

    private fun removeVinylView(root: ConstraintLayout) {
        vinylRecordView?.let { vinyl ->
            vinyl.cleanup()
            (vinyl.parent as? ViewGroup)?.removeView(vinyl)
        }
        vinylRecordView = null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
    }
}