package com.musicdownloader.ui

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicdownloader.data.toSong
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.R
import com.musicdownloader.data.LocalSong
import com.musicdownloader.data.MusicRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CategoryListFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var emptyView: TextView? = null
    private var headerBar: View? = null
    private var categoryTitle: TextView? = null
    private lateinit var adapter: CategoryAdapter
    private lateinit var songsAdapter: FilteredSongAdapter
    private lateinit var repository: MusicRepository
    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var categoryType: String

    private var showingSongs = false
    private var selectedCategoryValue = ""
    private var scrollPosition = 0
    private var scrollOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categoryType = arguments?.getString("category", "album") ?: "album"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val lm = recyclerView?.layoutManager as? LinearLayoutManager
        if (lm != null) {
            val pos = lm.findFirstVisibleItemPosition()
            val view = lm.findViewByPosition(pos)
            val offset = view?.top ?: 0
            outState.putInt("scroll_position", pos)
            outState.putInt("scroll_offset", offset)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_category_list, container, false)
        recyclerView = view.findViewById(R.id.rv_categories)
        emptyView = view.findViewById(R.id.tv_empty)
        headerBar = view.findViewById(R.id.header_bar)
        categoryTitle = view.findViewById(R.id.tv_category_title)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = MusicRepository(requireContext())
        playerViewModel = PlayerViewModel.getInstance(requireActivity().application as Application)

        songsAdapter = FilteredSongAdapter(onItemClick = { song ->
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@FilteredSongAdapter
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

        adapter = CategoryAdapter { name ->
            val lm = recyclerView?.layoutManager as? LinearLayoutManager
            if (lm != null) {
                scrollPosition = lm.findFirstVisibleItemPosition()
                val view = lm.findViewByPosition(scrollPosition)
                scrollOffset = view?.top ?: 0
            }
            selectedCategoryValue = name
            showingSongs = true
            headerBar?.visibility = View.VISIBLE
            categoryTitle?.text = name
            loadSongsForCategory(name)
        }

        recyclerView?.layoutManager = LinearLayoutManager(requireContext())

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showingSongs) {
                    showingSongs = false
                    headerBar?.visibility = View.GONE
                    recyclerView?.adapter = adapter
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
        recyclerView?.post {
            val lm = recyclerView?.layoutManager as? LinearLayoutManager ?: return@post
            if (scrollPosition > 0) {
                lm.scrollToPositionWithOffset(scrollPosition, scrollOffset)
            }
        }
    }

    private fun loadCategories() {
        recyclerView?.adapter = adapter
        lifecycleScope.launch {
            val flow = when (categoryType) {
                "album" -> repository.getAllAlbums()
                "artist" -> repository.getAllArtists()
                "genre" -> repository.getAllGenres()
                "year" -> repository.getAllYears()
                else -> repository.getAllAlbums()
            }
            flow.collectLatest { items ->
                val data = if (categoryType == "genre") {
                    items.filter { it.isNotBlank() }
                } else items
                adapter.submitList(data)
                if (data.isEmpty()) {
                    recyclerView?.visibility = View.GONE
                    emptyView?.visibility = View.VISIBLE
                } else {
                    recyclerView?.visibility = View.VISIBLE
                    emptyView?.visibility = View.GONE
                }
            }
        }
    }

    private fun loadSongsForCategory(name: String) {
        recyclerView?.adapter = songsAdapter
        lifecycleScope.launch {
            val flow = when (categoryType) {
                "album" -> repository.getSongsByAlbum(name)
                "artist" -> repository.getSongsByArtist(name)
                "genre" -> repository.getSongsByGenre(name)
                "year" -> repository.getSongsByYear(name)
                else -> repository.getAllSongs()
            }
            flow.collectLatest { songs ->
                songsAdapter.submitList(songs)
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
        headerBar = null
        categoryTitle = null
        super.onDestroyView()
    }
}

class CategoryAdapter(private val onItemClick: (String) -> Unit) :
    ListAdapter<String, CategoryAdapter.ViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.name.text = if (item.isBlank()) "Desconocido" else item
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_name)
        val count: TextView = view.findViewById(R.id.tv_count)
    }

    class CategoryDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(old: String, new: String): Boolean = old == new
        override fun areContentsTheSame(old: String, new: String): Boolean = old == new
    }
}
