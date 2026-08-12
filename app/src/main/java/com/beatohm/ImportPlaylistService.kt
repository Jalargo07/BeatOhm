package com.beatohm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.beatohm.importer.PlaylistImportManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


class ImportPlaylistService : Service() {

    inner class LocalBinder : Binder() {
        fun getService() = this@ImportPlaylistService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var importJob: Job? = null
    @Volatile private var isPaused = false
    private lateinit var importManager: PlaylistImportManager

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        importManager = PlaylistImportManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startImport(intent)
            ACTION_PAUSE -> pauseImport()
            ACTION_RESUME -> resumeImport()
            ACTION_CANCEL -> cancelImport()
        }
        return START_NOT_STICKY
    }

    private fun startImport(intent: Intent) {
        val playlistUrl = intent.getStringExtra(EXTRA_PLAYLIST_URL) ?: run {
            Log.e(TAG, "No playlist URL provided")
            stopSelf()
            return
        }

        isPaused = false
        startForeground(NOTIFICATION_ID, buildProgressNotification(0, 0, getString(R.string.import_notification_title)))

        importJob = serviceScope.launch {
            try {
                importManager.onProgress = { completed, total, currentTrack ->
                    if (isPaused) {
                        updateNotificationPaused(completed, total)
                    } else {
                        updateNotificationProgress(completed, total, currentTrack)
                    }
                }

                importManager.onTrackCompleted = { title, artist, filePath ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        com.beatohm.ui.MainViewModel.instance?.addImportedTrack(title, artist, filePath)
                    }
                }

                importManager.onComplete = { imported, failed, skipped ->
                    Log.d(TAG, "Import complete: $imported imported, $failed failed, $skipped skipped")
                    showCompletionNotification(imported, failed, imported + failed + skipped)
                }

                val sessionId = importManager.startImport(playlistUrl)

                Log.d(TAG, "Import session $sessionId finished")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()

            } catch (e: CancellationException) {
                Log.d(TAG, "Import cancelled")
                importManager.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Import error: ${e.message}")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun pauseImport() {
        isPaused = true
    }

    private fun resumeImport() {
        isPaused = false
    }

    private fun cancelImport() {
        isPaused = false
        importJob?.cancel()
        importJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Notification (SAME pattern as MetadataRegenService) ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.import_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.import_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildProgressNotification(done: Int, total: Int, title: String): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            this, ACTION_CANCEL.hashCode(),
            Intent(this, ImportPlaylistService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_player)
            .setContentTitle(getString(R.string.import_notification_title))
            .setContentText(getString(R.string.import_notification_progress, done, total, title))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_pause, getString(R.string.import_action_cancel), cancelIntent)

        if (total > 0) {
            builder.setProgress(total, done, false)
        }

        return builder.build()
    }

    private fun updateNotificationProgress(done: Int, total: Int, title: String) {
        val cancelIntent = PendingIntent.getService(
            this, ACTION_CANCEL.hashCode(),
            Intent(this, ImportPlaylistService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this, ACTION_PAUSE.hashCode(),
            Intent(this, ImportPlaylistService::class.java).apply { action = ACTION_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_player)
            .setContentTitle(getString(R.string.import_notification_title))
            .setContentText(getString(R.string.import_notification_progress, done, total, title))
            .setOngoing(true)
            .setProgress(total, done, false)
            .addAction(R.drawable.ic_pause, getString(R.string.import_action_pause), pauseIntent)
            .addAction(R.drawable.ic_player, getString(R.string.import_action_cancel), cancelIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationPaused(done: Int, total: Int) {
        val resumeIntent = PendingIntent.getService(
            this, ACTION_RESUME.hashCode(),
            Intent(this, ImportPlaylistService::class.java).apply { action = ACTION_RESUME },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            this, ACTION_CANCEL.hashCode(),
            Intent(this, ImportPlaylistService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_player)
            .setContentTitle(getString(R.string.import_notification_paused, done, total))
            .setOngoing(true)
            .addAction(R.drawable.ic_play, getString(R.string.import_action_resume), resumeIntent)
            .addAction(R.drawable.ic_player, getString(R.string.import_action_cancel), cancelIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(succeeded: Int, failed: Int, total: Int) {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (failed > 0) {
            getString(R.string.import_notification_complete_with_failures, succeeded, total, failed)
        } else {
            getString(R.string.import_notification_complete, succeeded, total)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_player)
            .setContentTitle(title)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setSilent(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        importJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ImportPlaylistService"
        const val ACTION_START = "com.beatohm.action.IMPORT_START"
        const val ACTION_PAUSE = "com.beatohm.action.IMPORT_PAUSE"
        const val ACTION_RESUME = "com.beatohm.action.IMPORT_RESUME"
        const val ACTION_CANCEL = "com.beatohm.action.IMPORT_CANCEL"
        const val EXTRA_PLAYLIST_URL = "import_playlist_url"
        private const val CHANNEL_ID = "playlist_import_channel"
        private const val NOTIFICATION_ID = 1003

        fun start(context: Context, playlistUrl: String) {
            val intent = Intent(context, ImportPlaylistService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PLAYLIST_URL, playlistUrl)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
