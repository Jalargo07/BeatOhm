package com.musicdownloader.ui.player

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.musicdownloader.R
import com.musicdownloader.ui.ThemeManager

/**
 * Dynamically adapts the player layout based on the current theme's player style.
 * Works with the existing fragment_player.xml to apply style variations without
 * requiring separate layout files.
 */
object PlayerLayoutManager {

    var currentStyle: String = ThemeManager.currentPlayerLayout

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

    /**
     * Classic style: Default centered layout with large album art
     */
    private fun applyClassicStyle(root: ConstraintLayout) {
        // Cover: 260dp, rounded corners
        root.findViewById<View>(R.id.cover_container)?.let { cover ->
            cover.scaleX = 1f
            cover.scaleY = 1f
            cover.rotation = 0f
            val params = cover.layoutParams as ConstraintLayout.LayoutParams
            params.width = dpToPx(260)
            params.height = dpToPx(260)
            params.topMargin = dpToPx(32)
            cover.layoutParams = params
            cover.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(24).toFloat())
                }
            }
            cover.clipToOutline = true
        }

        // Cover image: rounded
        root.findViewById<View>(R.id.iv_cover)?.let { iv ->
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

        // Glow: visible, blurred
        root.findViewById<View>(R.id.iv_glow)?.let { glow ->
            glow.alpha = 0.6f
            glow.scaleX = 1f
            glow.scaleY = 1f
        }

        // Controls container
        root.findViewById<View>(R.id.controls_container)?.let { controls ->
            controls.scaleX = 1f
            controls.scaleY = 1f
        }
    }

    /**
     * Compact style: Smaller cover art, tighter spacing, more compact controls
     */
    private fun applyCompactStyle(root: ConstraintLayout) {
        // Cover: 180dp, more rounded
        root.findViewById<View>(R.id.cover_container)?.let { cover ->
            cover.scaleX = 1f
            cover.scaleY = 1f
            cover.rotation = 0f
            val params = cover.layoutParams as ConstraintLayout.LayoutParams
            params.width = dpToPx(180)
            params.height = dpToPx(180)
            params.topMargin = dpToPx(16)
            cover.layoutParams = params
            cover.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(16).toFloat())
                }
            }
            cover.clipToOutline = true
        }

        // Cover image: rounded
        root.findViewById<View>(R.id.iv_cover)?.let { iv ->
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

        // Glow: subtle
        root.findViewById<View>(R.id.iv_glow)?.let { glow ->
            glow.alpha = 0.35f
            glow.scaleX = 0.8f
            glow.scaleY = 0.8f
        }

        // Controls: slightly smaller
        root.findViewById<View>(R.id.controls_container)?.let { controls ->
            controls.scaleX = 0.9f
            controls.scaleY = 0.9f
        }
    }

    /**
     * Vinyl style: Circular cover art (like a vinyl record), with rotation animation
     */
    private fun applyVinylStyle(root: ConstraintLayout) {
        // Cover: 240dp, fully circular
        root.findViewById<View>(R.id.cover_container)?.let { cover ->
            cover.scaleX = 1f
            cover.scaleY = 1f
            cover.rotation = 0f
            val params = cover.layoutParams as ConstraintLayout.LayoutParams
            params.width = dpToPx(240)
            params.height = dpToPx(240)
            params.topMargin = dpToPx(40)
            cover.layoutParams = params
            cover.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val size = minOf(view.width, view.height)
                    outline.setOval(0, 0, size, size)
                }
            }
            cover.clipToOutline = true
        }

        // Cover image: fully circular
        root.findViewById<View>(R.id.iv_cover)?.let { iv ->
            iv.scaleX = 1f
            iv.scaleY = 1f
            iv.rotation = 0f
            iv.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val size = minOf(view.width, view.height)
                    outline.setOval(0, 0, size, size)
                }
            }
            iv.clipToOutline = true
        }

        // Glow: ring effect
        root.findViewById<View>(R.id.iv_glow)?.let { glow ->
            glow.alpha = 0.5f
            glow.scaleX = 1.1f
            glow.scaleY = 1.1f
        }

        // Controls
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
        val cover = view.findViewById<View>(R.id.cover_container) ?: return
        ObjectAnimator.ofFloat(cover, "rotation", 0f, 360f).apply {
            duration = 8000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = OvershootInterpolator(0f)
            start()
        }
    }

    /**
     * Stop vinyl rotation animation.
     */
    fun stopVinylRotation(view: View) {
        if (currentStyle != "vinyl") return
        val cover = view.findViewById<View>(R.id.cover_container) ?: return
        cover.animate().cancel()
    }

    /**
     * Animate cover on song change.
     */
    fun animateSongChange(root: ConstraintLayout) {
        val cover = root.findViewById<View>(R.id.cover_container) ?: return
        cover.alpha = 0f
        cover.scaleX = 0.8f
        cover.scaleY = 0.8f
        cover.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
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

    private fun dpToPx(dp: Int): Int {
        return (dp * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
    }
}