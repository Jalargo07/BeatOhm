package com.beatohm.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.beatohm.R
import com.beatohm.data.LocalSong
import com.beatohm.databinding.ItemFavoriteHorizontalBinding
import java.io.File

class FavoriteSongAdapter(
    private val onPlay: (LocalSong) -> Unit,
    private val onToggleFavorite: (LocalSong, Boolean) -> Unit
) : ListAdapter<LocalSong, FavoriteSongAdapter.ViewHolder>(FavoriteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteHorizontalBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = getItem(position)
        val b = holder.binding
        b.tvFavTitle.text = song.title
        b.tvFavArtist.text = song.artist.ifBlank { b.root.context.getString(R.string.unknown_artist) }

        val icons = IconPackManager.getAppIconResIds(ThemeManager.currentIconPack)
        if (song.thumbnailUrl.isNotBlank() && File(song.thumbnailUrl).exists()) {
            b.ivFavCover.tag = null
            b.ivFavCover.load(File(song.thumbnailUrl)) {
                crossfade(true)
                placeholder(R.drawable.ic_player)
                error(R.drawable.ic_player)
            }
        } else if (song.filePath.isNotBlank() && File(song.filePath).exists()) {
            b.ivFavCover.tag = song.filePath
            ArtworkLoader.loadArtFromAudioFile(b.ivFavCover, song.filePath)
        } else {
            b.ivFavCover.tag = null
            b.ivFavCover.setImageResource(R.drawable.ic_player)
        }

        b.btnFavToggle.setImageResource(
            if (song.isFavorite) icons[IconPackManager.ICON_HEART] ?: R.drawable.ic_favorite
            else icons[IconPackManager.ICON_HEART_BORDER] ?: R.drawable.ic_favorite_border
        )

        b.root.setOnClickListener { onPlay(song) }
        b.btnFavPlay.setOnClickListener { onPlay(song) }
        b.btnFavToggle.setOnClickListener { onToggleFavorite(song, !song.isFavorite) }
    }

    class ViewHolder(val binding: ItemFavoriteHorizontalBinding) : RecyclerView.ViewHolder(binding.root)

    class FavoriteDiffCallback : DiffUtil.ItemCallback<LocalSong>() {
        override fun areItemsTheSame(oldItem: LocalSong, newItem: LocalSong): Boolean =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: LocalSong, newItem: LocalSong): Boolean =
            oldItem == newItem
    }
}
