package com.musicdownloader.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.R

class ColorPaletteAdapter(
    private val colors: IntArray,
    private val selectedIndex: Int,
    private val onColorSelected: (Int) -> Unit
) : RecyclerView.Adapter<ColorPaletteAdapter.ViewHolder>() {

    private var selectedPosition = selectedIndex

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorCircle: View = view.findViewById(R.id.color_circle)
        val checkIcon: ImageView = view.findViewById(R.id.check_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_color_palette, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val color = colors[position]
        val circle = holder.colorCircle.background as? GradientDrawable
            ?: GradientDrawable().also { holder.colorCircle.background = it }
        circle.shape = GradientDrawable.OVAL
        circle.setColor(color)
        circle.setStroke(
            (2 * holder.itemView.context.resources.displayMetrics.density).toInt(),
            if (position == selectedPosition) Color.WHITE else Color.TRANSPARENT
        )
        holder.checkIcon.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = position
            if (prev >= 0) notifyItemChanged(prev)
            notifyItemChanged(position)
            onColorSelected(color)
        }
    }

    override fun getItemCount() = colors.size
}
