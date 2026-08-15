package com.beatohm.ui.player

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import coil.load
import com.beatohm.R
import com.beatohm.data.AudioTagReader
import com.beatohm.databinding.FragmentPlayerBinding
import com.beatohm.lrc.LrcLine
import com.beatohm.lrc.LrcParser
import com.beatohm.model.Song
import com.beatohm.ui.ArtworkLoader
import com.beatohm.ui.TutorialManager
import java.io.File

class PlayerLyricsHelper(
    private val binding: FragmentPlayerBinding,
    private val fragment: Fragment
) {
    private var isLyricsVisible = false
    private var miniLrcLines: List<LrcLine> = emptyList()
    private var lastMiniCurrentIdx = -1
    private var downY = 0f

    fun toggleLyrics(song: Song?) {
        isLyricsVisible = !isLyricsVisible
        if (isLyricsVisible) {
            var lyrics = song?.lyrics.orEmpty()
            if (lyrics.isBlank()) {
                val path = song?.filePath?.ifBlank { song.youtubeUrl }.orEmpty()
                if (path.isNotBlank()) {
                    lyrics = AudioTagReader.readLyrics(path)
                }
            }
            binding.syncedLyricsView.setLyrics(lyrics)
            binding.syncedLyricsView.onLineClicked = { positionMs ->
                (fragment.requireActivity() as? com.beatohm.MainActivity)?.playbackService?.seekTo(positionMs)
            }
            binding.syncedLyricsView.onSwipeDown = {
                closeLyrics()
            }
            loadLyricsBackground(song)
            miniLrcLines = LrcParser.parse(lyrics)
            lastMiniCurrentIdx = -1
            binding.lyricsPanel.visibility = View.VISIBLE
            binding.ivLyricsBackground.visibility = View.VISIBLE
            binding.coverContainer.visibility = View.INVISIBLE
            binding.ivCover.visibility = View.INVISIBLE
            binding.ivGlow.visibility = View.INVISIBLE
            binding.titleContainer.visibility = View.INVISIBLE
            binding.waveformSeekbar.visibility = View.INVISIBLE
            binding.timeRow.visibility = View.INVISIBLE
            binding.controlsContainer.visibility = View.INVISIBLE
            binding.bottomActions.visibility = View.INVISIBLE
            binding.miniLyricsContainer.visibility = View.GONE

            binding.lyricsPanel.alpha = 0f
            binding.lyricsPanel.scaleX = 0.97f
            binding.lyricsPanel.scaleY = 0.97f
            binding.lyricsPanel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .start()

            applyLyricsBlur(true)
            val pos = (fragment.requireActivity() as? com.beatohm.MainActivity)?.playbackService?.getCurrentPosition() ?: 0L
            binding.syncedLyricsView.scrollToCurrentPosition(pos)

            TutorialManager.showTutorial(
                fragment.requireActivity(),
                "lyrics",
                listOf(
                    TutorialManager.TooltipStep({ binding.syncedLyricsView }, fragment.getString(R.string.tutorial_lyrics_tap), fragment.getString(R.string.tutorial_lyrics_tap_desc)),
                    TutorialManager.TooltipStep({ binding.syncedLyricsView }, fragment.getString(R.string.tutorial_lyrics_swipe), fragment.getString(R.string.tutorial_lyrics_swipe_desc))
                )
            )
        } else {
            closeLyrics()
        }
    }

    fun closeLyrics() {
        isLyricsVisible = false
        binding.miniLyricsContainer.visibility = if (miniLrcLines.isNotEmpty()) View.VISIBLE else View.GONE
        updateMiniLyrics()
        applyLyricsBlur(false)
        val panelHeight = binding.lyricsPanel.height.toFloat()
        binding.lyricsPanel.animate()
            .alpha(0f)
            .translationY(panelHeight * 0.3f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (fragment.view != null) {
                    binding.lyricsPanel.translationY = 0f
                    binding.lyricsPanel.visibility = View.GONE
                    binding.ivLyricsBackground.visibility = View.GONE
                    binding.ivLyricsBackground.setImageDrawable(null)
                    binding.ivLyricsBackground.colorFilter = null
                    if (Build.VERSION.SDK_INT >= 31) {
                        binding.ivLyricsBackground.setRenderEffect(null)
                    }
                    binding.coverContainer.visibility = View.VISIBLE
                    binding.ivGlow.visibility = View.VISIBLE
                    if (PlayerLayoutManager.currentStyle != "vinyl") {
                        binding.ivCover.visibility = View.VISIBLE
                    }
                    binding.titleContainer.visibility = View.VISIBLE
                    binding.waveformSeekbar.visibility = View.VISIBLE
                    binding.timeRow.visibility = View.VISIBLE
                    binding.controlsContainer.visibility = View.VISIBLE
                    binding.bottomActions.visibility = View.VISIBLE
                }
            }
            .start()
    }

    fun loadLyricsBackground(song: Song?) {
        val iv = binding.ivLyricsBackground
        if (song != null && song.thumbnailUrl.isNotBlank()) {
            iv.load(song.thumbnailUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_player)
                error(R.drawable.ic_player)
            }
        } else {
            val path = when {
                song == null -> ""
                song.filePath.isNotBlank() -> song.filePath
                else -> song.youtubeUrl
            }
            if (path.isNotBlank() && File(path).exists()) {
                ArtworkLoader.loadArtFromAudioFile(iv, path)
            } else {
                iv.setImageResource(R.drawable.ic_player)
            }
        }
        applyLyricsBackgroundTint()
    }

    private fun applyLyricsBackgroundTint() {
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0.35f)
        }
        binding.ivLyricsBackground.colorFilter = ColorMatrixColorFilter(colorMatrix)
        if (Build.VERSION.SDK_INT >= 31) {
            binding.ivLyricsBackground.setRenderEffect(
                RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.CLAMP)
            )
        }
    }

    private fun applyLyricsBlur(@Suppress("UNUSED_PARAMETER") visible: Boolean) {
        // Blur effect removed - not critical for functionality
        // Can be re-implemented later with compatible API
    }

    fun setupLyricsSwipe() {
        val swipeCloseThreshold = 100 * fragment.resources.displayMetrics.density
        binding.lyricsPanel.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> downY = event.y
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val deltaY = event.y - downY
                    if (isLyricsVisible && deltaY > swipeCloseThreshold) {
                        closeLyrics()
                    }
                }
                else -> {}
            }
            false
        }
    }

    fun parseMiniLyrics(song: Song) {
        val path = song.filePath.ifBlank { song.youtubeUrl }
        var songLyrics = song.lyrics.orEmpty()
        if (songLyrics.isBlank() && path.isNotBlank()) {
            songLyrics = AudioTagReader.readLyrics(path)
        }
        miniLrcLines = LrcParser.parse(songLyrics)
        lastMiniCurrentIdx = -1
        binding.miniLyricsContainer.visibility = if (miniLrcLines.isNotEmpty() && !isLyricsVisible) View.VISIBLE else View.GONE
    }

    fun updateMiniLyrics() {
        if (miniLrcLines.isEmpty()) return
        val pos = (fragment.requireActivity() as? com.beatohm.MainActivity)?.playbackService?.getCurrentPosition() ?: 0L
        var currentIdx = -1
        for (i in miniLrcLines.indices) {
            if (miniLrcLines[i].timeMs <= pos) currentIdx = i else break
        }
        if (currentIdx < 0) return

        binding.tvMiniLyricsCurrent.text = miniLrcLines[currentIdx].text
        binding.tvMiniLyricsNext.text = if (currentIdx + 1 < miniLrcLines.size) miniLrcLines[currentIdx + 1].text else ""

        if (currentIdx == lastMiniCurrentIdx) return

        binding.miniLyricsContainer.post {
            if (fragment.view == null) return@post
            val currentLines = binding.tvMiniLyricsCurrent.lineCount.coerceIn(1, 2)
            val nextText = binding.tvMiniLyricsNext.text?.toString().orEmpty()
            val hasNext = nextText.isNotEmpty()

            val containerLp = binding.miniLyricsContainer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            val currentLp = binding.tvMiniLyricsCurrent.layoutParams as ViewGroup.MarginLayoutParams
            val nextLp = binding.tvMiniLyricsNext.layoutParams as ViewGroup.MarginLayoutParams

            if (currentLines >= 2 && hasNext) {
                containerLp.verticalBias = 0.89f
                currentLp.topMargin = (2 * fragment.resources.displayMetrics.density).toInt()
                nextLp.topMargin = (6 * fragment.resources.displayMetrics.density).toInt()
            } else if (currentLines >= 2 && !hasNext) {
                containerLp.verticalBias = 0.90f
                currentLp.topMargin = (2 * fragment.resources.displayMetrics.density).toInt()
                nextLp.topMargin = (2 * fragment.resources.displayMetrics.density).toInt()
            } else {
                containerLp.verticalBias = 1.0f
                currentLp.topMargin = 0
                nextLp.topMargin = (2 * fragment.resources.displayMetrics.density).toInt()
            }

            binding.miniLyricsContainer.layoutParams = containerLp
            binding.tvMiniLyricsCurrent.layoutParams = currentLp
            binding.tvMiniLyricsNext.layoutParams = nextLp
        }

        lastMiniCurrentIdx = currentIdx
    }

    fun isLyricsOpen(): Boolean = isLyricsVisible

    fun resetOnSongChange() {
        isLyricsVisible = false
        miniLrcLines = emptyList()
        lastMiniCurrentIdx = -1
        applyLyricsBlur(false)
        binding.lyricsPanel.visibility = View.GONE
        binding.syncedLyricsView.setLyrics("")
        binding.ivLyricsBackground.visibility = View.GONE
    }

    fun cleanup() {
        binding.lyricsPanel.animate().cancel()
        binding.syncedLyricsView.setLyrics("")
    }
}
