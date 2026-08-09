package com.musicdownloader.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.musicdownloader.R
import com.musicdownloader.databinding.ItemSearchResultBinding
import com.musicdownloader.model.SearchResult

class SearchResultAdapter(
    private val onDownload: (SearchResult) -> Unit,
    private val onPlay: ((SearchResult) -> Unit)? = null
) : ListAdapter<SearchResult, SearchResultAdapter.ViewHolder>(DiffCallback()) {

    enum class ButtonState { IDLE, DOWNLOADING, COMPLETED }

    private val states = mutableMapOf<String, ButtonState>()
    private val playingIds = mutableSetOf<String>()
    private val loadingIds = mutableSetOf<String>()

    fun setPlaying(videoId: String) {
        playingIds.clear()
        playingIds.add(videoId)
        loadingIds.remove(videoId)
        notifyDataSetChanged()
    }

    fun setLoading(videoId: String) {
        loadingIds.add(videoId)
        notifyDataSetChanged()
    }

    fun clearLoading(videoId: String) {
        loadingIds.remove(videoId)
        notifyDataSetChanged()
    }

    fun clearPlaying() {
        playingIds.clear()
        notifyDataSetChanged()
    }

    fun setDownloading(videoId: String) {
        states[videoId] = ButtonState.DOWNLOADING
        notifyStateChanged(positionOf(videoId))
    }

    fun setCompleted(videoId: String) {
        states[videoId] = ButtonState.COMPLETED
        notifyStateChanged(positionOf(videoId))
    }

    fun setIdle(videoId: String) {
        states[videoId] = ButtonState.IDLE
        notifyStateChanged(positionOf(videoId))
    }

    fun resetStates() {
        states.clear()
    }

    private fun positionOf(videoId: String): Int {
        for (i in 0 until itemCount) {
            if (currentList[i].videoId == videoId) return i
        }
        return RecyclerView.NO_POSITION
    }

    private fun notifyStateChanged(position: Int) {
        if (position != RecyclerView.NO_POSITION) {
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onDownload, onPlay)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val isPlaying = playingIds.contains(item.videoId)
        val isLoading = loadingIds.contains(item.videoId)
        holder.bind(item, states[item.videoId] ?: ButtonState.IDLE, isPlaying, isLoading)
    }

    class ViewHolder(
        private val binding: ItemSearchResultBinding,
        private val onDownload: (SearchResult) -> Unit,
        private val onPlay: ((SearchResult) -> Unit)? = null
    ) : RecyclerView.ViewHolder(binding.root) {

        private var lastState: ButtonState? = null

        fun bind(result: SearchResult, state: ButtonState, isPlaying: Boolean = false, isLoading: Boolean = false) {
            binding.tvTitle.text = result.title
            binding.tvChannel.text = result.channelName
            binding.tvDuration.text = result.durationText
            binding.ivThumbnail.load(result.thumbnailUrl) {
                placeholder(R.drawable.ic_music_note)
                error(R.drawable.ic_music_note)
            }
            if (isLoading) {
                binding.btnPlay.isEnabled = false
                binding.btnPlay.setIconResource(R.drawable.ic_pause)
                binding.btnPlay.alpha = 0.5f
            } else {
                binding.btnPlay.isEnabled = true
                binding.btnPlay.alpha = 1f
                binding.btnPlay.setIconResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
            }
            binding.btnPlay.setOnClickListener { if (!isLoading) onPlay?.invoke(result) }
            binding.btnDownload.setOnClickListener { onDownload(result) }
            applyState(state)
        }

        private fun applyState(state: ButtonState) {
            val animating = state != lastState
            when (state) {
                ButtonState.IDLE -> showIdle()
                ButtonState.DOWNLOADING -> showDownloading(animating)
                ButtonState.COMPLETED -> showCompleted(animating)
            }
            lastState = state
        }

        private fun showIdle() {
            binding.btnDownload.animate().cancel()
            binding.pbDownload.animate().cancel()
            binding.tvDownloadDone.animate().cancel()
            binding.pbDownload.visibility = View.GONE
            binding.tvDownloadDone.visibility = View.GONE
            binding.btnDownload.visibility = View.VISIBLE
            binding.btnDownload.alpha = 1f
        }

        private fun showDownloading(animating: Boolean) {
            binding.btnDownload.animate().cancel()
            binding.tvDownloadDone.animate().cancel()
            binding.tvDownloadDone.visibility = View.GONE
            binding.pbDownload.visibility = View.VISIBLE
            binding.pbDownload.alpha = 0f
            if (animating) {
                binding.btnDownload.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        binding.btnDownload.visibility = View.GONE
                    }
                    .start()
                binding.pbDownload.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                binding.btnDownload.visibility = View.GONE
                binding.pbDownload.alpha = 1f
            }
        }

        private fun showCompleted(animating: Boolean) {
            binding.btnDownload.animate().cancel()
            binding.pbDownload.animate().cancel()
            binding.tvDownloadDone.animate().cancel()
            binding.btnDownload.visibility = View.GONE
            binding.tvDownloadDone.visibility = View.VISIBLE
            if (animating) {
                binding.pbDownload.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        binding.pbDownload.visibility = View.GONE
                    }
                    .start()
                binding.tvDownloadDone.scaleX = 0.3f
                binding.tvDownloadDone.scaleY = 0.3f
                binding.tvDownloadDone.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            } else {
                binding.pbDownload.visibility = View.GONE
                binding.tvDownloadDone.scaleX = 1f
                binding.tvDownloadDone.scaleY = 1f
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean =
            oldItem.videoId == newItem.videoId

        override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean =
            oldItem == newItem
    }
}
