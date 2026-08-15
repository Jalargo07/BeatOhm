package com.beatohm.ui.player

import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.beatohm.databinding.FragmentPlayerBinding
import com.beatohm.model.Song

class PlayerAnimationHelper(
    private val binding: FragmentPlayerBinding
) {
    private var coverBreatheAnimator: ValueAnimator? = null
    private var isActive = true

    fun animateCoverPlaying() {
        if (!isActive) return
        stopCoverBreathe()
        val sf = PlayerLayoutManager.currentScaleFactor
        binding.coverContainer.scaleX = sf * 0.95f
        binding.coverContainer.scaleY = sf * 0.95f
        binding.coverContainer.animate()
            .scaleX(sf)
            .scaleY(sf)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (isActive) startCoverBreathe()
            }
            .start()
    }

    fun startCoverBreathe() {
        if (!isActive) return
        val sf = PlayerLayoutManager.currentScaleFactor
        val animator = ValueAnimator.ofFloat(sf * 0.98f, sf).apply {
            duration = 1600L
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                binding.coverContainer.scaleX = value
                binding.coverContainer.scaleY = value
            }
            start()
        }
        coverBreatheAnimator = animator
    }

    fun stopCoverBreathe() {
        coverBreatheAnimator?.cancel()
        coverBreatheAnimator = null
        binding.coverContainer.animate().cancel()
        binding.coverContainer.alpha = 1f
        binding.coverContainer.scaleX = PlayerLayoutManager.currentScaleFactor
        binding.coverContainer.scaleY = PlayerLayoutManager.currentScaleFactor
    }

    fun animatePlayPausePress() {
        if (!isActive) return
        binding.btnPlayPause.scaleX = 0.9f
        binding.btnPlayPause.scaleY = 0.9f
        binding.btnPlayPause.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(BounceInterpolator())
            .start()
    }

    fun animateFavoriteHeart() {
        if (!isActive) return
        val btn = binding.btnFavorite
        btn.animate().cancel()
        btn.scaleX = 1f
        btn.scaleY = 1f
        btn.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                btn.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
            .start()
    }

    fun animateSongChange(
        @Suppress("UNUSED_PARAMETER") song: Song,
        onSongUpdated: () -> Unit
    ) {
        if (!isActive) return
        val density = binding.root.resources.displayMetrics.density
        binding.ivCover.animate().cancel()
        binding.titleTextContainer.animate().cancel()

        binding.ivCover.animate()
            .alpha(0f)
            .setDuration(100)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (!isActive) return@withEndAction
                onSongUpdated()
                binding.titleTextContainer.translationY = 20 * density
                binding.titleTextContainer.alpha = 0f
                binding.titleTextContainer.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                binding.ivCover.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    fun cleanup() {
        isActive = false
        coverBreatheAnimator?.cancel()
        coverBreatheAnimator = null
    }
}
