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
import com.beatohm.data.AppDatabase
import com.beatohm.data.MetadataCandidateRepository
import com.beatohm.data.MusicRepository
import com.beatohm.data.RegenRepository
import com.beatohm.data.TagWriteLimitReachedException
import com.beatohm.data.WaveformRepository
import com.beatohm.data.toSong
import com.beatohm.metadata.MetadataFetcher
import com.beatohm.metadata.MetadataResult
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
import kotlinx.coroutines.withContext
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
    private val limitDialogShown = java.util.concurrent.atomic.AtomicBoolean(false)
    private lateinit var repository: MusicRepository
    private lateinit var regenRepo: RegenRepository
    private lateinit var waveformRepo: WaveformRepository
    private val metadataFetcher by lazy { MetadataFetcher() }
    private val dao by lazy { AppDatabase.getInstance(this).songDao() }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        repository = MusicRepository(this, metadataCandidateRepo = MetadataCandidateRepository(AppDatabase.getInstance(this).metadataCandidateDao()))
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

            // Skip songs already marked as SUCCESS in a previous run (checkpoint resilience)
            val alreadySuccess = regenRepo.getSuccessIds(songIds.toList())
            val pendingSongIds = songIds.filter { it !in alreadySuccess }
            if (alreadySuccess.isNotEmpty()) {
                Log.d(TAG, "Skipping ${alreadySuccess.size} already-successful songs from checkpoint")
            }

            if (pendingSongIds.isEmpty()) {
                Log.d(TAG, "All songs already processed successfully, finishing")
                regenRepo.finishRegenProgress()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            regenRepo.markPending(pendingSongIds)
            regenRepo.startRegenProgress(pendingSongIds.size)

            val threads = DeviceUtils.getOptimalThreadCount(this@MetadataRegenService)
            val semaphore = Semaphore(threads)
            val done = AtomicInteger(0)
            val total = pendingSongIds.size
            val batches = pendingSongIds.chunked(10)

            Log.d(TAG, "Processing $total songs with $threads threads (batched by 10)")

            batches.map { batch ->
                async {
                    semaphore.withPermit {
                        for (songId in batch) {
                            if (!isActive) return@async

                            if (isPaused) {
                                updateNotificationPaused(done.get(), total)
                                while (isPaused) {
                                    // Check if user reset the counter
                                    if (limitResetPending.compareAndSet(true, false)) {
                                        Log.d(TAG, "Limit reset detected — unpausing ALL threads")
                                        limitDialogShown.set(false)
                                        isPaused = false
                                        break
                                    }
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
                                    var metadataResult: MetadataResult = MetadataResult.NoMatch
                                    if (doMetadata) {
                                        metadataResult = metadataFetcher.fetchFullMetadata(song.toSong())
                                        updated = when (val mr = metadataResult) {
                                            is MetadataResult.ClearMatch ->
                                                repository.applyClearMatch(updated, mr.candidate)
                                            is MetadataResult.AmbiguousMatches -> {
                                                // T13a: persistir candidatos pero NO actualizar metadata de la canción
                                                repository.persistAmbiguousCandidates(song.id, mr.candidates)
                                                updated  // sin cambios
                                            }
                                            MetadataResult.NoMatch -> updated  // sin cambios
                                        }
                                    }
                                    if (doArtwork) {
                                        val before = updated.thumbnailUrl
                                        updated = repository.downloadArtworkForSong(updated)
                                        if (updated.thumbnailUrl != before && updated.thumbnailUrl.isNotBlank()) {
                                            updated = repository.writeArtworkToFile(updated)
                                        }
                                    }
                                    if (doColor) updated = repository.extractDominantColor(updated)
                                    if (doLyrics) {
                                        val before = updated.lyrics
                                        updated = repository.fetchLyricsForSong(updated)
                                        if (updated.lyrics != before && updated.lyrics.isNotBlank()) {
                                            updated = repository.writeLyricsToFile(updated)
                                        }
                                    }
                                    if (doMetadata || doLyrics || doArtwork || doColor) {
                                        repository.saveSong(updated)
                                    }
                                    if (doWaveform) waveformRepo.resetWaveform(updated)
                                    metadataResult  // devolver el resultado para tracking
                                }
                                val current = done.incrementAndGet()
                                regenRepo.updateRegenProgress(current, total)
                                updateNotificationProgress(current, total, song.title)
                                when {
                                    result == null -> {
                                        // Timeout
                                        synchronized(failedIds) {
                                            failedIds.add(songId)
                                            failedReasons[songId] = "timeout"
                                        }
                                        regenRepo.markFailed(songId)
                                    }
                                    result is MetadataResult.AmbiguousMatches -> {
                                        // T13a: ambiguas → no son success ni failed, la UI de pendientes las mostrará
                                        Log.d(TAG, "Song $songId ambiguous → candidates persisted, pending for user choice")
                                    }
                                    else -> {
                                        // ClearMatch o NoMatch → success (el loop anterior ya procesó)
                                        synchronized(succeededIds) {
                                            succeededIds.add(songId)
                                        }
                                        regenRepo.markSuccess(songId)
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: TagWriteLimitReachedException) {
                                // Only ONE thread should trigger the dialog (compareAndSet)
                                if (limitDialogShown.compareAndSet(false, true)) {
                                    Log.w(TAG, "Tag write limit reached — pausing ALL threads, showing dialog ONCE")
                                    isPaused = true
                                    repository.onLimitReached()
                                } else {
                                    // Other threads just pause, don't show another dialog
                                    Log.w(TAG, "Tag write limit reached — pausing (dialog already shown)")
                                    isPaused = true
                                }
                                // No marcar como failed — el usuario puede reanudar tras ver el anuncio
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
            saveLastRegenResult(succeededIds, failedIds, failedReasons, pendingSongIds.size)
            showCompletionNotification(succeededIds.size, failedIds.size)

            // Mostrar toast con conteo de pendientes si hay metadata
            if (doMetadata) {
                try {
                    val pendingCount = withContext(Dispatchers.IO) {
                        repository.metadataCandidateRepository.getPendingCountSync()
                    }
                    if (pendingCount > 0) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                this@MetadataRegenService,
                                getString(R.string.pending_candidates_toast, pendingCount),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get pending count: ${e.message}")
                }
            }

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

    /**
     * Called after user watches ad and counter is reset.
     * Resets limit flag and unpauses ALL threads.
     */
    fun onLimitReset() {
        Log.d(TAG, "onLimitReset: resetting limit flag and unpausing regen")
        limitDialogShown.set(false)
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
        const val ACTION_START = "com.beatohm.action.REGEN_START"
        const val ACTION_PAUSE = "com.beatohm.action.REGEN_PAUSE"
        const val ACTION_RESUME = "com.beatohm.action.REGEN_RESUME"
        const val ACTION_CANCEL = "com.beatohm.action.REGEN_CANCEL"
        const val EXTRA_SONG_IDS = "regen_song_ids"
        const val EXTRA_SONG_IDS_FILE = "regen_song_ids_file"
        const val EXTRA_DO_METADATA = "regen_do_metadata"
        const val EXTRA_DO_LYRICS = "regen_do_lyrics"
        const val EXTRA_DO_WAVEFORM = "regen_do_waveform"
        const val EXTRA_DO_ARTWORK = "regen_do_artwork"
        const val EXTRA_DO_COLOR = "regen_do_color"
        private const val CHANNEL_ID = "metadata_regen_channel"
        private const val NOTIFICATION_ID = 1002

        // Global flag: set by dialog callback, checked by service to unpause
        @Volatile
        var limitResetPending = java.util.concurrent.atomic.AtomicBoolean(false)

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
