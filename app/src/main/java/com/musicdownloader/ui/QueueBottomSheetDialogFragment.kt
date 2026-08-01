package com.musicdownloader.ui

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playerViewModel = PlayerViewModel.getInstance(requireActivity().application as Application)

        val adapter = QueueSongAdapter(
            onItemClick = { index, song ->
                val activity = requireActivity() as? MainActivity ?: return@QueueSongAdapter
                val service = activity.playbackService ?: return@QueueSongAdapter
                playerViewModel.playAt(index)
                service.playFile(song.filePath)
                dismiss()
            },
            onRemove = { index -> playerViewModel.removeFromQueue(index) }
        )
        binding.rvQueue.layoutManager = LinearLayoutManager(requireContext())
        binding.rvQueue.adapter = adapter

        playerViewModel.playlist.observe(viewLifecycleOwner) { songs ->
            adapter.submitList(songs ?: emptyList())
            binding.tvQueueHeader.text = getString(R.string.queue_header, songs?.size ?: 0)
        }

        playerViewModel.currentSong.observe(viewLifecycleOwner) { song ->
            adapter.setCurrentPath(song?.filePath.orEmpty())
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private class QueueSongAdapter(
        private val onItemClick: (Int, Song) -> Unit,
        private val onRemove: (Int) -> Unit
    ) : ListAdapter<Song, QueueSongAdapter.ViewHolder>(QueueSongDiffCallback()) {

        private var currentPath: String = ""

        fun setCurrentPath(path: String) {
            if (currentPath != path) {
                currentPath = path
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_queue_song, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val song = getItem(position)
            val isCurrent = song.filePath == currentPath
            holder.title.text = song.title
            holder.title.setTypeface(holder.title.typeface, if (isCurrent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
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
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val root: View = view.findViewById(R.id.root)
            val title: TextView = view.findViewById(R.id.tv_title)
            val artist: TextView = view.findViewById(R.id.tv_artist)
            val btnRemove: ImageButton = view.findViewById(R.id.btn_remove)
        }

        class QueueSongDiffCallback : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(old: Song, new: Song): Boolean =
                old.filePath == new.filePath
            override fun areContentsTheSame(old: Song, new: Song): Boolean =
                old == new
        }
    }
}
