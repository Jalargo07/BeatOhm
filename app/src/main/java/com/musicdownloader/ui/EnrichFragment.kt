package com.musicdownloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.R
import com.musicdownloader.databinding.FragmentEnrichBinding

class EnrichFragment : Fragment() {

    private var _binding: FragmentEnrichBinding? = null
    private val binding get() = _binding!!
    private lateinit var libraryViewModel: LibraryViewModel
    private lateinit var adapter: SongSelectorAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEnrichBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        libraryViewModel = ViewModelProvider(requireActivity())[LibraryViewModel::class.java]

        val allSongs = libraryViewModel.allSongs.value.orEmpty()
            .sortedBy { it.title.lowercase() }
        val incompleteIds = allSongs
            .filter { libraryViewModel.isIncomplete(it) }
            .map { it.id }
            .toSet()

        adapter = SongSelectorAdapter(allSongs, incompleteIds)
        binding.rvSongSelector.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSongSelector.adapter = adapter

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.btnEnrichConfirm.setOnClickListener {
            val selected = adapter.getSelectedSongs()
            if (selected.isNotEmpty()) {
                val options = com.musicdownloader.data.MusicRepository.EnrichOptions(
                    fetchMetadata = binding.cbEnrichName.isChecked,
                    writeTags = binding.cbEnrichTags.isChecked,
                    fetchLyrics = binding.cbEnrichLyrics.isChecked
                )
                libraryViewModel.startEnrichment(selected, options)
                findNavController().popBackStack()
            }
        }

        updateCount()
        adapter.onSelectionChanged = { updateCount() }

        binding.etEnrichSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                adapter.setFilter(s?.toString().orEmpty())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnSelectAll.setOnClickListener {
            if (adapter.getSelectedCount() == allSongs.size) {
                adapter.deselectAll()
            } else {
                adapter.selectAll()
            }
            updateCount()
        }
    }

    private fun updateCount() {
        val count = adapter.getSelectedCount()
        val total = adapter.itemCount
        binding.tvSelectionCount.text =
            getString(R.string.select_songs_selected_count, count, total)
        binding.btnSelectAll.text =
            getString(if (count == total) R.string.deselect_all else R.string.select_all)
        binding.btnEnrichConfirm.isEnabled = count > 0
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
