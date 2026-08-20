package com.beatohm.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.beatohm.R
import com.beatohm.databinding.ItemMetadataCandidateOptionBinding
import com.beatohm.databinding.ItemMetadataCandidateSongBinding
import com.beatohm.metadata.MetadataCandidate
import com.beatohm.metadata.MetadataSource
import java.io.File
import kotlin.math.roundToInt

/**
 * Item de la lista plana de pendientes (T10).
 *
 * Cada canción con candidatos PENDING genera un [SongHeader] (título/artista
 * actual + botón Saltar) seguido de N [CandidateOption] (uno por candidato,
 * con su metadata y botón Aplicar). El índice [CandidateOption.index] es la
 * posición del candidato dentro del `candidatesJson` del registro.
 */
sealed class MetadataCandidatesItem {
    data class SongHeader(
        val entityId: Long,
        val songId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String,
        val filePath: String
    ) : MetadataCandidatesItem()

    data class CandidateOption(
        val entityId: Long,
        val candidate: MetadataCandidate,
        val index: Int
    ) : MetadataCandidatesItem()
}

/**
 * Adapter de canciones con candidatos de metadata pendientes de elección.
 *
 * Modelo de lista plana agrupada: por cada registro PENDING se emiten
 * [MetadataCandidatesItem.SongHeader] + N [MetadataCandidatesItem.CandidateOption].
 * Callbacks:
 * - [onApply]: `(entityId, selectedIndex)` → aplicar el candidato elegido.
 * - [onSkip]: `(entityId)` → descartar todo el lote de la canción.
 */
class MetadataCandidatesAdapter(
    private val onApply: (entityId: Long, selectedIndex: Int) -> Unit,
    private val onSkip: (entityId: Long) -> Unit
) : ListAdapter<MetadataCandidatesItem, RecyclerView.ViewHolder>(DiffCallback()) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is MetadataCandidatesItem.SongHeader -> TYPE_HEADER
        is MetadataCandidatesItem.CandidateOption -> TYPE_OPTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> SongHeaderViewHolder(
                ItemMetadataCandidateSongBinding.inflate(inflater, parent, false)
            )
            else -> CandidateOptionViewHolder(
                ItemMetadataCandidateOptionBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is MetadataCandidatesItem.SongHeader -> (holder as SongHeaderViewHolder).bind(item)
            is MetadataCandidatesItem.CandidateOption -> (holder as CandidateOptionViewHolder).bind(item)
        }
    }

    inner class SongHeaderViewHolder(
        private val binding: ItemMetadataCandidateSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MetadataCandidatesItem.SongHeader) {
            val ctx = binding.root.context
            binding.tvSongTitle.text = item.title
            binding.tvSongArtist.text = item.artist.ifBlank { ctx.getString(R.string.unknown_artist) }
            bindHeaderArtwork(binding.ivSongThumbnail, item.thumbnailUrl, item.filePath)
            binding.btnSkip.setOnClickListener { onSkip(item.entityId) }
        }
    }

    inner class CandidateOptionViewHolder(
        private val binding: ItemMetadataCandidateOptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MetadataCandidatesItem.CandidateOption) {
            val ctx = binding.root.context
            val candidate = item.candidate

            binding.tvCandidateTitle.text = candidate.title.ifBlank { ctx.getString(R.string.placeholder_title) }
            binding.tvCandidateArtist.text = candidate.artist.ifBlank { ctx.getString(R.string.unknown_artist) }

            binding.tvCandidateAlbum.text = candidate.album
            binding.tvCandidateAlbum.visibility = if (candidate.album.isBlank()) View.GONE else View.VISIBLE

            val yearGenre = listOf(candidate.year, candidate.genre)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            binding.tvCandidateYearGenre.text = yearGenre
            binding.tvCandidateYearGenre.visibility = if (yearGenre.isBlank()) View.GONE else View.VISIBLE

            binding.tvCandidateSource.text =
                ctx.getString(R.string.metadata_candidates_source, sourceLabel(candidate.source))
            binding.tvCandidateScore.text =
                ctx.getString(R.string.metadata_candidates_score, (candidate.score * 100).roundToInt())

            bindOptionArtwork(binding.ivCandidateArtwork, candidate.artworkUrl)
            binding.btnApply.setOnClickListener { onApply(item.entityId, item.index) }
        }
    }

    private fun bindHeaderArtwork(imageView: ImageView, thumbnailUrl: String, filePath: String) {
        if (thumbnailUrl.startsWith("http")) {
            imageView.tag = null
            imageView.load(thumbnailUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_player)
                error(R.drawable.ic_player)
            }
        } else if (thumbnailUrl.isNotBlank() && File(thumbnailUrl).exists()) {
            imageView.tag = null
            imageView.load(File(thumbnailUrl)) {
                crossfade(true)
                placeholder(R.drawable.ic_player)
                error(R.drawable.ic_player)
            }
        } else if (filePath.isNotBlank() && File(filePath).exists()) {
            imageView.tag = filePath
            ArtworkLoader.loadArtFromAudioFile(imageView, filePath)
        } else {
            imageView.tag = null
            imageView.setImageResource(R.drawable.ic_player)
        }
    }

    private fun bindOptionArtwork(imageView: ImageView, artworkUrl: String) {
        imageView.tag = null
        if (artworkUrl.startsWith("http")) {
            imageView.load(artworkUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_player)
                error(R.drawable.ic_player)
            }
        } else {
            imageView.setImageResource(R.drawable.ic_player)
        }
    }

    private fun sourceLabel(source: MetadataSource): String = when (source) {
        MetadataSource.LASTFM -> "Last.fm"
        MetadataSource.ITUNES -> "iTunes"
        MetadataSource.SPOTIFY -> "Spotify"
        MetadataSource.DEEZER -> "Deezer"
        MetadataSource.MUSICBRAINZ -> "MusicBrainz"
    }

    class DiffCallback : DiffUtil.ItemCallback<MetadataCandidatesItem>() {
        override fun areItemsTheSame(oldItem: MetadataCandidatesItem, newItem: MetadataCandidatesItem): Boolean {
            return when {
                oldItem is MetadataCandidatesItem.SongHeader && newItem is MetadataCandidatesItem.SongHeader ->
                    oldItem.songId == newItem.songId
                oldItem is MetadataCandidatesItem.CandidateOption && newItem is MetadataCandidatesItem.CandidateOption ->
                    oldItem.entityId == newItem.entityId && oldItem.index == newItem.index
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: MetadataCandidatesItem, newItem: MetadataCandidatesItem): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_OPTION = 1
    }
}
