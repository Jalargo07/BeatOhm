package com.musicdownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.musicdownloader.downloader.AudioDownloader
import com.musicdownloader.extractor.YouTubeExtractor
import com.musicdownloader.metadata.MetadataFetcher
import com.musicdownloader.model.DownloadState
import com.musicdownloader.model.DownloadStatus
import com.musicdownloader.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val extractor = YouTubeExtractor()
    private val metadataFetcher = MetadataFetcher()
    private val audioDownloader = AudioDownloader()
    private val downloadStates = mutableMapOf<String, DownloadState>()
    private var notifManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val downloadId = intent.getStringExtra(EXTRA_ID) ?: UUID.randomUUID().toString()
                Log.d(TAG, "Iniciando descarga id=$downloadId url=$url")
                processDownload(downloadId, url)
            }
            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_ID)
                if (id != null) {
                    downloadStates.remove(id)
                    sendBroadcast(createStateIntent(id))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun processDownload(downloadId: String, url: String) {
        val initialState = DownloadState(
            id = downloadId,
            url = url,
            status = DownloadStatus.QUEUED
        )
        downloadStates[downloadId] = initialState
        notifyState(initialState)

        val notification = createNotification("Iniciando descarga...", 0)
        startForeground(NOTIF_ID + downloadId.hashCode(), notification)

        scope.launch {
            try {
                updateState(downloadId, DownloadStatus.EXTRACTING, 0, "Extrayendo información...")

                val isPlaylist = extractor.isPlaylistUrl(url)
                val songs: List<Song>

                if (isPlaylist) {
                    val result = extractor.extractPlaylist(url)
                    if (result.isFailure) {
                        throw result.exceptionOrNull() ?: Exception("Failed to extract playlist")
                    }
                    songs = result.getOrThrow()
                } else {
                    val result = extractor.extractSong(url)
                    if (result.isFailure) {
                        throw result.exceptionOrNull() ?: Exception("Failed to extract song")
                    }
                    songs = listOf(result.getOrThrow())
                }

                var successCount = 0
                for (index in songs.indices) {
                    val song = songs[index]
                    updateState(
                        downloadId, DownloadStatus.FETCHING_METADATA, 0,
                        "Buscando metadata de ${song.title}... (${index + 1}/${songs.size})"
                    )

                    val metaResult = metadataFetcher.fetchFullMetadata(song)
                    val enrichedSong = metaResult.getOrNull() ?: song

                    updateState(
                        downloadId, DownloadStatus.DOWNLOADING, 0,
                        "Descargando ${enrichedSong.title}... (${index + 1}/${songs.size})"
                    )

                    updateSongState(downloadId, enrichedSong)

                    val audioResult = extractor.getBestAudioStream(enrichedSong.youtubeUrl)
                    if (audioResult.isFailure) continue

                    val audioStream = audioResult.getOrThrow()
                    val downloadDir = getDownloadDirectory()

                    val downloadResult = audioDownloader.downloadAudio(
                        audioUrl = audioStream.url,
                        song = enrichedSong,
                        outputDir = downloadDir,
                        onProgress = { progress ->
                            updateState(downloadId, DownloadStatus.DOWNLOADING, progress)
                        }
                    )

                    if (downloadResult.isSuccess) {
                        updateState(
                            downloadId, DownloadStatus.TAGGING, 100,
                            "Escribiendo metadatos..."
                        )
                        successCount++
                    }
                }

                val finalMessage = if (successCount == songs.size) {
                    "Completado: $successCount canciones"
                } else {
                    "Descargadas $successCount de ${songs.size} canciones"
                }

                updateState(downloadId, DownloadStatus.COMPLETED, 100, finalMessage)
                showCompletionNotification(finalMessage)

            } catch (e: Exception) {
                Log.e(TAG, "Error en descarga", e)
                updateState(
                    downloadId, DownloadStatus.ERROR, 0,
                    "Error: ${e.localizedMessage ?: "Desconocido"}"
                )
                showErrorNotification(e.localizedMessage ?: "Error desconocido")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun updateState(id: String, status: DownloadStatus, progress: Int = 0, message: String? = null) {
        val current = downloadStates[id] ?: return
        val newState = current.copy(
            status = status,
            progress = progress,
            errorMessage = message ?: current.errorMessage
        )
        downloadStates[id] = newState
        notifyState(newState)
        updateNotification(newState)
    }

    private fun updateSongState(id: String, song: Song) {
        val current = downloadStates[id] ?: return
        val newState = current.copy(song = song)
        downloadStates[id] = newState
        notifyState(newState)
    }

    private fun notifyState(state: DownloadState) {
        sendBroadcast(createStateIntent(state.id, state))
    }

    private fun createStateIntent(id: String, state: DownloadState? = null): Intent {
        val intent = Intent(BROADCAST_UPDATE)
        intent.putExtra(EXTRA_ID, id)
        if (state != null) {
            intent.putExtra(EXTRA_STATE, state.status.name)
            intent.putExtra(EXTRA_PROGRESS, state.progress)
            intent.putExtra(EXTRA_MESSAGE, state.errorMessage)
        }
        return intent
    }

    private fun createNotification(message: String, progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music Downloader")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(state: DownloadState) {
        val message = when (state.status) {
            DownloadStatus.EXTRACTING -> "Extrayendo información..."
            DownloadStatus.FETCHING_METADATA -> "Buscando metadata..."
            DownloadStatus.DOWNLOADING -> "Descargando ${state.progress}%..."
            DownloadStatus.TAGGING -> "Escribiendo metadatos..."
            DownloadStatus.COMPLETED -> "¡Completado!"
            DownloadStatus.ERROR -> "Error: ${state.errorMessage}"
            else -> "Preparando..."
        }

        val notification = createNotification(
            if (state.song.title.isNotBlank()) "${state.song.title} - $message" else message,
            state.progress
        )

        if (state.status == DownloadStatus.COMPLETED || state.status == DownloadStatus.ERROR) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            notifManager?.notify(NOTIF_ID, notification)
        }
    }

    private fun showCompletionNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Descarga completada")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        notifManager?.notify(NOTIF_ID_COMPLETE, notification)
    }

    private fun showErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Error en descarga")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        notifManager?.notify(NOTIF_ID_ERROR, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Descargas",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificaciones de descarga de música"
        }
        notifManager?.createNotificationChannel(channel)
    }

    private fun getDownloadDirectory(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val appDir = File(musicDir, "MusicDownloader")
        if (!appDir.exists()) appDir.mkdirs()
        return appDir
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val TAG = "MusicDownloader"
        const val ACTION_DOWNLOAD = "com.musicdownloader.DOWNLOAD"
        const val ACTION_CANCEL = "com.musicdownloader.CANCEL"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_STATE = "extra_state"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_MESSAGE = "extra_message"
        const val BROADCAST_UPDATE = "com.musicdownloader.UPDATE"
        const val CHANNEL_ID = "music_downloader_channel"
        private const val NOTIF_ID = 1000
        private const val NOTIF_ID_COMPLETE = 1001
        private const val NOTIF_ID_ERROR = 1002
    }
}
