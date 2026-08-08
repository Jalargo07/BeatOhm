package com.musicdownloader.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.R
import com.musicdownloader.data.LocalSong

class SongSelectorAdapter(
    private val songs: List<LocalSong>,
    private val initiallySelected: Set<String>
) : RecyclerView.Adapter<SongSelectorAdapter.ViewHolder>() {

    var onSelectionChanged: (() -> Unit)? = null

    private val selectedIds = HashSet(initiallySelected)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song_selector, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = songs[position]
        holder.tvTitle.text = song.title
        holder.tvArtist.text = song.artist.ifBlank { holder.itemView.context.getString(R.string.unknown_artist) }
        holder.cbSelect.isChecked = selectedIds.contains(song.id)
        holder.tvIncomplete.visibility = if (isIncomplete(song)) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            toggleSelection(song.id)
            holder.cbSelect.isChecked = selectedIds.contains(song.id)
        }

        holder.cbSelect.setOnClickListener {
            toggleSelection(song.id)
        }
    }

    override fun getItemCount() = songs.size

    private fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        onSelectionChanged?.invoke()
    }

    fun selectAll() {
        selectedIds.addAll(songs.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun deselectAll() {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun getSelectedSongs(): List<LocalSong> = songs.filter { selectedIds.contains(it.id) }

    fun getSelectedCount(): Int = selectedIds.size

    private fun isIncomplete(song: LocalSong): Boolean {
        return song.artist.isBlank() || song.album.isBlank() || song.genre.isBlank()
            || song.thumbnailUrl.isBlank() || song.lyrics.isBlank() || song.year.isBlank()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbSelect: CheckBox = view.findViewById(R.id.cb_select)
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val tvArtist: TextView = view.findViewById(R.id.tv_artist)
        val tvIncomplete: TextView = view.findViewById(R.id.tv_incomplete)
    }
}
