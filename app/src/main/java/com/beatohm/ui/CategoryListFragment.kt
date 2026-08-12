package com.beatohm.ui

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.beatohm.data.toSong
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.beatohm.R
import com.beatohm.data.LocalSong
import com.beatohm.data.MusicRepository
import com.beatohm.data.ILibraryRepository
import com.beatohm.data.LibraryRepository
import com.beatohm.databinding.FragmentCategoryListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import coil.load
import java.io.File

data class CategoryItem(val name: String, val coverPath: String = "")

class CategoryListFragment : Fragment() {

    private var _binding: FragmentCategoryListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CategoryAdapter
    private lateinit var songsAdapter: FilteredSongAdapter
    private lateinit var repository: MusicRepository
    private lateinit var libraryRepo: ILibraryRepository
    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var categoryType: String

    private var showingSongs = false
    private var selectedCategoryValue = ""
    private var scrollPosition = 0
    private var scrollOffset = 0
    private var coverDialogJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categoryType = arguments?.getString("category", "album") ?: "album"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val lm = binding.rvCategories.layoutManager as? LinearLayoutManager
        if (lm != null) {
            val pos = lm.findFirstVisibleItemPosition()
            val view = lm.findViewByPosition(pos)
            val offset = view?.top ?: 0
            outState.putInt("scroll_position", pos)
            outState.putInt("scroll_offset", offset)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = MusicRepository(requireContext())
        libraryRepo = LibraryRepository(requireContext())
        playerViewModel = PlayerViewModel.getInstance(requireActivity().application as Application)

        songsAdapter = FilteredSongAdapter(onItemClick = { song ->
            val activity = requireActivity() as? com.beatohm.MainActivity ?: return@FilteredSongAdapter
            val service = activity.playbackService
            if (service != null) {
                val songs = songsAdapter.currentList
                val index = songs.indexOf(song)
                playerViewModel.setPlaylist(
                    songs.map { it.toSong() },
                    if (index >= 0) index else 0
                )
                service.playFile(song.filePath)
            }
        })

        adapter = CategoryAdapter(
            onItemClick = { name ->
                val lm = binding.rvCategories.layoutManager as? LinearLayoutManager
                if (lm != null) {
                    scrollPosition = lm.findFirstVisibleItemPosition()
                    val v = lm.findViewByPosition(scrollPosition)
                    scrollOffset = v?.top ?: 0
                }
                selectedCategoryValue = name
                showingSongs = true
                binding.headerBar.visibility = View.VISIBLE
                binding.tvCategoryTitle.text = name
                loadSongsForCategory(name)
            },
            onItemLongClick = { item ->
                if (categoryType == "album") {
                    showAlbumCoverDialog(item.name)
                }
            },
            defaultIconRes = when (categoryType) {
                "album" -> IconPackManager.getAppIconResIds(ThemeManager.currentIconPack)[IconPackManager.ICON_ALBUM] ?: R.drawable.ic_album
                "artist" -> IconPackManager.getAppIconResIds(ThemeManager.currentIconPack)[IconPackManager.ICON_MIC] ?: R.drawable.ic_mic
                else -> IconPackManager.getAppIconResIds(ThemeManager.currentIconPack)[IconPackManager.ICON_ALBUM] ?: R.drawable.ic_album
            }
        )

        binding.rvCategories.layoutManager = LinearLayoutManager(requireContext())

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showingSongs) {
                    showingSongs = false
                    binding.headerBar.visibility = View.GONE
                    binding.rvCategories.adapter = adapter
                    loadCategories()
                    restoreScrollPosition()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        loadCategories()

        if (savedInstanceState != null) {
            scrollPosition = savedInstanceState.getInt("scroll_position", 0)
            scrollOffset = savedInstanceState.getInt("scroll_offset", 0)
            restoreScrollPosition()
        }
    }

    private fun restoreScrollPosition() {
        binding.rvCategories.post {
            val lm = binding.rvCategories.layoutManager as? LinearLayoutManager ?: return@post
            if (scrollPosition > 0) {
                lm.scrollToPositionWithOffset(scrollPosition, scrollOffset)
            }
        }
    }

    private fun loadCategories() {
        binding.rvCategories.adapter = adapter
        lifecycleScope.launch {
            when (categoryType) {
                "album" -> {
                    repository.getAllAlbums().combine(repository.getAllAlbumsWithCover()) { allAlbums, covers ->
                        val coverMap = covers.associateBy { it.name }
                        val overrideMap = allAlbums.associateWith { libraryRepo.getAlbumCoverOverride(it) }
                        allAlbums.map { name ->
                            val cover = overrideMap[name]
                                ?: coverMap[name]?.coverPath
                                ?: ""
                            CategoryItem(name, cover)
                        }
                    }.collectLatest { data ->
                        adapter.submitList(data)
                        updateEmptyState(data.isEmpty())
                    }
                }
                "artist" -> {
                    repository.getAllArtists().combine(repository.getAllArtistsWithCover()) { allArtists, covers ->
                        val coverMap = covers.associateBy { it.name }
                        allArtists.map { name ->
                            CategoryItem(name, coverMap[name]?.coverPath ?: "")
                        }
                    }.collectLatest { data ->
                        adapter.submitList(data)
                        updateEmptyState(data.isEmpty())
                    }
                }
                "genre" -> {
                    repository.getAllGenres().collectLatest { items ->
                        val data = items.filter { it.isNotBlank() }.map { CategoryItem(it) }
                        adapter.submitList(data)
                        updateEmptyState(data.isEmpty())
                    }
                }
                else -> {
                    repository.getAllAlbums().collectLatest { items ->
                        val data = items.map { CategoryItem(it) }
                        adapter.submitList(data)
                        updateEmptyState(data.isEmpty())
                    }
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.rvCategories.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            binding.rvCategories.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
        }
    }

    private fun loadSongsForCategory(name: String) {
        binding.rvCategories.adapter = songsAdapter
        lifecycleScope.launch {
            val flow = when (categoryType) {
                "album" -> repository.getSongsByAlbum(name)
                "artist" -> repository.getSongsByArtist(name)
                "genre" -> repository.getSongsByGenre(name)
                "year" -> repository.getSongsByYear(name)
                else -> repository.getAllSongs()
            }
            flow.collectLatest { songs ->
                if (_binding == null) return@collectLatest
                songsAdapter.submitList(songs)
                if (songs.isEmpty()) {
                    binding.rvCategories.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.rvCategories.visibility = View.VISIBLE
                    binding.tvEmpty.visibility = View.GONE
                }
            }
        }
    }

    private fun showAlbumCoverDialog(albumName: String) {
        coverDialogJob?.cancel()
        coverDialogJob = lifecycleScope.launch {
            repository.getSongsByAlbum(albumName).collectLatest { songsList ->
                if (songsList.isEmpty()) return@collectLatest

                val dialogItems = songsList.map { song ->
                    if (song.thumbnailUrl.isNotBlank() && File(song.thumbnailUrl).exists()) {
                        "${song.title} ★"
                    } else if (song.filePath.isNotBlank() && File(song.filePath).exists()) {
                        "${song.title} ◆"
                    } else {
                        song.title
                    }
                }.toTypedArray()

                val songArray = songsList.toTypedArray()

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(albumName)
                    .setItems(dialogItems) { _, which ->
                        val selectedSong = songArray[which]
                        val coverPath = if (selectedSong.thumbnailUrl.isNotBlank() && File(selectedSong.thumbnailUrl).exists()) {
                            selectedSong.thumbnailUrl
                        } else if (selectedSong.filePath.isNotBlank() && File(selectedSong.filePath).exists()) {
                            selectedSong.filePath
                        } else {
                            ""
                        }
                        if (coverPath.isNotBlank()) {
                            libraryRepo.setAlbumCoverOverride(albumName, coverPath)
                            loadCategories()
                        }
                    }
                    .setNeutralButton("Quitar carátula") { _, _ ->
                        libraryRepo.setAlbumCoverOverride(albumName, "")
                        loadCategories()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

class CategoryAdapter(
    private val onItemClick: (String) -> Unit,
    private val onItemLongClick: ((CategoryItem) -> Unit)? = null,
    private val defaultIconRes: Int = R.drawable.ic_album
) : ListAdapter<CategoryItem, CategoryAdapter.ViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context
        holder.name.text = if (item.name.isBlank()) ctx.getString(R.string.unknown_artist) else item.name
        holder.count.visibility = View.GONE

        val cover = holder.cover
        if (item.coverPath.isNotBlank() && File(item.coverPath).exists()) {
            cover.visibility = View.VISIBLE
            cover.tag = null
            if (isImageExtension(item.coverPath)) {
                cover.load(File(item.coverPath)) {
                    crossfade(true)
                    placeholder(defaultIconRes)
                    error(defaultIconRes)
                }
            } else {
                ArtworkLoader.loadArtFromAudioFile(cover, item.coverPath)
            }
        } else if (item.coverPath.isNotBlank() && item.coverPath.contains("/")) {
            cover.visibility = View.VISIBLE
            cover.tag = item.coverPath
            ArtworkLoader.loadArtFromAudioFile(cover, item.coverPath)
        } else {
            cover.visibility = View.VISIBLE
            cover.tag = null
            cover.setImageResource(defaultIconRes)
        }

        holder.itemView.setOnClickListener { onItemClick(item.name) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(item)
            true
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_name)
        val count: TextView = view.findViewById(R.id.tv_count)
        val cover: ImageView = view.findViewById(R.id.iv_category_cover)
    }

    class CategoryDiffCallback : DiffUtil.ItemCallback<CategoryItem>() {
        override fun areItemsTheSame(old: CategoryItem, new: CategoryItem): Boolean = old.name == new.name
        override fun areContentsTheSame(old: CategoryItem, new: CategoryItem): Boolean = old == new
    }

    private fun isImageExtension(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".bmp") || lower.endsWith(".gif")
    }
}
