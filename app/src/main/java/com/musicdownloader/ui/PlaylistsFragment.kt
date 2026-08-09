package com.musicdownloader.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.R
import com.musicdownloader.data.AppDatabase
import com.musicdownloader.data.Playlist
import com.musicdownloader.databinding.FragmentPlaylistsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaylistsFragment : Fragment() {
    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PlaylistAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = AppDatabase.getInstance(requireContext())
        val playlistDao = db.playlistDao()

        adapter = PlaylistAdapter { playlist ->
            findNavController().navigate(
                R.id.playlistDetailFragment,
                bundleOf(
                    PlaylistDetailFragment.ARG_PLAYLIST_ID to playlist.id,
                    PlaylistDetailFragment.ARG_PLAYLIST_NAME to playlist.name
                )
            )
        }
        binding.rvPlaylists.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlaylists.adapter = adapter

        binding.fabAddPlaylist.setOnClickListener {
            val input = EditText(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.nueva_playlist))
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
                binding.tvEmpty.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
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
            .inflate(R.layout.item_playlist, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = getItem(position)
        holder.icon.text = p.name.take(1).uppercase()
        holder.title.text = p.name
        holder.subtitle.text = holder.itemView.context.getString(R.string.canciones_count, p.songCount)
        holder.itemView.setOnClickListener { onClick(p) }
    }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.tv_playlist_icon)
        val title: TextView = view.findViewById(R.id.tv_playlist_name)
        val subtitle: TextView = view.findViewById(R.id.tv_playlist_count)
    }
}
