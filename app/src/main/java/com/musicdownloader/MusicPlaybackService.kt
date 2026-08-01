package com.musicdownloader

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.ui.PlayerViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MusicPlaybackService : android.app.Service() {

    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    private val binder = LocalBinder()
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
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
        mediaSession = MediaSessionCompat(this, "MusicPlaybackService")
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        setupPlayerListeners()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return android.app.Service.START_STICKY
    }

    override fun onDestroy() {
        durationCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        playbackScope.cancel()
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    fun setViewModel(vm: PlayerViewModel) {
        playerViewModel = vm
    }

    fun playFile(filePath: String) {
        playerViewModel?.setDuration(0L)
        val uri = android.net.Uri.fromFile(File(filePath))
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(File(filePath).nameWithoutExtension)
                .build())
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        startForeground(NOTIFICATION_ID, buildNotification("Reproduciendo", File(filePath).nameWithoutExtension))
        updateMediaSession(filePath)

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
                updateNotification()
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
                updateNotification()
                checkAndSetDuration()
            }
        })
    }

    private fun updateMediaSession(filePath: String) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, File(filePath).nameWithoutExtension)
            .build()
        mediaSession.setMetadata(metadata)
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .build())
    }

    private fun createNotificationChannel() {
        val channel = android.app.NotificationChannel(
            CHANNEL_ID, "Reproducción de música",
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, content: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_player)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val nm = NotificationManagerCompat.from(this)
        try {
            nm.notify(NOTIFICATION_ID, buildNotification(
                if (player.isPlaying) "Reproduciendo" else "Pausado",
                File(player.currentMediaItem?.mediaId ?: "").name.ifEmpty { "Sin canción" }
            ))
        } catch (_: Exception) {}
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
