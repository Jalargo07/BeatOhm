package com.beatohm.ui.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.beatohm.model.Song

/**
 * Strategy interface for different player layouts.
 */
interface PlayerLayoutStrategy {
    /**
     * Inflate the layout for the player.
     */
    fun inflate(inflater: LayoutInflater, container: ViewGroup?): View

    /**
     * Bind the view with the song data and set up controls.
     * @param view the inflated view
     * @param song the current song
     * @param isPlaying whether the song is playing
     * @param progress current progress in milliseconds
     * @param onPlayPauseClick callback when play/pause button is clicked
     * @param onNextClick callback when next button is clicked
     * @param onPrevClick callback when previous button is clicked
     * @param onSeekBarChanged callback when seek bar is changed (progress in milliseconds)
     */
    fun bind(
        view: View,
        song: Song?,
        isPlaying: Boolean,
        progress: Long,
        onPlayPauseClick: () -> Unit,
        onNextClick: () -> Unit,
        onPrevClick: () -> Unit,
        onSeekBarChanged: (Long) -> Unit
    )

    /**
     * Update the progress of the seek bar.
     */
    fun updateProgress(view: View, progress: Long)

    /**
     * Update the playing state (toggle play/pause icon).
     */
    fun updatePlayingState(view: View, isPlaying: Boolean)

    /**
     * Update the song information (title, artist, cover).
     */
    fun updateSongInfo(view: View, song: Song?)
}