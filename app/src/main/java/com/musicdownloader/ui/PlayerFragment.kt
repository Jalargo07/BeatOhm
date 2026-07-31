package com.musicdownloader.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
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
    private var updateRunnable: Runnable? = null
    private var currentSongFilePath: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]
        repository = MusicRepository(requireContext())

        setupObservers()
        setupControls()
    }

    private fun setupObservers() {
        viewModel.currentSong.observe(viewLifecycleOwner) { song ->
            if (song != null) {
                currentSongFilePath = song.youtubeUrl
                binding.tvTitle.text = song.title
                binding.tvArtist.text = song.artist.ifBlank { "Desconocido" }
                binding.tvNoSong.visibility = View.GONE
                if (song.thumbnailUrl.isNotBlank()) {
                    binding.ivCover.load(song.thumbnailUrl) {
                        placeholder(R.drawable.ic_player)
                        error(R.drawable.ic_player)
                    }
                } else if (song.youtubeUrl.isNotBlank() && File(song.youtubeUrl).exists()) {
                    binding.ivCover.load(android.net.Uri.fromFile(File(song.youtubeUrl))) {
                        placeholder(R.drawable.ic_player)
                        error(R.drawable.ic_player)
                    }
                } else {
                    binding.ivCover.setImageResource(R.drawable.ic_player)
                }
                updateFavoriteIcon(song.youtubeUrl)
            } else {
                currentSongFilePath = null
                binding.tvNoSong.visibility = View.VISIBLE
                binding.tvTitle.text = "Título"
                binding.tvArtist.text = "Artista"
                binding.ivCover.setImageResource(R.drawable.ic_player)
                binding.btnFavorite.setImageResource(android.R.drawable.btn_star_big_off)
            }
        }

        viewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            binding.btnPlayPause.setImageResource(
                if (playing) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }

        viewModel.duration.observe(viewLifecycleOwner) { dur ->
            binding.seekBar.max = if (dur > 0) (dur / 1000).toInt() else 100
            binding.tvTotalTime.text = formatTime(dur)
        }
    }

    private fun updateFavoriteIcon(filePath: String) {
        lifecycleScope.launch {
            val song = repository.getSongById(filePath)
            val isFav = song?.isFavorite ?: false
            binding.btnFavorite.setImageResource(
                if (isFav) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
        }
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            val song = viewModel.currentSong.value ?: return@setOnClickListener
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService
            if (service == null) return@setOnClickListener

            if (service.isPlaying()) service.pause() else service.play()
        }

        binding.btnNext.setOnClickListener {
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService ?: return@setOnClickListener
            val nextSong = viewModel.nextSong() ?: return@setOnClickListener
            if (nextSong.youtubeUrl.isNotBlank()) {
                service.playFile(nextSong.youtubeUrl)
            }
        }

        binding.btnPrev.setOnClickListener {
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService ?: return@setOnClickListener
            val prevSong = viewModel.prevSong() ?: return@setOnClickListener
            if (prevSong.youtubeUrl.isNotBlank()) {
                service.playFile(prevSong.youtubeUrl)
            }
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
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return
                activity.playbackService?.seekTo(seekBar?.progress?.toLong() ?: 0L)
            }
        })

        binding.volumeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return
                val player = activity.playbackService?.getPlayer() ?: return
                player.volume = progress / 100f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        startPositionUpdater()
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
                    b.seekBar.progress = (pos / 1000).toInt()
                    b.tvCurrentTime.text = formatTime(pos)
                    viewModel.setPosition(pos)
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
        updateRunnable?.let { binding.root.removeCallbacks(it) }
        _binding = null
        super.onDestroyView()
    }
}
