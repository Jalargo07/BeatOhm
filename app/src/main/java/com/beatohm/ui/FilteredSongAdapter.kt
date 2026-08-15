package com.beatohm.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.beatohm.R
import com.beatohm.data.LocalSong
import java.io.File

class FilteredSongAdapter(
    private val onItemClick: (LocalSong) -> Unit,
    private val onItemLongClick: ((LocalSong) -> Unit)? = null
) : ListAdapter<LocalSong, FilteredSongAdapter.ViewHolder>(FilteredSongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = getItem(position)
        holder.title.text = song.title
        holder.artist.text = song.artist.ifBlank { holder.itemView.context.getString(R.string.unknown_artist) }

        val packId = ThemeManager.currentIconPack
        val colorAware = IconPackManager.isColorAwarePack(packId)
        val playerRes = IconPackManager.getAppIconResIds(packId)[IconPackManager.ICON_PLAYER] ?: R.drawable.ic_player
        val playerDrawable = if (colorAware) IconPackManager.getIcon(IconPackManager.ICON_PLAYER, packId, holder.itemView.context) else null
        if (song.thumbnailUrl.isNotBlank() && File(song.thumbnailUrl).exists()) {
            holder.thumbnail.tag = null
            holder.thumbnail.load(File(song.thumbnailUrl)) {
                crossfade(true)
                if (playerDrawable != null) {
                    placeholder(playerDrawable)
                    error(playerDrawable)
                } else {
                    placeholder(playerRes)
                    error(playerRes)
                }
            }
        } else if (song.filePath.isNotBlank() && File(song.filePath).exists()) {
            holder.thumbnail.tag = song.filePath
            ArtworkLoader.loadArtFromAudioFile(holder.thumbnail, song.filePath)
        } else {
            holder.thumbnail.tag = null
            if (playerDrawable != null) {
                holder.thumbnail.setImageDrawable(playerDrawable)
                holder.thumbnail.imageTintList = null
            } else {
                holder.thumbnail.setImageResource(playerRes)
            }
        }

        holder.itemView.setOnClickListener { onItemClick(song) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(song)
            true
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.iv_thumbnail)
        val title: TextView = view.findViewById(R.id.tv_title)
        val artist: TextView = view.findViewById(R.id.tv_artist)
    }

    class FilteredSongDiffCallback : DiffUtil.ItemCallback<LocalSong>() {
        override fun areItemsTheSame(old: LocalSong, new: LocalSong): Boolean =
            old.id == new.id
        override fun areContentsTheSame(old: LocalSong, new: LocalSong): Boolean =
            old == new
    }
}
