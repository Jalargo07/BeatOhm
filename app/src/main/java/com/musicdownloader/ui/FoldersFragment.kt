package com.musicdownloader.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.musicdownloader.R
import com.musicdownloader.databinding.FragmentFoldersBinding
import com.musicdownloader.databinding.ItemFolderBinding
import java.io.File

class FoldersFragment : Fragment() {

    private var _binding: FragmentFoldersBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: LibraryViewModel
    private lateinit var adapter: FolderAdapter

    private val folderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireActivity().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        val path = treeUriToAbsolutePath(uri)
        if (path == null) {
            Toast.makeText(requireContext(), R.string.folder_invalid, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val current = viewModel.folders.value.orEmpty()
        if (current.any { it.trimEnd('/') == path }) {
            Toast.makeText(requireContext(), R.string.folder_already_added, Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addFolder(path)
            Toast.makeText(requireContext(), R.string.folder_added, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFoldersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[LibraryViewModel::class.java]
        adapter = FolderAdapter { path -> confirmRemoveFolder(path) }
        binding.rvFolders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFolders.adapter = adapter

        binding.btnAddFolder.setOnClickListener {
            folderLauncher.launch(null)
        }

        viewModel.folders.observe(viewLifecycleOwner) { folders ->
            adapter.submitList(folders)
            binding.tvFoldersEmpty.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmRemoveFolder(path: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.folder_remove_title)
            .setMessage(R.string.folder_remove_message)
            .setPositiveButton(R.string.folder_remove_and_songs) { _, _ ->
                viewModel.removeFolder(path, deleteSongs = true)
            }
            .setNeutralButton(R.string.folder_remove_only) { _, _ ->
                viewModel.removeFolder(path, deleteSongs = false)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun treeUriToAbsolutePath(uri: Uri): String? {
            if (uri.scheme != "content") return null
            return when (uri.authority) {
                "com.android.externalstorage.documents" -> externalStoragePath(uri)
                "com.android.providers.downloads.documents" -> downloadsPath(uri)
                "com.android.providers.media.documents" -> mediaPath(uri)
                else -> null
            }
        }

        private fun externalStoragePath(uri: Uri): String? {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val idx = docId.indexOf(':')
            if (idx <= 0) return null
            val volume = docId.substring(0, idx)
            val subPath = docId.substring(idx + 1)
            val root = when {
                volume == "primary" -> Environment.getExternalStorageDirectory().absolutePath
                volume.matches(Regex("[A-Za-z0-9]{4}-[A-Za-z0-9]{4}")) -> "/storage/$volume"
                else -> return null
            }
            return if (subPath.isBlank()) root else "$root/${subPath.trim('/')}"
        }

        private fun mediaPath(uri: Uri): String? {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val idx = docId.indexOf(':')
            if (idx <= 0) return null
            val volume = docId.substring(0, idx)
            if (volume != "primary") return null
            val subPath = docId.substring(idx + 1)
            val root = Environment.getExternalStorageDirectory().absolutePath
            return if (subPath.isBlank()) root else "$root/${subPath.trim('/')}"
        }

        private fun downloadsPath(uri: Uri): String? {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            if (docId.startsWith("raw:")) {
                return docId.substring(4).trimEnd('/').ifBlank { null }
            }
            if (docId.startsWith("msd:")) {
                val name = docId.substring(4)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                return if (name == downloadsDir.name) downloadsDir.absolutePath else null
            }
            val idx = docId.indexOf(':')
            if (idx <= 0) return null
            val volume = docId.substring(0, idx)
            if (volume != "primary") return null
            val subPath = docId.substring(idx + 1)
            val root = Environment.getExternalStorageDirectory().absolutePath
            return if (subPath.isBlank()) root else "$root/${subPath.trim('/')}"
        }
    }
}

class FolderAdapter(private val onRemove: (String) -> Unit) :
    ListAdapter<String, FolderAdapter.ViewHolder>(FolderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val path = getItem(position)
        holder.binding.tvFolderName.text = path.substringAfterLast(File.separator).ifBlank { path }
        holder.binding.tvFolderPath.text = path
        holder.binding.btnRemoveFolder.setOnClickListener { onRemove(path) }
    }

    class ViewHolder(val binding: ItemFolderBinding) : RecyclerView.ViewHolder(binding.root)

    class FolderDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(old: String, new: String): Boolean = old == new
        override fun areContentsTheSame(old: String, new: String): Boolean = old == new
    }
}
