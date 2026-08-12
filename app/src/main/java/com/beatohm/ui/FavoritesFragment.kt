package com.beatohm.ui

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.beatohm.R
import com.beatohm.data.MusicRepository
import com.beatohm.data.toSong
import com.beatohm.databinding.FragmentSongListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {
    private var _binding: FragmentSongListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FilteredSongAdapter
    private lateinit var repository: MusicRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSongListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = MusicRepository(requireContext())
        binding.tvListTitle.text = getString(R.string.favorites)
        binding.spinnerSort.visibility = View.GONE
        adapter = FilteredSongAdapter(onItemClick = { song ->
            val activity = requireActivity() as? com.beatohm.MainActivity
            val service = activity?.playbackService
            if (service != null) {
                val vm = PlayerViewModel.getInstance(requireActivity().application as Application)
                val songs = adapter.currentList
                val index = songs.indexOf(song)
                vm.setPlaylist(
                    songs.map { it.toSong() },
                    if (index >= 0) index else 0
                )
                service.playFile(song.filePath)
            }
        })
        binding.rvSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSongs.adapter = adapter

        lifecycleScope.launch {
            repository.getFavoriteSongs().collectLatest { songs ->
                adapter.submitList(songs)
                binding.llSongListEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
