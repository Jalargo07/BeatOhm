package com.beatohm.ui

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.beatohm.data.AppDatabase
import com.beatohm.data.PlaylistSong
import com.beatohm.data.toSong
import com.beatohm.R
import com.beatohm.databinding.FragmentPlaylistDetailBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlaylistDetailFragment : Fragment() {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FilteredSongAdapter
    private val db by lazy { AppDatabase.getInstance(requireContext()) }

    private val playlistId: Long
        get() = requireArguments().getLong(ARG_PLAYLIST_ID, 0L)

    private val playlistName: String
        get() = requireArguments().getString(ARG_PLAYLIST_NAME, "")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as AppCompatActivity).supportActionBar?.title = playlistName
        binding.tvPlaylistTitle.text = playlistName

        adapter = FilteredSongAdapter(
            onItemClick = { song ->
                val activity = requireActivity() as? com.beatohm.MainActivity ?: return@FilteredSongAdapter
                val service = activity.playbackService ?: return@FilteredSongAdapter
                val vm = PlayerViewModel.getInstance(requireActivity().application as Application)
                lifecycleScope.launch {
                    db.songDao().getSongsInPlaylist(playlistId).first().let { songs ->
                        val index = songs.indexOf(song)
                        vm.setPlaylist(songs.map { it.toSong() }, if (index >= 0) index else 0)
                        val path = song.filePath
                        if (path.isNotBlank()) {
                            service.playFile(path, isManual = true)
                        }
                    }
                }
            },
            onItemLongClick = { song ->
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.quitar_de_playlist))
                    .setMessage(getString(R.string.quitar_cancion_de_playlist, song.title))
                    .setPositiveButton(getString(R.string.si)) { _, _ ->
                        lifecycleScope.launch {
                            db.playlistDao().removeSongFromPlaylist(PlaylistSong(playlistId, song.filePath, 0))
                            Toast.makeText(requireContext(), getString(R.string.cancion_quitada), Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        )

        binding.rvPlaylistSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlaylistSongs.adapter = adapter

        lifecycleScope.launch {
            db.songDao().getSongsInPlaylist(playlistId).collectLatest { songs ->
                adapter.submitList(songs)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_PLAYLIST_ID = "playlist_id"
        const val ARG_PLAYLIST_NAME = "playlist_name"
    }
}
