package com.musicdownloader.ui

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
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
import com.musicdownloader.databinding.FragmentSongListBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

class SongListFragment : Fragment() {

    private var _binding: FragmentSongListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongItemAdapter
    private lateinit var repository: MusicRepository
    private lateinit var playerViewModel: PlayerViewModel
    private var collectJob: Job? = null
    private var folderPath: String = ""
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderPath = arguments?.getString(ARG_FOLDER_PATH, "").orEmpty()
        searchQuery = arguments?.getString(ARG_SEARCH_QUERY, "").orEmpty()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSongListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = MusicRepository(requireContext())
        playerViewModel = PlayerViewModel.getInstance(requireActivity().application as Application)

        adapter = SongItemAdapter(
            onItemClick = { song ->
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
            },
                    onLongClick = { song ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Reset Waveform")
                    .setMessage("Regenerar waveform de '${song.title}'?")
                    .setPositiveButton("Reset") { _, _ ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                repository.resetWaveform(song)
                            }
                            android.widget.Toast.makeText(requireContext(), "Waveform regenerado", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )

        binding.rvSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSongs.adapter = adapter

        if (folderPath.isNotBlank()) {
            binding.spinnerSort.visibility = View.GONE
            binding.tvListTitle.text =
                folderPath.substringAfterLast(File.separator).ifBlank { folderPath }
            collectFolderSongs()
        } else if (searchQuery.isNotBlank()) {
            binding.spinnerSort.visibility = View.GONE
            binding.tvListTitle.text =
                getString(R.string.library_search)
            collectSearchResults()
        } else {
            setupSortSpinner()
        }
    }

    private fun collectSearchResults() {
        collectJob?.cancel()
        collectJob = lifecycleScope.launch {
            repository.getAllSongs().collectLatest { songs ->
                val query = searchQuery.trim().lowercase()
                val filtered = songs.filter { song ->
                    song.title.lowercase().contains(query) ||
                        song.artist.lowercase().contains(query) ||
                        song.album.lowercase().contains(query)
                }
                adapter.submitList(filtered)
                if (filtered.isEmpty()) {
                    binding.rvSongs.visibility = View.GONE
                    binding.llSongListEmpty.visibility = View.VISIBLE
                } else {
                    binding.rvSongs.visibility = View.VISIBLE
                    binding.llSongListEmpty.visibility = View.GONE
                }
            }
        }
    }

    private fun collectFolderSongs() {
        collectJob?.cancel()
        collectJob = lifecycleScope.launch {
            repository.getSongsInFolder(folderPath).collectLatest { songs ->
                adapter.submitList(songs)
                if (songs.isEmpty()) {
                    binding.rvSongs.visibility = View.GONE
                    binding.llSongListEmpty.visibility = View.VISIBLE
                } else {
                    binding.rvSongs.visibility = View.VISIBLE
                    binding.llSongListEmpty.visibility = View.GONE
                }
            }
        }
    }

    private fun setupSortSpinner() {
        val spinner = binding.spinnerSort
        spinner.visibility = View.VISIBLE
        val options = resources.getStringArray(R.array.sort_options)
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            options
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(PREFS_SORT_ORDER, SORT_TITLE) ?: SORT_TITLE
        spinner.setSelection(sortKeys.indexOf(saved).coerceAtLeast(0))

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val key = sortKeys[position]
                prefs.edit().putString(PREFS_SORT_ORDER, key).apply()
                collectSongs(key)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun collectSongs(sortKey: String) {
        collectJob?.cancel()
        collectJob = lifecycleScope.launch {
            val flow = when (sortKey) {
                SORT_ARTIST -> repository.getAllSongsByArtist()
                SORT_ALBUM -> repository.getAllSongsByAlbum()
                SORT_DURATION -> repository.getAllSongsByDuration()
                else -> repository.getAllSongsByTitle()
            }
            flow.collectLatest { songs ->
                adapter.submitList(songs)
                if (songs.isEmpty()) {
                    binding.rvSongs.visibility = View.GONE
                    binding.llSongListEmpty.visibility = View.VISIBLE
                } else {
                    binding.rvSongs.visibility = View.VISIBLE
                    binding.llSongListEmpty.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        collectJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_FOLDER_PATH = "folderPath"
        private const val ARG_SEARCH_QUERY = "searchQuery"
        private const val PREFS_NAME = "player_prefs"
        private const val PREFS_SORT_ORDER = "sort_order"
        private const val SORT_TITLE = "title"
        private const val SORT_ARTIST = "artist"
        private const val SORT_ALBUM = "album"
        private const val SORT_DURATION = "duration"
        private val sortKeys = listOf(SORT_TITLE, SORT_ARTIST, SORT_ALBUM, SORT_DURATION)
    }
}

class SongItemAdapter(
    private val onItemClick: (LocalSong) -> Unit,
    private val onLongClick: ((LocalSong) -> Unit)? = null
) : ListAdapter<LocalSong, SongItemAdapter.ViewHolder>(SongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = getItem(position)
        holder.title.text = song.title
        holder.artist.text = song.artist.ifBlank { holder.itemView.context.getString(R.string.unknown_artist) }

        val playerRes = IconPackManager.getAppIconResIds(ThemeManager.currentIconPack)[IconPackManager.ICON_PLAYER] ?: R.drawable.ic_player
        if (song.thumbnailUrl.isNotBlank() && File(song.thumbnailUrl).exists()) {
            holder.thumbnail.tag = null
            holder.thumbnail.load(File(song.thumbnailUrl)) {
                crossfade(true)
                placeholder(playerRes)
                error(playerRes)
            }
        } else if (song.filePath.isNotBlank() && File(song.filePath).exists()) {
            holder.thumbnail.tag = song.filePath
            ArtworkLoader.loadArtFromAudioFile(holder.thumbnail, song.filePath)
        } else {
            holder.thumbnail.tag = null
            holder.thumbnail.setImageResource(playerRes)
        }

        holder.itemView.setOnClickListener { onItemClick(song) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(song)
            true
        }
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
