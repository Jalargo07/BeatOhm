package com.musicdownloader.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.databinding.ItemLibraryFolderBinding
import com.musicdownloader.databinding.ItemLibraryGridBinding
import com.musicdownloader.databinding.ItemLibrarySectionBinding
import java.io.File

data class LibraryCategory(val id: String, val labelRes: Int, val iconRes: Int, val count: Int = 0)

sealed interface LibraryMenuItem {
    val id: String

    data class Category(val category: LibraryCategory) : LibraryMenuItem {
        override val id: String get() = "cat:${category.id}"
    }

    data class Folder(val path: String) : LibraryMenuItem {
        override val id: String get() = "folder:$path"
    }

    data class Section(val labelRes: Int) : LibraryMenuItem {
        override val id: String get() = "section:$labelRes"
    }
}

class LibraryMenuAdapter(
    private val onCategoryClick: (LibraryCategory) -> Unit,
    private val onFolderClick: (String) -> Unit
) : ListAdapter<LibraryMenuItem, RecyclerView.ViewHolder>(LibraryMenuDiffCallback()) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is LibraryMenuItem.Category -> TYPE_CATEGORY
        is LibraryMenuItem.Folder -> TYPE_FOLDER
        is LibraryMenuItem.Section -> TYPE_SECTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CATEGORY -> CategoryViewHolder(ItemLibraryGridBinding.inflate(inflater, parent, false))
            TYPE_FOLDER -> FolderViewHolder(ItemLibraryFolderBinding.inflate(inflater, parent, false))
            else -> SectionViewHolder(ItemLibrarySectionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is LibraryMenuItem.Category -> {
                val vh = holder as CategoryViewHolder
                val category = item.category
                vh.binding.tvGridName.text = vh.binding.root.context.getString(category.labelRes)
                vh.binding.ivGridIcon.setImageResource(category.iconRes)
                vh.binding.root.tag = category.id
                if (category.count > 0) {
                    vh.binding.tvGridCount.visibility = android.view.View.VISIBLE
                    vh.binding.tvGridCount.text = formatCount(category.count)
                } else {
                    vh.binding.tvGridCount.visibility = android.view.View.INVISIBLE
                }
                vh.binding.root.setOnClickListener { onCategoryClick(category) }
            }
            is LibraryMenuItem.Folder -> {
                val vh = holder as FolderViewHolder
                vh.binding.tvFolderName.text = item.path.substringAfterLast(File.separator).ifBlank { item.path }
                vh.binding.tvFolderPath.text = item.path
                vh.binding.root.setOnClickListener { onFolderClick(item.path) }
            }
            is LibraryMenuItem.Section -> {
                val vh = holder as SectionViewHolder
                vh.binding.tvSectionTitle.setText(item.labelRes)
            }
        }
    }

    private fun formatCount(count: Int): String {
        return if (count >= 1000) {
            "%,d".format(count)
        } else {
            count.toString()
        }
    }

    class CategoryViewHolder(val binding: ItemLibraryGridBinding) : RecyclerView.ViewHolder(binding.root)
    class FolderViewHolder(val binding: ItemLibraryFolderBinding) : RecyclerView.ViewHolder(binding.root)
    class SectionViewHolder(val binding: ItemLibrarySectionBinding) : RecyclerView.ViewHolder(binding.root)

    class LibraryMenuDiffCallback : DiffUtil.ItemCallback<LibraryMenuItem>() {
        override fun areItemsTheSame(old: LibraryMenuItem, new: LibraryMenuItem): Boolean =
            old.id == new.id
        override fun areContentsTheSame(old: LibraryMenuItem, new: LibraryMenuItem): Boolean =
            old == new
    }

    companion object {
        private const val TYPE_CATEGORY = 0
        private const val TYPE_FOLDER = 1
        private const val TYPE_SECTION = 2
    }
}
