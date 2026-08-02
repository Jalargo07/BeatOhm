package com.musicdownloader.ui

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import coil.load
import com.musicdownloader.R
import com.musicdownloader.data.AppDatabase
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.data.PlaylistSong
import com.musicdownloader.data.toSong
import com.musicdownloader.databinding.FragmentPlayerBinding
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PlayerViewModel
    private lateinit var repository: MusicRepository
    private var isSeeking = false
    private var isLyricsVisible = false
    private var updateRunnable: Runnable? = null
    private var currentSongFilePath: String? = null
    private var coverBreatheAnimator: ValueAnimator? = null

    private var audioManager: AudioManager? = null
    private var isVolumeDragging = false
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION" && !isVolumeDragging) {
                val am = audioManager ?: return
                binding.volumeSeekbar.progress = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = PlayerViewModel.getInstance(requireActivity().application as Application)
        repository = MusicRepository(requireContext())
        audioManager = requireContext().getSystemService(AudioManager::class.java)
        requireActivity().volumeControlStream = AudioManager.STREAM_MUSIC

        setupObservers()
        setupControls()
    }

    override fun onStart() {
        super.onStart()
        requireContext().registerReceiver(
            volumeReceiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        )
    }

    override fun onResume() {
        super.onResume()
        audioManager?.let { am ->
            binding.volumeSeekbar.max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            binding.volumeSeekbar.progress = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
    }

    override fun onStop() {
        requireContext().unregisterReceiver(volumeReceiver)
        super.onStop()
    }

    private fun setupObservers() {
        viewModel.currentSong.observe(viewLifecycleOwner) { song ->
            isLyricsVisible = false
            binding.syncedLyricsView.visibility = View.GONE
            binding.ivCover.visibility = View.VISIBLE
            if (song != null) {
                val path = song.filePath.ifBlank { song.youtubeUrl }
                currentSongFilePath = path
                binding.tvTitle.text = song.title
                binding.tvArtist.text = song.artist.ifBlank { "Desconocido" }
                binding.tvNoSong.visibility = View.GONE
                binding.ivGlow.alpha = 0f
                binding.ivGlow.visibility = View.VISIBLE
                binding.ivGlow.animate().alpha(1f).setDuration(300).start()
                if (song.thumbnailUrl.isNotBlank()) {
                    binding.ivCover.load(song.thumbnailUrl) {
                        placeholder(R.drawable.ic_player)
                        error(R.drawable.ic_player)
                    }
                } else if (path.isNotBlank() && File(path).exists()) {
                    binding.ivCover.tag = path
                    ArtworkLoader.loadArtFromAudioFile(binding.ivCover, path)
                } else {
                    binding.ivCover.setImageResource(R.drawable.ic_player)
                }
                updateFavoriteIcon(path)
            } else {
                currentSongFilePath = null
                binding.tvNoSong.visibility = View.VISIBLE
                binding.tvTitle.text = getString(R.string.app_name)
                binding.tvArtist.text = ""
                binding.ivCover.setImageResource(R.drawable.ic_music_note)
                binding.btnFavorite.setImageResource(R.drawable.ic_favorite_border)
                binding.ivGlow.animate().alpha(0f).setDuration(200).withEndAction {
                    if (_binding != null) binding.ivGlow.visibility = View.INVISIBLE
                }.start()
                stopCoverBreathe()
            }
        }

        viewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            binding.btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause
                else R.drawable.ic_play
            )
            if (playing) {
                animateCoverPlaying()
            } else {
                stopCoverBreathe()
            }
        }

        viewModel.isShuffle.observe(viewLifecycleOwner) { shuffle ->
            binding.btnShuffle.alpha = if (shuffle) 1f else 0.4f
        }

        viewModel.repeatMode.observe(viewLifecycleOwner) { mode ->
            when (mode) {
                PlayerViewModel.RepeatMode.ALL -> {
                    binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                    binding.btnRepeat.alpha = 1f
                }
                PlayerViewModel.RepeatMode.ONE -> {
                    binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                    binding.btnRepeat.alpha = 1f
                }
                PlayerViewModel.RepeatMode.OFF -> {
                    binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                    binding.btnRepeat.alpha = 0.4f
                }
            }
        }

        viewModel.duration.observe(viewLifecycleOwner) { dur ->
            binding.seekBar.max = MAX_SEEK
            binding.tvTotalTime.text = formatTime(dur)
        }
    }

    private fun currentDurationMs(): Long = viewModel.duration.value ?: 0L

    private fun updateFavoriteIcon(filePath: String) {
        lifecycleScope.launch {
            val song = repository.getSongById(filePath)
            val isFav = song?.isFavorite ?: false
            binding.btnFavorite.setImageResource(
                if (isFav) R.drawable.ic_favorite
                else R.drawable.ic_favorite_border
            )
        }
    }

    private fun animateCoverPlaying() {
        stopCoverBreathe()
        binding.coverContainer.scaleX = 0.95f
        binding.coverContainer.scaleY = 0.95f
        binding.coverContainer.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (_binding != null) startCoverBreathe()
            }
            .start()
    }

    private fun startCoverBreathe() {
        val animator = ValueAnimator.ofFloat(0.98f, 1.0f).apply {
            duration = 1600L
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                binding.coverContainer.scaleX = value
                binding.coverContainer.scaleY = value
            }
            start()
        }
        coverBreatheAnimator = animator
    }

    private fun stopCoverBreathe() {
        coverBreatheAnimator?.cancel()
        coverBreatheAnimator = null
        binding.coverContainer.animate().cancel()
        binding.coverContainer.scaleX = 1f
        binding.coverContainer.scaleY = 1f
    }

    private fun animatePlayPausePress() {
        binding.btnPlayPause.scaleX = 0.9f
        binding.btnPlayPause.scaleY = 0.9f
        binding.btnPlayPause.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(BounceInterpolator())
            .start()
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            val song = viewModel.currentSong.value ?: return@setOnClickListener
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService
            if (service == null) return@setOnClickListener

            animatePlayPausePress()

            if (service.isPlaying()) service.pause() else service.play()
        }

        binding.btnNext.setOnClickListener {
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService ?: return@setOnClickListener
            val nextSong = viewModel.nextSong() ?: return@setOnClickListener
            val path = nextSong.filePath.ifBlank { nextSong.youtubeUrl }
            if (path.isNotBlank()) {
                service.playFile(path)
            }
        }

        binding.btnPrev.setOnClickListener {
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService ?: return@setOnClickListener
            val prevSong = viewModel.prevSong() ?: return@setOnClickListener
            val path = prevSong.filePath.ifBlank { prevSong.youtubeUrl }
            if (path.isNotBlank()) {
                service.playFile(path)
            }
        }

        binding.btnShuffle.setOnClickListener {
            viewModel.toggleShuffle()
        }

        binding.btnRepeat.setOnClickListener {
            viewModel.toggleRepeatMode()
        }

        binding.btnQueue.setOnClickListener {
            QueueBottomSheetDialogFragment().show(childFragmentManager, "queue")
        }

        binding.btnEqualizer.setOnClickListener {
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService ?: return@setOnClickListener
            EqualizerDialog(requireContext(), service.getAudioSessionId()).show()
        }

        binding.btnLyrics.setOnClickListener {
            val song = viewModel.currentSong.value ?: return@setOnClickListener
            if (song.lyrics.isBlank()) {
                Toast.makeText(requireContext(), R.string.no_lyrics, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleLyrics()
        }

        binding.btnFavorite.setOnClickListener {
            val path = currentSongFilePath ?: return@setOnClickListener
            lifecycleScope.launch {
                val song = repository.getSongById(path)
                if (song != null) {
                    repository.setFavorite(path, !song.isFavorite)
                    updateFavoriteIcon(path)
                }
            }
        }

        binding.btnRewind.setOnClickListener {
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService ?: return@setOnClickListener
            val newPos = maxOf(0L, service.getCurrentPosition() - 10_000L)
            service.seekTo(newPos)
        }

        binding.btnForward.setOnClickListener {
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService ?: return@setOnClickListener
            val newPos = service.getCurrentPosition() + 10_000L
            service.seekTo(newPos)
        }

        binding.btnAddPlaylist.setOnClickListener {
            val path = currentSongFilePath ?: return@setOnClickListener
            showAddToPlaylistDialog(path)
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val dur = currentDurationMs()
                    binding.tvCurrentTime.text = formatTime(if (dur > 0) progress.toLong() * dur / MAX_SEEK else 0L)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return
                val dur = currentDurationMs()
                val target = if (dur > 0 && seekBar != null) (seekBar.progress.toLong() * dur / MAX_SEEK) else 0L
                activity.playbackService?.seekTo(target)
            }
        })

        binding.volumeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isVolumeDragging = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { isVolumeDragging = false }
        })

        startPositionUpdater()
    }

    private fun toggleLyrics() {
        isLyricsVisible = !isLyricsVisible
        if (isLyricsVisible) {
            val song = viewModel.currentSong.value
            binding.syncedLyricsView.setLyrics(song?.lyrics ?: "")
            binding.syncedLyricsView.visibility = View.VISIBLE
            binding.ivCover.visibility = View.INVISIBLE
            binding.syncedLyricsView.alpha = 0f
            binding.syncedLyricsView.animate().alpha(1f).setDuration(200).start()
            val pos = (requireActivity() as? com.musicdownloader.MainActivity)?.playbackService?.getCurrentPosition() ?: 0L
            binding.syncedLyricsView.updatePosition(pos)
        } else {
            binding.syncedLyricsView.animate().alpha(0f).setDuration(200).withEndAction {
                binding.syncedLyricsView.visibility = View.GONE
                binding.ivCover.visibility = View.VISIBLE
            }.start()
        }
    }

    private fun showAddToPlaylistDialog(songFilePath: String) {
        val db = AppDatabase.getInstance(requireContext())
        lifecycleScope.launch {
            val list = db.playlistDao().getAllPlaylists().first()
            if (list.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Sin playlists")
                    .setMessage("Crea una playlist primero desde la pestaña Playlists.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }
            val names = list.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Agregar a playlist")
                .setItems(names) { _, which ->
                    val playlist = list[which]
                    lifecycleScope.launch {
                        db.playlistDao().addSongToPlaylist(
                            PlaylistSong(playlist.id, songFilePath, 0)
                        )
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun startPositionUpdater() {
        updateRunnable = object : Runnable {
            override fun run() {
                val b = _binding ?: return
                val activity = b.root.context as? com.musicdownloader.MainActivity
                val service = activity?.playbackService
                if (service != null && !isSeeking) {
                    val pos = service.getCurrentPosition()
                    var dur = currentDurationMs()
                    if (dur <= 0L) {
                        val serviceDur = service.getDuration()
                        if (serviceDur > 0L && serviceDur != C.TIME_UNSET) {
                            dur = serviceDur
                            viewModel.setDuration(dur)
                        }
                    }
                    val progress = if (dur > 0) ((pos * MAX_SEEK) / dur).toInt() else 0
                    b.seekBar.progress = progress.coerceIn(0, MAX_SEEK)
                    b.tvCurrentTime.text = formatTime(pos)
                    viewModel.setPosition(pos)
                    if (isLyricsVisible) {
                        b.syncedLyricsView.updatePosition(pos)
                    }
                }
                b.root.postDelayed(this, 500)
            }
        }
        _binding?.root?.postDelayed(updateRunnable!!, 500)
    }

    private fun formatTime(millis: Long): String {
        val mins = TimeUnit.MILLISECONDS.toMinutes(millis)
        val secs = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return "%d:%02d".format(mins, secs)
    }

    override fun onDestroyView() {
        stopCoverBreathe()
        updateRunnable?.let { binding.root.removeCallbacks(it) }
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val MAX_SEEK = 1000
    }
}
