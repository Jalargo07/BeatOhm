package com.beatohm.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.beatohm.data.toSong
import coil.load
import com.beatohm.MetadataRegenService
import com.beatohm.R
import com.beatohm.data.LocalSong
import com.beatohm.data.IMusicRepository
import com.beatohm.data.IWaveformRepository
import com.beatohm.data.ILibraryRepository
import com.beatohm.data.IRegenRepository
import com.beatohm.data.MusicRepository
import com.beatohm.data.LibraryRepository
import com.beatohm.data.RegenRepository
import com.beatohm.data.WaveformRepository
import com.beatohm.data.PlaylistRepository
import com.beatohm.databinding.FragmentSongListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

class SongListFragment : Fragment() {

    private var _binding: FragmentSongListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongItemAdapter
    private lateinit var repository: IMusicRepository
    private lateinit var libraryRepo: ILibraryRepository
    private lateinit var waveformRepo: IWaveformRepository
    private lateinit var regenRepo: IRegenRepository
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var playerViewModel: PlayerViewModel
    private var collectJob: Job? = null
    private var folderPath: String = ""
    private var searchQuery: String = ""
    private var isRegenActive = false
    private var currentSortKey: String = SORT_TITLE

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
        libraryRepo = LibraryRepository(requireContext())
        waveformRepo = WaveformRepository(requireContext())
        regenRepo = RegenRepository(requireContext())
        playlistRepository = PlaylistRepository(requireContext())
        playerViewModel = PlayerViewModel.getInstance(requireActivity().application as Application)

        regenRepo.regenProgress.observe(viewLifecycleOwner) { progress ->
            if (progress != null) {
                binding.llProgress.visibility = View.VISIBLE
                binding.tvProgress.text = getString(R.string.regenerando, progress.first, progress.second)
            } else {
                binding.llProgress.visibility = View.GONE
                if (isRegenActive) {
                    isRegenActive = false
                    collectSongs(currentSortKey)
                }
            }
        }

        // Restore progress if regen is still running (survives navigation)
        regenRepo.regenProgress.value?.let { progress ->
            binding.llProgress.visibility = View.VISIBLE
            binding.tvProgress.text = getString(R.string.regenerando, progress.first, progress.second)
        }

        adapter = SongItemAdapter(
            onItemClick = { song ->
                val activity = requireActivity() as? com.beatohm.MainActivity ?: return@SongItemAdapter
                val service = activity.playbackService
                if (service != null) {
                    val songs = adapter.currentList
                    val index = songs.indexOf(song)
                    playerViewModel.setPlaylist(
                        songs.map { it.toSong() },
                        if (index >= 0) index else 0
                    )
                    service.playFile(song.filePath, isManual = true)
                }
            },
                    onLongClick = { song ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.reset_waveform))
                    .setMessage(getString(R.string.regenerar_waveform, song.title))
                    .setPositiveButton(getString(R.string.reset_waveform)) { _, _ ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                waveformRepo.resetWaveform(song)
                            }
                            android.widget.Toast.makeText(requireContext(), getString(R.string.waveform_regenerado), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        )

        binding.rvSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSongs.adapter = adapter

        adapter.onSelectionChanged = { count ->
            if (adapter.isMultiSelectMode && count > 0) {
                binding.llMultiSelectBar.visibility = View.VISIBLE
                binding.tvSelectedCount.text = if (count > 1) getString(R.string.seleccionadas, count) else getString(R.string.seleccionada, count)
            } else {
                binding.llMultiSelectBar.visibility = View.GONE
            }
        }

        binding.btnCloseMultiSelect.setOnClickListener {
            adapter.deselectAll()
        }

        binding.btnSelectAll.setOnClickListener {
            if (adapter.getSelectedSongs().size == adapter.currentList.size) {
                adapter.deselectAll()
            } else {
                adapter.selectAll()
            }
        }

        binding.btnAddToPlaylist.setOnClickListener {
            val selected = adapter.getSelectedSongs()
            if (selected.isEmpty()) return@setOnClickListener
            showAddToPlaylistDialog(selected)
        }

        binding.btnRegenerateMetadata.setOnClickListener {
            val selected = adapter.getSelectedSongs()
            if (selected.isEmpty()) return@setOnClickListener
            regenerateMetadata(selected)
        }

        binding.btnDeleteSongs.setOnClickListener {
            val selected = adapter.getSelectedSongs()
            if (selected.isEmpty()) return@setOnClickListener
            confirmDelete(selected)
        }

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
            libraryRepo.getSongsInFolder(folderPath).collectLatest { songs ->
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
        currentSortKey = saved
        spinner.setSelection(sortKeys.indexOf(saved).coerceAtLeast(0))

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val key = sortKeys[position]
                currentSortKey = key
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
                val filtered = if (isRegenActive) {
                    val pendingFailedIds = withContext(Dispatchers.IO) {
                        regenRepo.getPendingAndFailedSongs().first().map { it.songId }.toSet()
                    }
                    songs.filter { it.id in pendingFailedIds }
                } else {
                    songs
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

    private fun showAddToPlaylistDialog(songs: List<LocalSong>) {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { playlistRepository.getAllPlaylists().first() }
            if (list.isEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.sin_playlists))
                    .setMessage(getString(R.string.crea_playlist_primero))
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }
            val names = list.map { it.name }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.agregar_canciones_a_playlist, songs.size, if (songs.size > 1) "s" else ""))
                .setItems(names) { _, which ->
                    val playlist = list[which]
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            for (song in songs) {
                                playlistRepository.addSongToPlaylist(playlist.id, song.filePath)
                            }
                        }
                        android.widget.Toast.makeText(
                            requireContext(),
                            "${songs.size} cancione${if (songs.size > 1) "s" else ""} agregada${if (songs.size > 1) "s" else ""} a '${playlist.name}'",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        adapter.deselectAll()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun regenerateMetadata(songs: List<LocalSong>) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_regen_options, null)
        val checkMetadata = dialogView.findViewById<android.widget.CheckBox>(R.id.check_metadata)
        val checkLyrics = dialogView.findViewById<android.widget.CheckBox>(R.id.check_lyrics)
        val checkWaveform = dialogView.findViewById<android.widget.CheckBox>(R.id.check_waveform)
        val checkArtwork = dialogView.findViewById<android.widget.CheckBox>(R.id.check_artwork)
        val checkColor = dialogView.findViewById<android.widget.CheckBox>(R.id.check_color)
        val btnRetryFailed = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_retry_failed)

        lifecycleScope.launch {
            val pendingFailedCount = withContext(Dispatchers.IO) { regenRepo.getPendingAndFailedCount() }
            if (pendingFailedCount > 0) {
                btnRetryFailed.visibility = View.VISIBLE
                btnRetryFailed.text = getString(R.string.regen_retry_failed, pendingFailedCount)
                btnRetryFailed.setOnClickListener {
                    lifecycleScope.launch {
                        val pendingFailedSongs = withContext(Dispatchers.IO) { regenRepo.getPendingAndFailedSongsNow() }
                        if (pendingFailedSongs.isNotEmpty()) {
                            val pendingFailedSongIds = pendingFailedSongs.map { it.songId }.toTypedArray()
                            val intent = Intent(requireContext(), MetadataRegenService::class.java).apply {
                                action = MetadataRegenService.ACTION_START
                                putExtra(MetadataRegenService.EXTRA_SONG_IDS, pendingFailedSongIds)
                                putExtra(MetadataRegenService.EXTRA_DO_METADATA, checkMetadata.isChecked)
                                putExtra(MetadataRegenService.EXTRA_DO_LYRICS, checkLyrics.isChecked)
                                putExtra(MetadataRegenService.EXTRA_DO_WAVEFORM, checkWaveform.isChecked)
                                putExtra(MetadataRegenService.EXTRA_DO_ARTWORK, checkArtwork.isChecked)
                                putExtra(MetadataRegenService.EXTRA_DO_COLOR, checkColor.isChecked)
                            }
                            ContextCompat.startForegroundService(requireContext(), intent)
                            isRegenActive = true
                            collectSongs(currentSortKey)
                            adapter.deselectAll()
                        }
                    }
                }
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.regen_select_title))
            .setView(dialogView)
            .setPositiveButton("Regenerar") { _, _ ->
                val doMetadata = checkMetadata.isChecked
                val doLyrics = checkLyrics.isChecked
                val doWaveform = checkWaveform.isChecked
                val doArtwork = checkArtwork.isChecked
                val doColor = checkColor.isChecked

                if (!doMetadata && !doLyrics && !doWaveform && !doArtwork && !doColor) return@setPositiveButton

                adapter.deselectAll()
                val songIds = songs.map { it.id }.toTypedArray()
                val intent = Intent(requireContext(), MetadataRegenService::class.java).apply {
                    action = MetadataRegenService.ACTION_START
                    putExtra(MetadataRegenService.EXTRA_SONG_IDS, songIds)
                    putExtra(MetadataRegenService.EXTRA_DO_METADATA, doMetadata)
                    putExtra(MetadataRegenService.EXTRA_DO_LYRICS, doLyrics)
                    putExtra(MetadataRegenService.EXTRA_DO_WAVEFORM, doWaveform)
                    putExtra(MetadataRegenService.EXTRA_DO_ARTWORK, doArtwork)
                    putExtra(MetadataRegenService.EXTRA_DO_COLOR, doColor)
                }
                ContextCompat.startForegroundService(requireContext(), intent)
                isRegenActive = true
                collectSongs(currentSortKey)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDelete(songs: List<LocalSong>) {
        val suffix = if (songs.size > 1) "s" else ""
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_songs_title))
            .setMessage(getString(R.string.delete_songs_message, songs.size, suffix))
            .setPositiveButton(getString(R.string.delete_confirm)) { _, _ ->
                requireActivity().lifecycleScope.launch {
                    var deleted = 0
                    for (song in songs) {
                        withContext(Dispatchers.IO) {
                            try {
                                val file = File(song.filePath)
                                if (file.exists()) file.delete()
                            } catch (_: Exception) {}
                            repository.deleteSong(song)
                        }
                        deleted++
                    }
                    android.widget.Toast.makeText(
                        requireContext(),
                        getString(R.string.delete_songs_done, deleted, suffix),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    adapter.deselectAll()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        collectJob?.cancel()
        isRegenActive = false
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

    private val selectedIds = mutableSetOf<String>()
    var isMultiSelectMode = false
        private set
    var onSelectionChanged: ((count: Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = getItem(position)
        holder.title.text = song.title
        holder.artist.text = song.artist.ifBlank { holder.itemView.context.getString(R.string.unknown_artist) }

        val packId = ThemeManager.currentIconPack
        val colorAware = IconPackManager.isColorAwarePack(packId)
        val playerRes = IconPackManager.getAppIconResIds(packId)[IconPackManager.ICON_PLAYER] ?: R.drawable.ic_player
        val playerDrawable = if (colorAware) IconPackManager.getIcon(IconPackManager.ICON_PLAYER, packId, holder.itemView.context) else null
        if (song.thumbnailUrl.isNotBlank() && File(song.thumbnailUrl).exists()) {
            holder.thumbnail.tag = null
            holder.thumbnail.load(File(song.thumbnailUrl)) {
                crossfade(true)
                if (playerDrawable != null) {
                    placeholder(playerDrawable)
                    error(playerDrawable)
                } else {
                    placeholder(playerRes)
                    error(playerRes)
                }
            }
        } else if (song.filePath.isNotBlank() && File(song.filePath).exists()) {
            holder.thumbnail.tag = song.filePath
            ArtworkLoader.loadArtFromAudioFile(holder.thumbnail, song.filePath)
        } else {
            holder.thumbnail.tag = null
            if (playerDrawable != null) {
                holder.thumbnail.setImageDrawable(playerDrawable)
                holder.thumbnail.imageTintList = null
            } else {
                holder.thumbnail.setImageResource(playerRes)
            }
        }

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = selectedIds.contains(song.id)
        holder.checkBox.setOnCheckedChangeListener { _, _ ->
            toggleSelection(song)
        }

        holder.itemView.setOnClickListener {
            if (isMultiSelectMode) {
                toggleSelection(song)
            } else {
                onItemClick(song)
            }
        }
        holder.itemView.setOnLongClickListener {
            if (!isMultiSelectMode) {
                isMultiSelectMode = true
                selectedIds.add(song.id)
                notifyDataSetChanged()
                onSelectionChanged?.invoke(selectedIds.size)
            } else {
                onLongClick?.invoke(song)
            }
            true
        }
    }

    private fun toggleSelection(song: LocalSong) {
        if (selectedIds.contains(song.id)) {
            selectedIds.remove(song.id)
        } else {
            selectedIds.add(song.id)
        }
        if (selectedIds.isEmpty()) {
            isMultiSelectMode = false
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(currentList.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun deselectAll() {
        selectedIds.clear()
        isMultiSelectMode = false
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun getSelectedSongs(): List<LocalSong> {
        return currentList.filter { selectedIds.contains(it.id) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.iv_thumbnail)
        val title: TextView = view.findViewById(R.id.tv_title)
        val artist: TextView = view.findViewById(R.id.tv_artist)
        val checkBox: android.widget.CheckBox = view.findViewById(R.id.cb_select)
    }

    class SongDiffCallback : DiffUtil.ItemCallback<LocalSong>() {
        override fun areItemsTheSame(old: LocalSong, new: LocalSong): Boolean =
            old.id == new.id
        override fun areContentsTheSame(old: LocalSong, new: LocalSong): Boolean =
            old == new
    }
}
