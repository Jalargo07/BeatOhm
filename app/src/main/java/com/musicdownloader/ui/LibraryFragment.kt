package com.musicdownloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.databinding.FragmentLibraryBinding
import com.musicdownloader.model.Song
import java.io.File

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PlayerViewModel
    private lateinit var adapter: SongAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

        adapter = SongAdapter { song ->
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@SongAdapter
            val service = activity.playbackService
            if (service != null) {
                viewModel.setPlaylist(viewModel.scanMusicFiles(), adapter.currentList.indexOf(song))
                service.playFile(song.youtubeUrl)
            }
        }

        binding.rvLibrary.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLibrary.adapter = adapter

        loadSongs()
    }

    private fun loadSongs() {
        val songs = viewModel.scanMusicFiles()
        if (songs.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvLibrary.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvLibrary.visibility = View.VISIBLE
            adapter.submitList(songs)
        }
    }

    override fun onResume() {
        super.onResume()
        loadSongs()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

class SongAdapter(private val onItemClick: (Song) -> Unit) :
    ListAdapter<Song, SongAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = getItem(position)
        holder.title.text = song.title
        holder.artist.text = song.artist.ifBlank { "Desconocido" }
        holder.itemView.setOnClickListener { onItemClick(song) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
        val artist: TextView = view.findViewById(android.R.id.text2)
    }

    class DiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(old: Song, new: Song): Boolean = old.youtubeUrl == new.youtubeUrl
        override fun areContentsTheSame(old: Song, new: Song): Boolean = old == new
    }
}
