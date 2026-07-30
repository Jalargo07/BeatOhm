package com.musicdownloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.data.toSong
import coil.load
import com.musicdownloader.R
import com.musicdownloader.data.LocalSong
import com.musicdownloader.data.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class SongListFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var emptyView: TextView? = null
    private lateinit var adapter: SongItemAdapter
    private lateinit var repository: MusicRepository
    private lateinit var playerViewModel: PlayerViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_song_list, container, false)
        recyclerView = view.findViewById(R.id.rv_songs)
        emptyView = view.findViewById(R.id.tv_empty)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = MusicRepository(requireContext())
        playerViewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

        adapter = SongItemAdapter { song ->
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@SongItemAdapter
            val service = activity.playbackService
            if (service != null) {
                val songs = adapter.currentList
                val index = songs.indexOf(song)
                playerViewModel.setPlaylist(
                    songs.map { it.toSong() },
                    if (index >= 0) index else 0
                )
                service.playFile(song.filePath)
            }
        }

        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        recyclerView?.adapter = adapter

        lifecycleScope.launch {
            repository.getAllSongs().collectLatest { songs ->
                adapter.submitList(songs)
                if (songs.isEmpty()) {
                    recyclerView?.visibility = View.GONE
                    emptyView?.visibility = View.VISIBLE
                } else {
                    recyclerView?.visibility = View.VISIBLE
                    emptyView?.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        recyclerView = null
        emptyView = null
        super.onDestroyView()
    }
}

class SongItemAdapter(private val onItemClick: (LocalSong) -> Unit) :
    ListAdapter<LocalSong, SongItemAdapter.ViewHolder>(SongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = getItem(position)
        holder.title.text = song.title
        holder.artist.text = song.artist.ifBlank { "Desconocido" }

        if (song.thumbnailUrl.isNotBlank() && File(song.thumbnailUrl).exists()) {
            holder.thumbnail.load(File(song.thumbnailUrl)) {
                crossfade(true)
                placeholder(R.drawable.ic_player)
                error(R.drawable.ic_player)
            }
        } else {
            holder.thumbnail.setImageResource(R.drawable.ic_player)
        }

        holder.itemView.setOnClickListener { onItemClick(song) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.iv_thumbnail)
        val title: TextView = view.findViewById(R.id.tv_title)
        val artist: TextView = view.findViewById(R.id.tv_artist)
    }

    class SongDiffCallback : DiffUtil.ItemCallback<LocalSong>() {
        override fun areItemsTheSame(old: LocalSong, new: LocalSong): Boolean =
            old.id == new.id
        override fun areContentsTheSame(old: LocalSong, new: LocalSong): Boolean =
            old == new
    }
}
