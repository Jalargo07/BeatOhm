package com.beatohm.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.beatohm.R
import com.beatohm.data.ArtistSimilarityDetector.DuplicatePair
import com.beatohm.databinding.ItemArtistDuplicateBinding

class ArtistDuplicatesAdapter(
    private val onMerge: (DuplicatePair, winner: String) -> Unit,
    private val onDismiss: (DuplicatePair) -> Unit
) : ListAdapter<DuplicatePair, ArtistDuplicatesAdapter.ViewHolder>(DiffCallback()) {

    private val selections = mutableMapOf<Int, String?>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArtistDuplicateBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun clearSelections() {
        selections.clear()
    }

    inner class ViewHolder(
        private val binding: ItemArtistDuplicateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(pair: DuplicatePair) {
            val ctx = binding.root.context
            val pos = bindingAdapterPosition

            binding.tvArtist1.text = pair.artist1
            binding.tvArtist1Count.text = ctx.resources.getQuantityString(
                R.plurals.artist_duplicates_song_count, pair.songCount1, pair.songCount1
            )
            binding.tvArtist2.text = pair.artist2
            binding.tvArtist2Count.text = ctx.resources.getQuantityString(
                R.plurals.artist_duplicates_song_count, pair.songCount2, pair.songCount2
            )
            binding.tvScore.text = ctx.getString(R.string.artist_duplicates_score, pair.score)

            val selectedWinner = selections[pos]

            updateArtist1Visual(selectedWinner == pair.artist1)
            updateArtist2Visual(selectedWinner == pair.artist2)
            binding.btnMerge.isEnabled = selectedWinner != null

            binding.llArtist1Container.setOnClickListener {
                val current = selections[pos]
                selections[pos] = if (current == pair.artist1) null else pair.artist1
                bind(pair)
            }

            binding.llArtist2Container.setOnClickListener {
                val current = selections[pos]
                selections[pos] = if (current == pair.artist2) null else pair.artist2
                bind(pair)
            }

            binding.btnMerge.setOnClickListener {
                val winner = selections[pos] ?: return@setOnClickListener
                onMerge(pair, winner)
            }
            binding.btnDismiss.setOnClickListener { onDismiss(pair) }
        }

        private fun updateArtist1Visual(selected: Boolean) {
            binding.ivArtist1Check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            binding.tvArtist1.setTextColor(
                binding.root.context.getColor(
                    if (selected) R.color.primary else R.color.on_surface
                )
            )
        }

        private fun updateArtist2Visual(selected: Boolean) {
            binding.ivArtist2Check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            binding.tvArtist2.setTextColor(
                binding.root.context.getColor(
                    if (selected) R.color.primary else R.color.on_surface
                )
            )
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DuplicatePair>() {
        override fun areItemsTheSame(oldItem: DuplicatePair, newItem: DuplicatePair): Boolean {
            return oldItem.artist1 == newItem.artist1 && oldItem.artist2 == newItem.artist2
        }

        override fun areContentsTheSame(oldItem: DuplicatePair, newItem: DuplicatePair): Boolean {
            return oldItem == newItem
        }
    }
}
