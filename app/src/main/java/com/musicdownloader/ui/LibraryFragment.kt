package com.musicdownloader.ui

import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.musicdownloader.R
import com.musicdownloader.data.LocalSong
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.data.toSong
import com.musicdownloader.databinding.FragmentLibraryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private lateinit var libraryViewModel: LibraryViewModel
    private lateinit var repository: MusicRepository
    private lateinit var adapter: LibraryMenuAdapter
    private lateinit var favoriteAdapter: FavoriteSongAdapter
    private val categoryCounts = mutableMapOf<String, Int>()
    private var enrichmentTotal = 0
    private var wasEnriching = false
    private var hasScanned = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        libraryViewModel = ViewModelProvider(requireActivity())[LibraryViewModel::class.java]
        repository = MusicRepository(requireContext())

        adapter = LibraryMenuAdapter(
            onCategoryClick = { category -> navigateToCategory(category) },
            onFolderClick = { path -> navigateToFolder(path) }
        )

        val gridLayoutManager = GridLayoutManager(requireContext(), GRID_COLUMNS).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (adapter.currentList[position] is LibraryMenuItem.Category) 1 else GRID_COLUMNS
                }
            }
        }
        binding.rvLibraryCategories.layoutManager = gridLayoutManager
        binding.rvLibraryCategories.adapter = adapter

        favoriteAdapter = FavoriteSongAdapter(
            onPlay = { song -> playSong(song) },
            onToggleFavorite = { song, isFavorite -> toggleFavorite(song, isFavorite) }
        )
        binding.rvLibraryFavorites.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvLibraryFavorites.adapter = favoriteAdapter

        TutorialManager.showTutorial(
            requireActivity(),
            "library",
            listOf(
                TutorialManager.TooltipStep({ binding.etLibrarySearch }, getString(R.string.tutorial_lib_search), getString(R.string.tutorial_lib_search_desc)),
                TutorialManager.TooltipStep({ binding.rvLibraryCategories }, getString(R.string.tutorial_lib_categories), getString(R.string.tutorial_lib_categories_desc)),
                TutorialManager.TooltipStep({ binding.rvLibraryCategories }, getString(R.string.tutorial_lib_folders), getString(R.string.tutorial_lib_folders_desc)),
                TutorialManager.TooltipStep({ binding.btnEnrichManual }, getString(R.string.tutorial_lib_enrich), getString(R.string.tutorial_lib_enrich_desc))
            )
        )

        libraryViewModel.folders.observe(viewLifecycleOwner) { submitLibraryItems() }

        libraryViewModel.isScanning.observe(viewLifecycleOwner) { scanning ->
            binding.pbLibraryScan.visibility = if (scanning) View.VISIBLE else View.GONE
            updateLibraryEmptyState()
        }

        libraryViewModel.allSongs.observe(viewLifecycleOwner) { updateLibraryEmptyState() }

        observeFavorites()
        observeCategoryCounts()
        observeEnrichment()
        observeIncompleteCount()
        setupSearch()

        binding.tvSeeAllFavorites.setOnClickListener {
            findNavController().navigate(R.id.favoritesFragment)
        }

        if (!hasScanned) {
            libraryViewModel.refreshLibrary()
            hasScanned = true
        }
    }

    override fun onResume() {
        super.onResume()
        libraryViewModel.refreshFolders()
    }

    private fun submitLibraryItems() {
        val items = buildList {
            add(LibraryMenuItem.Section(R.string.your_library))
            getCategories().forEach { category ->
                add(LibraryMenuItem.Category(category.copy(count = categoryCounts[category.id] ?: 0)))
            }
            val current = libraryViewModel.folders.value ?: emptyList()
            if (current.isNotEmpty()) {
                add(LibraryMenuItem.Section(R.string.my_folders))
                current.forEach { add(LibraryMenuItem.Folder(it)) }
            }
        }
        adapter.submitList(items)
    }

    private fun updateLibraryEmptyState() {
        val scanning = libraryViewModel.isScanning.value == true
        val isEmpty = libraryViewModel.allSongs.value.orEmpty().isEmpty()
        val showEmpty = hasScanned && !scanning && isEmpty
        binding.llLibraryEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE
        binding.rvLibraryCategories.visibility = if (showEmpty) View.GONE else View.VISIBLE
        binding.llFavoritesHeader.visibility = if (showEmpty) View.GONE else View.VISIBLE
    }

    private fun observeFavorites() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getFavoriteSongs().collectLatest { songs ->
                val shown = songs.take(MAX_FAVORITES)
                favoriteAdapter.submitList(shown)
                binding.rvLibraryFavorites.visibility = if (shown.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun observeCategoryCounts() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                repository.getSongCount().collectLatest {
                    categoryCounts["songs"] = it
                    submitLibraryItems()
                }
            }
            launch {
                repository.getAllAlbums().collectLatest {
                    categoryCounts["albums"] = it.size
                    submitLibraryItems()
                }
            }
            launch {
                repository.getAllArtists().collectLatest {
                    categoryCounts["artists"] = it.size
                    submitLibraryItems()
                }
            }
            launch {
                repository.getAllGenres().collectLatest {
                    categoryCounts["genres"] = it.size
                    submitLibraryItems()
                }
            }
        }
    }

    private fun setupSearch() {
        binding.etLibrarySearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etLibrarySearch.text?.toString()?.trim().orEmpty()
                if (query.isNotBlank()) {
                    findNavController().navigate(R.id.songListFragment, bundleOf("searchQuery" to query))
                }
                true
            } else {
                false
            }
        }
    }

    private fun playSong(song: LocalSong) {
        val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return
        val service = activity.playbackService ?: return
        val vm = PlayerViewModel.getInstance(requireActivity().application as Application)
        val songs = favoriteAdapter.currentList
        val index = songs.indexOf(song)
        vm.setPlaylist(
            songs.map { it.toSong() },
            if (index >= 0) index else 0
        )
        service.playFile(song.filePath)
    }

    private fun toggleFavorite(song: LocalSong, isFavorite: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.setFavorite(song.id, isFavorite)
        }
    }

    private fun navigateToFolder(path: String) {
        findNavController().navigate(R.id.songListFragment, bundleOf("folderPath" to path))
    }

    private fun navigateToCategory(category: LibraryCategory) {
        val navController = findNavController()
        when (category.id) {
            "songs" -> navController.navigate(R.id.songListFragment)
            "artists" -> navController.navigate(R.id.categoryListFragment, bundleOf("category" to "artist"))
            "genres" -> navController.navigate(R.id.categoryListFragment, bundleOf("category" to "genre"))
            "albums" -> navController.navigate(R.id.categoryListFragment, bundleOf("category" to "album"))
            "playlists" -> navController.navigate(R.id.playlistsFragment)
            "favorites" -> navController.navigate(R.id.favoritesFragment)
            "most_played" -> navController.navigate(R.id.mostPlayedFragment)
            "folders" -> navController.navigate(R.id.foldersFragment)
        }
    }

    private fun observeIncompleteCount() {
        binding.btnEnrichManual.visibility = View.VISIBLE
        binding.btnEnrichManual.text = getString(R.string.enrich_manual)
        binding.btnEnrichManual.setOnClickListener {
            showSongSelectorDialog()
        }
    }

    private fun showSongSelectorDialog() {
        val allSongs = libraryViewModel.allSongs.value.orEmpty()
        if (allSongs.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_songs_available, Toast.LENGTH_SHORT).show()
            return
        }

        val incompleteIds = if (libraryViewModel.pendingIncompleteSongs.isNotEmpty()) {
            libraryViewModel.pendingIncompleteSongs.map { it.id }.toSet()
        } else {
            allSongs.filter { libraryViewModel.isIncomplete(it) }.map { it.id }.toSet()
        }
        val adapter = SongSelectorAdapter(allSongs, incompleteIds)

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_song_selector, null)

        val rvSelector = dialogView.findViewById<RecyclerView>(R.id.rv_song_selector)
        rvSelector.layoutManager = LinearLayoutManager(requireContext())
        rvSelector.adapter = adapter

        val tvCount = dialogView.findViewById<TextView>(R.id.tv_selection_count)
        val btnSelectAll = dialogView.findViewById<TextView>(R.id.btn_select_all)
        val cbLyrics = dialogView.findViewById<MaterialCheckBox>(R.id.cb_fetch_lyrics)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_songs_title)
            .setView(dialogView)
            .setPositiveButton(R.string.enrich_selected) { _, _ ->
                val selected = adapter.getSelectedSongs()
                if (selected.isNotEmpty()) {
                    libraryViewModel.startEnrichment(selected, cbLyrics.isChecked)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()

        fun updateCount() {
            val count = adapter.getSelectedCount()
            tvCount.text = getString(R.string.select_songs_selected_count, count, allSongs.size)
            btnSelectAll.text = getString(if (count == allSongs.size) R.string.deselect_all else R.string.select_all)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = count > 0
        }

        adapter.onSelectionChanged = { updateCount() }
        btnSelectAll.setOnClickListener {
            if (adapter.getSelectedCount() == allSongs.size) {
                adapter.deselectAll()
            } else {
                adapter.selectAll()
            }
        }
        updateCount()
    }

    private fun observeEnrichment() {
        libraryViewModel.offerEnrichment.observe(viewLifecycleOwner) { count ->
            if (count != null && count > 0) {
                val dialogView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_enrich_offer, null)
                dialogView.findViewById<TextView>(R.id.tv_offer_message).text =
                    getString(R.string.enrich_dialog_message, count)
                val cbOfferLyrics = dialogView.findViewById<MaterialCheckBox>(R.id.cb_offer_lyrics)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.enrich_dialog_title)
                    .setView(dialogView)
                    .setPositiveButton(R.string.enrich_accept) { _, _ ->
                        libraryViewModel.startEnrichment(cbOfferLyrics.isChecked)
                    }
                    .setNegativeButton(R.string.enrich_decline) { _, _ ->
                        libraryViewModel.dismissEnrichmentOffer()
                    }
                    .setCancelable(false)
                    .show()
            }
        }

        libraryViewModel.enrichmentProgress.observe(viewLifecycleOwner) { progress ->
            if (progress != null) {
                wasEnriching = true
                enrichmentTotal = progress.total
                binding.progressEnrich.visibility = View.VISIBLE
                binding.progressEnrich.max = progress.total
                binding.progressEnrich.progress = progress.done
                binding.tvEnrichProgress.visibility = View.VISIBLE
                binding.tvEnrichProgress.text = if (progress.withLyrics) {
                    getString(R.string.enrich_progress_with_lyrics, progress.done, progress.total)
                } else {
                    getString(R.string.enrich_progress, progress.done, progress.total)
                }
            } else {
                binding.progressEnrich.visibility = View.GONE
                binding.tvEnrichProgress.visibility = View.GONE
                if (wasEnriching) {
                    wasEnriching = false
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.enrich_done, enrichmentTotal),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val GRID_COLUMNS = 2
        private const val MAX_FAVORITES = 5

        fun getCategories(): List<LibraryCategory> {
            val icons = IconPackManager.getLibraryCategoryIconResIds(ThemeManager.currentIconPack)
            return listOf(
                LibraryCategory("songs", R.string.songs, icons[IconPackManager.ICON_MUSIC_NOTE] ?: R.drawable.ic_music_note),
                LibraryCategory("artists", R.string.by_artist, icons[IconPackManager.ICON_MIC] ?: R.drawable.ic_mic),
                LibraryCategory("genres", R.string.by_genre, icons[IconPackManager.ICON_GENRES] ?: R.drawable.ic_genres),
                LibraryCategory("albums", R.string.albums, icons[IconPackManager.ICON_ALBUM] ?: R.drawable.ic_album),
                LibraryCategory("playlists", R.string.playlists_menu, icons[IconPackManager.ICON_PLAYLIST] ?: R.drawable.ic_playlist),
                LibraryCategory("favorites", R.string.favorites, icons[IconPackManager.ICON_HEART] ?: R.drawable.ic_favorite),
                LibraryCategory("most_played", R.string.most_played, icons[IconPackManager.ICON_TRENDING] ?: R.drawable.ic_trending_up),
                LibraryCategory("folders", R.string.folders, icons[IconPackManager.ICON_FOLDER] ?: R.drawable.ic_folder)
            )
        }
    }
}
