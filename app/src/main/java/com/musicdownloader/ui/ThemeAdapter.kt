package com.musicdownloader.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.R
import com.musicdownloader.data.UserTheme

class ThemeAdapter(
    private val onThemeClick: (UserTheme) -> Unit
) : ListAdapter<ThemeAdapter.ThemeItem, ThemeAdapter.ThemeViewHolder>(ThemeDiffCallback()) {

    data class ThemeItem(
        val theme: UserTheme,
        val isPreset: Boolean,
        val isActive: Boolean
    )

    class ThemeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val previewPrimary: View = itemView.findViewById(R.id.preview_primary)
        val previewSecondary: View = itemView.findViewById(R.id.preview_secondary)
        val previewAccent: View = itemView.findViewById(R.id.preview_accent)
        val tvName: TextView = itemView.findViewById(R.id.tv_theme_name)
        val tvType: TextView = itemView.findViewById(R.id.tv_theme_type)
        val ivActiveCheck: ImageView = itemView.findViewById(R.id.iv_active_check)
        val card: com.google.android.material.card.MaterialCardView =
            itemView as com.google.android.material.card.MaterialCardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_card, parent, false)
        return ThemeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context

        holder.previewPrimary.setBackgroundColor(item.theme.primaryColor)
        holder.previewSecondary.setBackgroundColor(item.theme.secondaryColor)
        holder.previewAccent.setBackgroundColor(item.theme.accentColor)

        holder.tvName.text = item.theme.name
        holder.tvType.text = if (item.isPreset) ctx.getString(R.string.theme_type_preset) else ctx.getString(R.string.theme_type_custom)

        if (item.isActive) {
            holder.card.strokeColor = item.theme.primaryColor
            holder.card.strokeWidth = 3
            holder.ivActiveCheck.visibility = View.VISIBLE
        } else {
            holder.card.strokeColor = Color.parseColor("#33FFFFFF")
            holder.card.strokeWidth = 1
            holder.ivActiveCheck.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onThemeClick(item.theme) }
    }

    class ThemeDiffCallback : DiffUtil.ItemCallback<ThemeItem>() {
        override fun areItemsTheSame(oldItem: ThemeItem, newItem: ThemeItem): Boolean =
            oldItem.theme.id == newItem.theme.id

        override fun areContentsTheSame(oldItem: ThemeItem, newItem: ThemeItem): Boolean =
            oldItem == newItem
    }
}