package com.musicdownloader.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.R
import com.musicdownloader.databinding.ItemDownloadBinding
import com.musicdownloader.model.DownloadState
import com.musicdownloader.model.DownloadStatus

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

        fun bind(state: DownloadState) {
            val song = state.song
            binding.tvTitle.text = song.title.ifBlank { "Procesando..." }
            binding.tvSubtitle.text = song.artist.ifBlank { state.url }

            when (state.status) {
                DownloadStatus.QUEUED -> {
                    binding.progressBar.isIndeterminate = true
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.tvStatus.text = "En cola..."
                }
                DownloadStatus.EXTRACTING -> {
                    binding.progressBar.isIndeterminate = true
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.tvStatus.text = "Extrayendo información..."
                }
                DownloadStatus.FETCHING_METADATA -> {
                    binding.progressBar.isIndeterminate = true
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.tvStatus.text = "Buscando metadata..."
                }
                DownloadStatus.DOWNLOADING -> {
                    binding.progressBar.isIndeterminate = false
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.progressBar.progress = state.progress
                    binding.tvStatus.text = "Descargando ${state.progress}%..."
                }
                DownloadStatus.TAGGING -> {
                    binding.progressBar.isIndeterminate = true
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.tvStatus.text = "Escribiendo metadatos..."
                }
                DownloadStatus.COMPLETED -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.tvStatus.text = "✓ Completado"
                    binding.tvStatus.setTextColor(
                        binding.root.context.getColor(R.color.primary)
                    )
                }
                DownloadStatus.ERROR -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.tvStatus.text = "✗ ${state.errorMessage}"
                    binding.tvStatus.setTextColor(
                        binding.root.context.getColor(android.R.color.holo_red_light)
                    )
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DownloadState>() {
        override fun areItemsTheSame(old: DownloadState, new: DownloadState): Boolean =
            old.id == new.id

        override fun areContentsTheSame(old: DownloadState, new: DownloadState): Boolean =
            old == new
    }
}
