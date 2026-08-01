package com.musicdownloader

import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.ui.PlayerViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MusicPlaybackService : MediaSessionService() {

    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    private val binder = LocalBinder()
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var playerViewModel: PlayerViewModel? = null
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var durationCheckRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        val audioManager = getSystemService(AudioManager::class.java)
        val sessionId = audioManager.generateAudioSessionId()
        player = ExoPlayer.Builder(this).build()
        player.setAudioSessionId(sessionId)
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.media_playback_channel_name)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_player)
        setMediaNotificationProvider(notificationProvider)
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val keyCode = (intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent)?.keyCode
                    return when (keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            advanceSong(true)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            advanceSong(false)
                            true
                        }
                        else -> super.onMediaButtonEvent(session, controllerInfo, intent)
                    }
                }
            })
            .build()
        setupPlayerListeners()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        durationCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        playbackScope.cancel()
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }

    fun setViewModel(vm: PlayerViewModel) {
        playerViewModel = vm
    }

    fun playFile(filePath: String) {
        playerViewModel?.setDuration(0L)
        val uri = Uri.fromFile(File(filePath))
        val fallbackTitle = File(filePath).nameWithoutExtension
        val song = playerViewModel?.currentSong?.value
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(filePath)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(song?.title?.ifBlank { fallbackTitle } ?: fallbackTitle)
                .setArtist(song?.artist)
                .setAlbumTitle(song?.album)
                .setArtworkUri(song?.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                .build())
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        val path = filePath
        playbackScope.launch {
            try { MusicRepository(applicationContext).incrementPlayCount(path) } catch (_: Exception) {}
        }
    }

    fun play() { player.play() }
    fun pause() { player.pause() }
    fun isPlaying(): Boolean = player.isPlaying
    fun seekTo(pos: Long) { player.seekTo(pos) }
    fun getCurrentPosition(): Long = player.currentPosition
    fun getDuration(): Long = player.duration
    fun getPlayer(): ExoPlayer = player
    fun getAudioSessionId(): Int = player.audioSessionId

    fun onSongEnded() {
        val next = playerViewModel?.notifySongEnded() ?: return
        val path = next.filePath.ifBlank { next.youtubeUrl }
        if (path.isNotBlank()) playFile(path)
    }

    private fun advanceSong(next: Boolean) {
        val song = (if (next) playerViewModel?.nextSong() else playerViewModel?.prevSong()) ?: return
        val path = song.filePath.ifBlank { song.youtubeUrl }
        if (path.isNotBlank()) playFile(path)
    }

    private fun checkAndSetDuration(retriesLeft: Int = 5) {
        durationCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        val dur = player.duration
        if (dur != C.TIME_UNSET && dur > 0) {
            playerViewModel?.setDuration(dur)
        } else if (retriesLeft > 0) {
            durationCheckRunnable = Runnable { checkAndSetDuration(retriesLeft - 1) }
            mainHandler.postDelayed(durationCheckRunnable!!, 300)
        }
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerViewModel?.setPlaying(isPlaying)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    checkAndSetDuration()
                }
                if (playbackState == Player.STATE_ENDED) {
                    val queueSize = playerViewModel?.playlist?.value?.size ?: 0
                    if (queueSize > 0) {
                        mainHandler.post { onSongEnded() }
                    }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                playerViewModel?.setDuration(0L)
                checkAndSetDuration()
            }
        })
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
    }
}
