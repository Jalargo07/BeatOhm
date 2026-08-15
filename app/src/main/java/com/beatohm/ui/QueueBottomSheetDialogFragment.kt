package com.beatohm.ui

import android.animation.ValueAnimator
import android.app.Application
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.beatohm.MainActivity
import com.beatohm.R
import com.beatohm.databinding.BottomSheetQueueBinding
import com.beatohm.model.Song
import java.io.File

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

        setupBottomSheetBehavior()

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

        playerViewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            adapter.setPlaying(playing)
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

    private fun setupBottomSheetBehavior() {
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        val behavior = BottomSheetBehavior.from(bottomSheet)

        behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        behavior.halfExpandedRatio = 0.5f
        behavior.peekHeight = 0
        behavior.isHideable = true
        behavior.skipCollapsed = true
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
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
        private var isPlaying = false

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = items[position].filePath.hashCode().toLong()

        fun submitSongs(songs: List<Song>) {
            if (items == songs) return
            val diffResult = DiffUtil.calculateDiff(QueueDiffCallback(items, songs))
            items.clear()
            items.addAll(songs)
            diffResult.dispatchUpdatesTo(this)
        }

        fun setCurrentPath(path: String) {
            if (currentPath != path) {
                val oldIndex = items.indexOfFirst { it.filePath == currentPath }
                val newIndex = items.indexOfFirst { it.filePath == path }
                currentPath = path
                if (oldIndex >= 0) notifyItemChanged(oldIndex)
                if (newIndex >= 0 && newIndex != oldIndex) notifyItemChanged(newIndex)
            }
        }

        fun setPlaying(playing: Boolean) {
            if (isPlaying != playing) {
                isPlaying = playing
                val currentIndex = items.indexOfFirst { it.filePath == currentPath }
                if (currentIndex >= 0) notifyItemChanged(currentIndex)
            }
        }

        fun moveItem(from: Int, to: Int) {
            if (from == to) return
            if (from < 0 || from >= items.size || to < 0 || to >= items.size) return
            val item = items.removeAt(from)
            items.add(to, item)
            notifyItemMoved(from, to)
        }

        private class QueueDiffCallback(
            private val oldItems: List<Song>,
            private val newItems: List<Song>
        ) : DiffUtil.Callback() {

            override fun getOldListSize(): Int = oldItems.size

            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition].filePath == newItems[newItemPosition].filePath

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition] == newItems[newItemPosition]
        }

        override fun getItemCount(): Int = items.size

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            holder.stopPlayingAnimation()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_queue_song_modern, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val song = items[position]
            val isCurrent = song.filePath == currentPath
            holder.title.text = song.title
            holder.title.setTypeface(
                holder.title.typeface,
                if (isCurrent) Typeface.BOLD else Typeface.NORMAL
            )
            holder.artist.text = song.artist.ifBlank { holder.itemView.context.getString(R.string.unknown_artist) }
            holder.card.setCardBackgroundColor(
                ContextCompat.getColorStateList(
                    holder.root.context,
                    if (isCurrent) R.color.queue_current_bg else R.color.surface
                )
            )
            holder.card.strokeColor = ContextCompat.getColor(
                holder.root.context,
                if (isCurrent) R.color.primary else R.color.outline
            )
            holder.card.strokeWidth = if (isCurrent) 2 else 0
            holder.playing.visibility = if (isCurrent) View.VISIBLE else View.GONE
            if (isCurrent && isPlaying) holder.startPlayingAnimation() else holder.stopPlayingAnimation()
            loadArtwork(holder.artwork, song)
            holder.root.setOnClickListener { onItemClick(position, song) }
            holder.btnRemove.setOnClickListener { onRemove(position) }
            holder.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                true
            }
        }

        private fun loadArtwork(iv: ImageView, song: Song) {
            val packId = ThemeManager.currentIconPack
            val colorAware = IconPackManager.isColorAwarePack(packId)
            val musicNoteRes = IconPackManager.getDownloadIconResId(packId)
            val musicNoteDrawable = if (colorAware) IconPackManager.getIcon(IconPackManager.ICON_MUSIC_NOTE, packId, iv.context) else null
            if (song.thumbnailUrl.isNotBlank() && File(song.thumbnailUrl).exists()) {
                iv.tag = null
                iv.load(File(song.thumbnailUrl)) {
                    crossfade(true)
                    if (musicNoteDrawable != null) {
                        placeholder(musicNoteDrawable)
                        error(musicNoteDrawable)
                    } else {
                        placeholder(musicNoteRes)
                        error(musicNoteRes)
                    }
                }
            } else if (song.filePath.isNotBlank() && File(song.filePath).exists()) {
                iv.tag = song.filePath
                ArtworkLoader.loadArtFromAudioFile(iv, song.filePath)
            } else {
                iv.tag = null
                if (musicNoteDrawable != null) {
                    iv.setImageDrawable(musicNoteDrawable)
                    iv.imageTintList = null
                } else {
                    iv.setImageResource(musicNoteRes)
                }
            }
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val root: MaterialCardView = view.findViewById(R.id.root)
            val card: MaterialCardView = view.findViewById(R.id.root)
            val title: TextView = view.findViewById(R.id.tv_title)
            val artist: TextView = view.findViewById(R.id.tv_artist)
            val btnRemove: ImageButton = view.findViewById(R.id.btn_remove)
            val ivDragHandle: ImageView = view.findViewById(R.id.iv_drag_handle)
            val artwork: ImageView = view.findViewById(R.id.iv_artwork)
            val playing: ImageView = view.findViewById(R.id.iv_playing)
            private var playingAnimator: ValueAnimator? = null

            fun startPlayingAnimation() {
                if (playingAnimator != null) return
                val animator = ValueAnimator.ofFloat(1f, 0.55f).apply {
                    duration = 500L
                    interpolator = AccelerateDecelerateInterpolator()
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { anim ->
                        val value = anim.animatedValue as Float
                        playing.scaleX = value
                        playing.scaleY = value
                    }
                    start()
                }
                playingAnimator = animator
            }

            fun stopPlayingAnimation() {
                playingAnimator?.cancel()
                playingAnimator = null
                playing.scaleX = 1f
                playing.scaleY = 1f
            }
        }
    }
}
