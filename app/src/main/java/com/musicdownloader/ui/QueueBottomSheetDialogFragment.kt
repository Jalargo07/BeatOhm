package com.musicdownloader.ui

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.musicdownloader.MainActivity
import com.musicdownloader.R
import com.musicdownloader.databinding.BottomSheetQueueBinding
import com.musicdownloader.model.Song

class QueueBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetQueueBinding? = null
    private val binding get() = _binding!!
    private lateinit var playerViewModel: PlayerViewModel
    private var dragStartPosition = RecyclerView.NO_POSITION

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playerViewModel = PlayerViewModel.getInstance(requireActivity().application as Application)

        lateinit var itemTouchHelper: ItemTouchHelper
        val adapter = QueueSongAdapter(
            onItemClick = { index, song ->
                val activity = requireActivity() as? MainActivity ?: return@QueueSongAdapter
                val service = activity.playbackService ?: return@QueueSongAdapter
                playerViewModel.playAtDisplay(index)
                service.playFile(song.filePath)
                dismiss()
            },
            onRemove = { index -> playerViewModel.removeFromQueue(index) },
            onStartDrag = { holder -> itemTouchHelper.startDrag(holder) }
        )
        binding.rvQueue.layoutManager = LinearLayoutManager(requireContext())
        binding.rvQueue.adapter = adapter

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = false

            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    dragStartPosition = viewHolder?.bindingAdapterPosition ?: RecyclerView.NO_POSITION
                }
                super.onSelectedChanged(viewHolder, actionState)
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val start = dragStartPosition
                dragStartPosition = RecyclerView.NO_POSITION
                if (start != RecyclerView.NO_POSITION) {
                    val end = viewHolder.bindingAdapterPosition
                    if (end != start) {
                        playerViewModel.moveQueueItem(start, end)
                    }
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.rvQueue)

        playerViewModel.displayPlaylist.observe(viewLifecycleOwner) { songs ->
            adapter.submitSongs(songs ?: emptyList())
            binding.tvQueueHeader.text = getString(R.string.queue_header, songs?.size ?: 0)
            scrollToCurrentSong()
        }

        playerViewModel.currentSong.observe(viewLifecycleOwner) { song ->
            adapter.setCurrentPath(song?.filePath.orEmpty())
            scrollToCurrentSong()
        }
    }

    private fun scrollToCurrentSong() {
        val songs = playerViewModel.displayPlaylist.value ?: return
        if (songs.isEmpty()) return
        val currentPath = playerViewModel.currentSong.value?.filePath.orEmpty()
        if (currentPath.isBlank()) return
        val index = songs.indexOfFirst { it.filePath == currentPath }
        if (index >= 0) {
            binding.rvQueue.scrollToPosition(index)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private class QueueSongAdapter(
        private val onItemClick: (Int, Song) -> Unit,
        private val onRemove: (Int) -> Unit,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
    ) : RecyclerView.Adapter<QueueSongAdapter.ViewHolder>() {

        private val items = mutableListOf<Song>()
        private var currentPath: String = ""

        fun submitSongs(songs: List<Song>) {
            if (items == songs) return
            items.clear()
            items.addAll(songs)
            notifyDataSetChanged()
        }

        fun setCurrentPath(path: String) {
            if (currentPath != path) {
                currentPath = path
                notifyDataSetChanged()
            }
        }

        fun moveItem(from: Int, to: Int) {
            if (from == to) return
            if (from < 0 || from >= items.size || to < 0 || to >= items.size) return
            val item = items.removeAt(from)
            items.add(to, item)
            notifyItemMoved(from, to)
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_queue_song, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val song = items[position]
            val isCurrent = song.filePath == currentPath
            holder.title.text = song.title
            holder.title.setTypeface(
                holder.title.typeface,
                if (isCurrent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )
            holder.artist.text = song.artist.ifBlank { "Desconocido" }
            holder.root.setBackgroundColor(
                if (isCurrent) {
                    ContextCompat.getColor(holder.root.context, R.color.queue_current_bg)
                } else {
                    android.graphics.Color.TRANSPARENT
                }
            )
            holder.root.setOnClickListener { onItemClick(position, song) }
            holder.btnRemove.setOnClickListener { onRemove(position) }
            holder.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                true
            }
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val root: View = view.findViewById(R.id.root)
            val title: TextView = view.findViewById(R.id.tv_title)
            val artist: TextView = view.findViewById(R.id.tv_artist)
            val btnRemove: ImageButton = view.findViewById(R.id.btn_remove)
            val ivDragHandle: ImageView = view.findViewById(R.id.iv_drag_handle)
        }
    }
}
