package com.musicdownloader.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.musicdownloader.R
import com.musicdownloader.databinding.ItemSearchResultBinding
import com.musicdownloader.model.SearchResult

class SearchResultAdapter(
    private val onDownload: (SearchResult) -> Unit
) : ListAdapter<SearchResult, SearchResultAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onDownload)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemSearchResultBinding,
        private val onDownload: (SearchResult) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: SearchResult) {
            binding.tvTitle.text = result.title
            binding.tvChannel.text = result.channelName
            binding.tvDuration.text = result.durationText
            binding.ivThumbnail.load(result.thumbnailUrl) {
                placeholder(R.drawable.ic_music_note)
                error(R.drawable.ic_music_note)
            }
            binding.btnDownload.setOnClickListener { onDownload(result) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean =
            oldItem.videoId == newItem.videoId

        override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean =
            oldItem == newItem
    }
}
