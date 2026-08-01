package com.musicdownloader.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.databinding.ItemPatternTokenBinding
import com.musicdownloader.model.PatternToken

class PatternBuilderAdapter(
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PatternBuilderAdapter.ViewHolder>() {

    private val items = mutableListOf<PatternToken>()

    fun submit(tokens: List<PatternToken>) {
        items.clear()
        items.addAll(tokens)
        notifyDataSetChanged()
    }

    fun currentTokens(): List<PatternToken> = items.toList()

    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        if (from < 0 || from >= items.size || to < 0 || to >= items.size) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPatternTokenBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val binding: ItemPatternTokenBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(token: PatternToken) {
            binding.tvToken.text = token.displayName
            binding.ivDrag.visibility = View.GONE
            binding.btnRemove.visibility = View.VISIBLE
            binding.btnRemove.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onRemove(pos)
            }
        }
    }
}
