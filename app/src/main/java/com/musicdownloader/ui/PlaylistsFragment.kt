package com.musicdownloader.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.musicdownloader.R
import com.musicdownloader.PlaylistDetailActivity
import com.musicdownloader.data.AppDatabase
import com.musicdownloader.data.Playlist
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaylistsFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var adapter: PlaylistAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_playlists, container, false)
        recyclerView = view.findViewById(R.id.rv_playlists)
        emptyView = view.findViewById(R.id.tv_empty)
        fab = view.findViewById(R.id.fab_add_playlist)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = AppDatabase.getInstance(requireContext())
        val playlistDao = db.playlistDao()

        adapter = PlaylistAdapter { playlist ->
            PlaylistDetailActivity.playlistId = playlist.id
            PlaylistDetailActivity.playlistName = playlist.name
            startActivity(android.content.Intent(requireContext(), PlaylistDetailActivity::class.java))
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        fab.setOnClickListener {
            val input = EditText(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle("Nueva Playlist")
                .setView(input)
                .setPositiveButton("Crear") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotBlank()) {
                        lifecycleScope.launch {
                            playlistDao.createPlaylist(Playlist(name = name))
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        lifecycleScope.launch {
            playlistDao.getAllPlaylists().collectLatest { playlists ->
                adapter.submitList(playlists)
                emptyView.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}

class PlaylistAdapter(private val onClick: (Playlist) -> Unit) :
    ListAdapter<Playlist, PlaylistAdapter.ViewHolder>(
        object : DiffUtil.ItemCallback<Playlist>() {
            override fun areItemsTheSame(a: Playlist, b: Playlist) = a.id == b.id
            override fun areContentsTheSame(a: Playlist, b: Playlist) = a == b
        }
    ) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = getItem(position)
        holder.title.text = p.name
        holder.subtitle.text = "${p.songCount} canciones"
        holder.itemView.setOnClickListener { onClick(p) }
    }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
        val subtitle: TextView = view.findViewById(android.R.id.text2)
    }
}
