package com.beatohm.ui

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

class WavePhaseAnimator(
    private val onPhaseUpdate: (phase: Float) -> Unit
) {
    private var animator: ValueAnimator? = null
    private var currentPhase = 0f

    fun start() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 2f * Math.PI.toFloat()).apply {
            duration = 2000
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                currentPhase = anim.animatedValue as Float
                onPhaseUpdate(currentPhase)
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    fun isRunning(): Boolean = animator?.isRunning == true
}
