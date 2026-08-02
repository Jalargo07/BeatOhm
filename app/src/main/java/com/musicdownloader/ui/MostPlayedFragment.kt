package com.musicdownloader.ui

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.R
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.data.toSong
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MostPlayedFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var listTitle: TextView
    private lateinit var adapter: FilteredSongAdapter
    private lateinit var repository: MusicRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_song_list, container, false)
        recyclerView = view.findViewById(R.id.rv_songs)
        emptyView = view.findViewById(R.id.ll_song_list_empty)
        listTitle = view.findViewById(R.id.tv_list_title)
        view.findViewById<View>(R.id.spinner_sort).visibility = View.GONE
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = MusicRepository(requireContext())
        listTitle.text = getString(R.string.most_played)
        adapter = FilteredSongAdapter(onItemClick = { song ->
            val activity = requireActivity() as? com.musicdownloader.MainActivity
            val service = activity?.playbackService
            if (service != null) {
                val vm = PlayerViewModel.getInstance(requireActivity().application as Application)
                val songs = adapter.currentList
                val index = songs.indexOf(song)
                vm.setPlaylist(songs.map { it.toSong() }, if (index >= 0) index else 0)
                service.playFile(song.filePath)
            }
        })
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            repository.getMostPlayedSongs().collectLatest { songs ->
                adapter.submitList(songs)
                emptyView.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
