package com.musicdownloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import coil.load
import com.musicdownloader.R
import com.musicdownloader.databinding.FragmentPlayerBinding
import java.io.File
import java.util.concurrent.TimeUnit

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PlayerViewModel
    private var isSeeking = false
    private var updateRunnable: Runnable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

        setupObservers()
        setupControls()
    }

    private fun setupObservers() {
        viewModel.currentSong.observe(viewLifecycleOwner) { song ->
            if (song != null) {
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
            } else {
                binding.tvNoSong.visibility = View.VISIBLE
                binding.tvTitle.text = "Título"
                binding.tvArtist.text = "Artista"
                binding.ivCover.setImageResource(R.drawable.ic_player)
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

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return
                activity.playbackService?.seekTo(seekBar?.progress?.toLong() ?: 0L)
            }
        })

        startPositionUpdater()
    }

    private fun startPositionUpdater() {
        updateRunnable = object : Runnable {
            override fun run() {
                val activity = requireActivity() as? com.musicdownloader.MainActivity
                val service = activity?.playbackService
                if (service != null && !isSeeking) {
                    val pos = service.getCurrentPosition()
                    binding.seekBar.progress = (pos / 1000).toInt()
                    binding.tvCurrentTime.text = formatTime(pos)
                    viewModel.setPosition(pos)
                }
                binding.root.postDelayed(this, 500)
            }
        }
        binding.root.postDelayed(updateRunnable!!, 500)
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
