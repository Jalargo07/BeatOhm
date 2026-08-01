package com.musicdownloader.ui

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.databinding.ItemPatternTokenBinding
import com.musicdownloader.model.PatternToken

class TokenBankAdapter(
    private val onStartDrag: (PatternToken, View) -> Unit
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

        private val gestureDetector = GestureDetector(
            binding.root.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onStartDrag(items[pos], binding.root)
                    }
                }
            }
        )

        fun bind(token: PatternToken) {
            binding.tvToken.text = token.displayName
            binding.btnRemove.visibility = View.GONE
            binding.root.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }
        }
    }
}
