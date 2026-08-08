package com.musicdownloader.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.databinding.ItemPatternTokenBinding
import com.musicdownloader.model.PatternToken

class TokenBankAdapter(
    private val onTokenClick: (PatternToken) -> Unit
) : RecyclerView.Adapter<TokenBankAdapter.ViewHolder>() {

    private val items = PatternToken.available

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
            binding.ivDrag.visibility = android.view.View.GONE
            binding.btnRemove.visibility = android.view.View.GONE

            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onTokenClick(items[pos])
                }
            }
        }
    }
}