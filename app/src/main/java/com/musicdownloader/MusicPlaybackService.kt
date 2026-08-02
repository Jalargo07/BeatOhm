package com.musicdownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.model.Song
import com.musicdownloader.ui.PlayerViewModel
import java.io.ByteArrayOutputStream
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
        createNotificationChannel()
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
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
            ACTION_NEXT -> advanceSong(true)
            ACTION_PREV -> advanceSong(false)
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return super.onStartCommand(intent, flags, startId)
    }

    private fun buildNotification(): Notification {
        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        val artist = metadata?.artist?.toString()?.takeIf { it.isNotBlank() }

        val playPauseIcon = if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_player)
            .setContentTitle(title)
            .setContentText(artist)
            .setOngoing(true)
            .setStyle(
                Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )

        builder.addAction(Notification.Action.Builder(
            android.R.drawable.ic_media_previous,
            getString(R.string.previous),
            pendingIntentFor(ACTION_PREV)
        ).build())

        builder.addAction(Notification.Action.Builder(
            playPauseIcon,
            if (player.isPlaying) getString(R.string.pause) else getString(R.string.play),
            pendingIntentFor(ACTION_PLAY_PAUSE)
        ).build())

        builder.addAction(Notification.Action.Builder(
            android.R.drawable.ic_media_next,
            getString(R.string.next),
            pendingIntentFor(ACTION_NEXT)
        ).build())

        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.media_playback_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Reproducción de música" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun pendingIntentFor(action: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
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
        if (filePath.isBlank()) return
        playerViewModel?.setDuration(0L)

        val orderedQueue = buildOrderedQueue()
            .filter { song -> song.filePath.ifBlank { song.youtubeUrl }.isNotBlank() }
        val mediaItems = orderedQueue
            .mapIndexed { index, song -> buildMediaItem(song, loadArtwork = index < MAX_ARTWORK_ITEMS) }
            .mapNotNull { it }
        val targetIndex = orderedQueue.indexOfFirst {
            it.filePath == filePath || it.youtubeUrl == filePath
        }

        if (mediaItems.isNotEmpty() && targetIndex >= 0) {
            player.setMediaItems(mediaItems, targetIndex, 0L)
        } else {
            player.setMediaItem(buildSingleMediaItem(filePath))
        }
        syncExoPlayerRepeatMode()
        player.prepare()
        player.play()

        val path = filePath
        playbackScope.launch {
            try { MusicRepository(applicationContext).incrementPlayCount(path) } catch (_: Exception) {}
        }
    }

    private fun buildOrderedQueue(): List<Song> {
        val display = playerViewModel?.displayPlaylist?.value
        val base = playerViewModel?.playlist?.value ?: emptyList()
        return if (!display.isNullOrEmpty()) display else base
    }

    private fun buildMediaItem(song: Song, loadArtwork: Boolean): MediaItem? {
        val path = song.filePath.ifBlank { song.youtubeUrl }
        if (path.isBlank()) return null
        val fallbackTitle = File(path).nameWithoutExtension
        return MediaItem.Builder()
            .setUri(Uri.fromFile(File(path)))
            .setMediaId(path)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(song.title.ifBlank { fallbackTitle })
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkData(
                    if (loadArtwork) loadArtworkBytes(song) else null,
                    MediaMetadata.PICTURE_TYPE_FRONT_COVER
                )
                .build())
            .build()
    }

    private fun buildSingleMediaItem(filePath: String): MediaItem {
        val fallbackTitle = File(filePath).nameWithoutExtension
        val song = playerViewModel?.currentSong?.value
        val artworkData = song?.let { loadArtworkBytes(it) }
        return MediaItem.Builder()
            .setUri(Uri.fromFile(File(filePath)))
            .setMediaId(filePath)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(song?.title?.ifBlank { fallbackTitle } ?: fallbackTitle)
                .setArtist(song?.artist)
                .setAlbumTitle(song?.album)
                .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build())
            .build()
    }

    private fun loadArtworkBytes(song: Song): ByteArray? {
        val candidate = song.thumbnailUrl.takeIf { it.isNotBlank() }
            ?: song.youtubeUrl.takeIf { it.isNotBlank() }
        if (candidate.isNullOrBlank()) return null
        return try {
            val file = File(candidate)
            if (!file.exists() || file.length() <= 0) return null
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return file.readBytes()
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
        } catch (_: Exception) { null }
    }

    private fun syncExoPlayerRepeatMode() {
        player.repeatMode = if (playerViewModel?.repeatMode?.value == PlayerViewModel.RepeatMode.ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    private fun syncViewModelToExoPlayer() {
        val currentMediaId = player.currentMediaItem?.mediaId
        if (currentMediaId.isNullOrBlank()) return
        val currentSong = playerViewModel?.currentSong?.value
        val currentPath = currentSong?.let { it.filePath.ifBlank { it.youtubeUrl } }
        if (currentMediaId == currentPath) return

        val advanced = playerViewModel?.notifySongEnded()
        val advancedPath = advanced?.let { it.filePath.ifBlank { it.youtubeUrl } }
        if (advancedPath.isNullOrBlank()) {
            // La cola del ViewModel quedó vacía: detener para no reproducir items stale.
            player.stop()
            return
        }
        if (advancedPath != currentMediaId) {
            // Cola desincronizada (shuffle/edición mid-playback): re-sincronizar desde el ViewModel.
            playFile(advancedPath)
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
                updateNotification()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    checkAndSetDuration()
                }
                if (playbackState == Player.STATE_ENDED) {
                    val queueSize = playerViewModel?.playlist?.value?.size ?: 0
                    // Solo delegar al ViewModel cuando ExoPlayer no puede avanzar solo (fin real
                    // de su cola). Si hay siguiente item, ExoPlayer auto-avanza y el sync del
                    // ViewModel ocurre en onMediaItemTransition(AUTO) — evita doble avance.
                    if (queueSize > 0 && !player.hasNextMediaItem()) {
                        mainHandler.post { onSongEnded() }
                    }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                playerViewModel?.setDuration(0L)
                checkAndSetDuration()
                updateNotification()
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    syncViewModelToExoPlayer()
                }
            }
        })
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_ARTWORK_ITEMS = 50
        const val ACTION_PLAY_PAUSE = "com.musicdownloader.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.musicdownloader.action.NEXT"
        const val ACTION_PREV = "com.musicdownloader.action.PREV"
    }
}
