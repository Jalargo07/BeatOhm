package com.musicdownloader

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
import com.musicdownloader.data.AppDatabase
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.data.RegenRepository
import com.musicdownloader.data.WaveformRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

class MetadataRegenService : Service() {

    inner class LocalBinder : Binder() {
        fun getService() = this@MetadataRegenService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var regenJob: Job? = null
    @Volatile private var isPaused = false
    private lateinit var repository: MusicRepository
    private lateinit var regenRepo: RegenRepository
    private lateinit var waveformRepo: WaveformRepository
    private val dao by lazy { AppDatabase.getInstance(this).songDao() }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        repository = MusicRepository(this)
        regenRepo = RegenRepository(this)
        waveformRepo = WaveformRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRegen(intent)
            ACTION_PAUSE -> pauseRegen()
            ACTION_RESUME -> resumeRegen()
            ACTION_CANCEL -> cancelRegen()
        }
        return START_NOT_STICKY
    }

    private fun startRegen(intent: Intent) {
        Log.d(TAG, "startRegen called, extras=${intent.extras?.keySet()}")

        // Support both intent array and file-based ID list (for ADB batch start)
        val songIdsFromIntent = intent.getStringArrayExtra(EXTRA_SONG_IDS)
        val songIdsFromFile = intent.getStringExtra(EXTRA_SONG_IDS_FILE)
        Log.d(TAG, "songIdsFromIntent=${songIdsFromIntent?.size}, songIdsFromFile=$songIdsFromFile")

        val songIds = songIdsFromIntent ?: songIdsFromFile?.let { path ->
            try {
                val file = java.io.File(path)
                Log.d(TAG, "Reading IDs from file: $path, exists=${file.exists()}, readable=${file.canRead()}")
                file.readLines().filter { it.isNotBlank() }.toTypedArray()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read IDs from file: ${e.message}")
                null
            }
        }

        val doMetadata = intent.getBooleanExtra(EXTRA_DO_METADATA, false)
        val doLyrics = intent.getBooleanExtra(EXTRA_DO_LYRICS, false)
        val doWaveform = intent.getBooleanExtra(EXTRA_DO_WAVEFORM, false)
        val doArtwork = intent.getBooleanExtra(EXTRA_DO_ARTWORK, false)
        val doColor = intent.getBooleanExtra(EXTRA_DO_COLOR, false)

        // ALWAYS call startForeground first (required within 5s on API 34)
        isPaused = false
        val count = songIds?.size ?: 0
        startForeground(NOTIFICATION_ID, buildProgressNotification(0, count, getString(R.string.regen_notification_title)))

        if (songIds == null || songIds.isEmpty()) {
            Log.e(TAG, "No song IDs provided, stopping")
            stopSelf()
            return
        }

        if (!doMetadata && !doLyrics && !doWaveform && !doArtwork && !doColor) {
            Log.e(TAG, "No regen flags set, stopping")
            stopSelf()
            return
        }

        Log.d(TAG, "Starting regen: ${songIds.size} songs, metadata=$doMetadata lyrics=$doLyrics waveform=$doWaveform artwork=$doArtwork color=$doColor")

        regenJob?.cancel()
        regenJob = serviceScope.launch {
            val succeededIds = mutableListOf<String>()
            val failedIds = mutableListOf<String>()
            val failedReasons = mutableMapOf<String, String>()

            regenRepo.markPending(songIds.toList())
            regenRepo.startRegenProgress(songIds.size)

            val threads = DeviceUtils.getOptimalThreadCount(this@MetadataRegenService)
            val semaphore = Semaphore(threads)
            val done = AtomicInteger(0)
            val total = songIds.size
            val batches = songIds.toList().chunked(10)

            Log.d(TAG, "Processing $total songs with $threads threads (batched by 10)")

            batches.map { batch ->
                async {
                    semaphore.withPermit {
                        for (songId in batch) {
                            if (!isActive) return@async

                            if (isPaused) {
                                updateNotificationPaused(done.get(), total)
                                while (isPaused) {
                                    delay(500)
                                    if (!isActive) return@async
                                }
                                if (!isActive) return@async
                            }

                            val song = dao.getSongById(songId)
                            if (song == null) {
                                synchronized(failedIds) {
                                    failedIds.add(songId)
                                    failedReasons[songId] = "not_found"
                                }
                                done.incrementAndGet()
                                continue
                            }

                            updateNotificationProgress(done.get() + 1, total, song.title)

                            try {
                                val result = withTimeoutOrNull(30_000L) {
                                    var updated = song
                                    if (doMetadata) updated = repository.fetchMetadata(updated)
                                    if (doArtwork) updated = repository.downloadArtworkForSong(updated)
                                    if (doColor) updated = repository.extractDominantColor(updated)
                                    if (doLyrics) updated = repository.fetchLyricsForSong(updated)
                                    if (doMetadata || doLyrics || doArtwork || doColor) {
                                        repository.saveSong(updated)
                                    }
                                    if (doWaveform) waveformRepo.resetWaveform(updated)
                                    Unit
                                }
                                val current = done.incrementAndGet()
                                regenRepo.updateRegenProgress(current, total)
                                updateNotificationProgress(current, total, song.title)
                                if (result != null) {
                                    synchronized(succeededIds) {
                                        succeededIds.add(songId)
                                    }
                                    regenRepo.markSuccess(songId)
                                } else {
                                    synchronized(failedIds) {
                                        failedIds.add(songId)
                                        failedReasons[songId] = "timeout"
                                    }
                                    regenRepo.markFailed(songId)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                val current = done.incrementAndGet()
                                regenRepo.updateRegenProgress(current, total)
                                synchronized(failedIds) {
                                    failedIds.add(songId)
                                    failedReasons[songId] = e.message ?: "unknown"
                                }
                                regenRepo.markFailed(songId)
                            }
                        }
                    }
                }
            }.awaitAll()

            regenRepo.finishRegenProgress()
            regenRepo.clearRegenStatus()
            saveLastRegenResult(succeededIds, failedIds, failedReasons, songIds.size)
            showCompletionNotification(succeededIds.size, failedIds.size)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pauseRegen() {
        isPaused = true
    }

    private fun resumeRegen() {
        isPaused = false
    }

    private fun cancelRegen() {
        isPaused = false
        regenJob?.cancel()
        regenJob = null
        serviceScope.launch {
            regenRepo.finishRegenProgress()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.regen_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.regen_channel_description)
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
            Intent(this, MetadataRegenService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_player)
            .setContentTitle(getString(R.string.regen_notification_title))
            .setContentText(getString(R.string.regen_notification_progress, done, total, title))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_pause, getString(R.string.regen_action_cancel), cancelIntent)

        if (total > 0) {
            builder.setProgress(total, done, false)
        }

        return builder.build()
    }

    private fun updateNotificationProgress(done: Int, total: Int, title: String) {
        val cancelIntent = PendingIntent.getService(
            this, ACTION_CANCEL.hashCode(),
            Intent(this, MetadataRegenService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this, ACTION_PAUSE.hashCode(),
            Intent(this, MetadataRegenService::class.java).apply { action = ACTION_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val finalNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_player)
            .setContentTitle(getString(R.string.regen_notification_title))
            .setContentText(getString(R.string.regen_notification_progress, done, total, title))
            .setOngoing(true)
            .setProgress(total, done, false)
            .addAction(R.drawable.ic_pause, getString(R.string.regen_action_pause), pauseIntent)
            .addAction(R.drawable.ic_player, getString(R.string.regen_action_cancel), cancelIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, finalNotification)
    }

    private fun updateNotificationPaused(done: Int, total: Int) {
        val resumeIntent = PendingIntent.getService(
            this, ACTION_RESUME.hashCode(),
            Intent(this, MetadataRegenService::class.java).apply { action = ACTION_RESUME },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            this, ACTION_CANCEL.hashCode(),
            Intent(this, MetadataRegenService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_player)
            .setContentTitle(getString(R.string.regen_notification_paused, done, total))
            .setOngoing(true)
            .addAction(R.drawable.ic_play, getString(R.string.regen_action_resume), resumeIntent)
            .addAction(R.drawable.ic_player, getString(R.string.regen_action_cancel), cancelIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(succeeded: Int, failed: Int) {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (failed > 0) {
            getString(R.string.regen_notification_failed, succeeded, succeeded + failed, failed)
        } else {
            getString(R.string.regen_notification_complete, succeeded, succeeded)
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

    // --- SharedPreferences ---

    private fun saveLastRegenResult(
        succeededIds: List<String>,
        failedIds: List<String>,
        failedReasons: Map<String, String>,
        total: Int
    ) {
        val json = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("totalSongs", total)
            put("succeededIds", JSONArray(succeededIds))
            put("failedIds", JSONArray(failedIds))
            put("failedReasons", JSONObject(failedReasons))
        }
        getSharedPreferences("player_prefs", MODE_PRIVATE).edit()
            .putString("last_regen_result", json.toString())
            .apply()
    }

    override fun onDestroy() {
        regenJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MetadataRegenService"
        const val ACTION_START = "com.musicdownloader.action.REGEN_START"
        const val ACTION_PAUSE = "com.musicdownloader.action.REGEN_PAUSE"
        const val ACTION_RESUME = "com.musicdownloader.action.REGEN_RESUME"
        const val ACTION_CANCEL = "com.musicdownloader.action.REGEN_CANCEL"
        const val EXTRA_SONG_IDS = "regen_song_ids"
        const val EXTRA_SONG_IDS_FILE = "regen_song_ids_file"
        const val EXTRA_DO_METADATA = "regen_do_metadata"
        const val EXTRA_DO_LYRICS = "regen_do_lyrics"
        const val EXTRA_DO_WAVEFORM = "regen_do_waveform"
        const val EXTRA_DO_ARTWORK = "regen_do_artwork"
        const val EXTRA_DO_COLOR = "regen_do_color"
        private const val CHANNEL_ID = "metadata_regen_channel"
        private const val NOTIFICATION_ID = 1002

        fun start(context: Context, songIds: Array<String>, doMetadata: Boolean, doLyrics: Boolean, doWaveform: Boolean, doArtwork: Boolean, doColor: Boolean) {
            val intent = Intent(context, MetadataRegenService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SONG_IDS, songIds)
                putExtra(EXTRA_DO_METADATA, doMetadata)
                putExtra(EXTRA_DO_LYRICS, doLyrics)
                putExtra(EXTRA_DO_WAVEFORM, doWaveform)
                putExtra(EXTRA_DO_ARTWORK, doArtwork)
                putExtra(EXTRA_DO_COLOR, doColor)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
