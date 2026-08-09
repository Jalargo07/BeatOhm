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
import com.musicdownloader.databinding.ItemDownloadBinding
import com.musicdownloader.model.DownloadState
import com.musicdownloader.model.DownloadStatus
import java.io.File

class DownloadAdapter : ListAdapter<DownloadState, DownloadAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemDownloadBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var lastStatus: DownloadStatus? = null

        fun bind(state: DownloadState) {
            val song = state.song
            binding.tvTitle.text = song.title.ifBlank { "Procesando..." }
            binding.tvSubtitle.text = song.artist.ifBlank { state.url }
            binding.tvMeta.text = metaText(state)

            val musicNoteRes = IconPackManager.getDownloadIconResId(ThemeManager.currentIconPack)
            if (song.thumbnailUrl.isNotBlank()) {
                binding.ivCover.load(song.thumbnailUrl) {
                    placeholder(musicNoteRes)
                    error(musicNoteRes)
                }
            } else if (state.filePath.isNotBlank() && File(state.filePath).exists()) {
                binding.ivCover.tag = state.filePath
                ArtworkLoader.loadArtFromAudioFile(binding.ivCover, state.filePath)
            } else {
                binding.ivCover.setImageResource(musicNoteRes)
            }

            applyStatus(state)
            lastStatus = state.status
        }

        private fun metaText(state: DownloadState): String {
            val format = "MP3"
            val sizeKb = fileSizeKb(state) ?: parseSizeKb(state.errorMessage)
            return if (sizeKb != null) {
                val size = if (sizeKb >= 1024) "%.1f MB".format(sizeKb / 1024f) else "$sizeKb KB"
                "$format • $size"
            } else {
                format
            }
        }

        private fun fileSizeKb(state: DownloadState): Int? {
            if (state.filePath.isBlank()) return null
            val file = File(state.filePath)
            if (!file.exists()) return null
            return (file.length() / 1024).toInt()
        }

        private fun parseSizeKb(message: String): Int? {
            val match = Regex("""\((\d+)\s*KB\)""").find(message) ?: return null
            return match.groupValues[1].toIntOrNull()
        }

        private fun applyStatus(state: DownloadState) {
            val status = state.status
            when (status) {
                DownloadStatus.QUEUED -> {
                    binding.progressBar.isIndeterminate = true
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvStatus.text = binding.root.context.getString(R.string.status_en_cola)
                    binding.tvStatus.setTextColor(binding.root.context.getColor(R.color.text_secondary))
                }
                DownloadStatus.EXTRACTING -> {
                    binding.progressBar.isIndeterminate = true
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvStatus.text = binding.root.context.getString(R.string.status_extrayendo)
                    binding.tvStatus.setTextColor(binding.root.context.getColor(R.color.text_secondary))
                }
                DownloadStatus.FETCHING_METADATA -> {
                    binding.progressBar.isIndeterminate = true
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvStatus.text = binding.root.context.getString(R.string.status_metadata)
                    binding.tvStatus.setTextColor(binding.root.context.getColor(R.color.text_secondary))
                }
                DownloadStatus.DOWNLOADING -> {
                    binding.progressBar.isIndeterminate = false
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = state.progress
                    binding.tvStatus.text = "${state.progress}%"
                    binding.tvStatus.setTextColor(binding.root.context.getColor(R.color.text_secondary))
                }
                DownloadStatus.TAGGING -> {
                    binding.progressBar.isIndeterminate = true
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvStatus.text = binding.root.context.getString(R.string.status_escribiendo_tags)
                    binding.tvStatus.setTextColor(binding.root.context.getColor(R.color.text_secondary))
                }
                DownloadStatus.COMPLETED -> showCompleted(lastStatus != DownloadStatus.COMPLETED)
                DownloadStatus.ERROR -> showError()
            }
        }

        private fun showCompleted(animating: Boolean) {
            binding.progressBar.animate().cancel()
            binding.tvStatus.animate().cancel()
            if (animating) {
                binding.progressBar.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        binding.progressBar.visibility = View.GONE
                        binding.progressBar.alpha = 1f
                    }
                    .start()
                binding.tvStatus.text = binding.root.context.getString(R.string.status_completado)
                binding.tvStatus.setTextColor(binding.root.context.getColor(R.color.success))
                binding.tvStatus.scaleX = 0.3f
                binding.tvStatus.scaleY = 0.3f
                binding.tvStatus.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            } else {
                binding.progressBar.visibility = View.GONE
                binding.progressBar.alpha = 1f
                binding.tvStatus.text = binding.root.context.getString(R.string.status_completado)
                binding.tvStatus.setTextColor(binding.root.context.getColor(R.color.success))
                binding.tvStatus.scaleX = 1f
                binding.tvStatus.scaleY = 1f
            }
        }

        private fun showError() {
            binding.progressBar.animate().cancel()
            binding.tvStatus.animate().cancel()
            binding.progressBar.visibility = View.GONE
            binding.progressBar.alpha = 1f
            binding.tvStatus.text = binding.root.context.getString(R.string.status_error)
            binding.tvStatus.setTextColor(binding.root.context.getColor(android.R.color.holo_red_light))
            binding.tvStatus.scaleX = 1f
            binding.tvStatus.scaleY = 1f
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DownloadState>() {
        override fun areItemsTheSame(old: DownloadState, new: DownloadState): Boolean =
            old.id == new.id

        override fun areContentsTheSame(old: DownloadState, new: DownloadState): Boolean =
            old == new
    }
}
