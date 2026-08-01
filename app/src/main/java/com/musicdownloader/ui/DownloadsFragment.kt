package com.musicdownloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicdownloader.R
import com.musicdownloader.databinding.FragmentDownloadsBinding

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: DownloadAdapter
    private lateinit var searchAdapter: SearchResultAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        adapter = DownloadAdapter()
        searchAdapter = SearchResultAdapter { result ->
            viewModel.downloadFromSearch(result)
            viewModel.clearSearch()
        }

        binding.rvDownloads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDownloads.adapter = adapter

        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchResults.adapter = searchAdapter

        binding.etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { startDownload(); true } else false
        }

        binding.btnDownload.setOnClickListener { startDownload() }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { startSearch(); true } else false
        }

        binding.btnSearch.setOnClickListener { startSearch() }

        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_downloadsFragment_to_settingsFragment)
        }

        viewModel.downloads.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            searchAdapter.submitList(results)
            binding.tvEmptySearch.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isSearching.observe(viewLifecycleOwner) { searching ->
            binding.pbSearch.visibility = if (searching) View.VISIBLE else View.GONE
            binding.btnSearch.isEnabled = !searching
        }

        viewModel.searchError.observe(viewLifecycleOwner) { error ->
            binding.tvEmptySearch.text = error ?: getString(R.string.search_empty)
        }
    }

    private fun startSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isBlank()) {
            binding.etSearch.error = getString(R.string.search_query_required)
            return
        }
        viewModel.searchSongs(query)
        binding.etSearch.clearFocus()
    }

    private fun startDownload() {
        val url = binding.etUrl.text.toString().trim()
        if (url.isBlank()) { binding.etUrl.error = getString(R.string.download_url_required); return }
        if (!url.matches(Regex("(https?://)?(www\\.|m\\.)?(youtube\\.com|youtu\\.be|music\\.youtube\\.com)/.*"))) {
            binding.etUrl.error = getString(R.string.download_url_invalid); return
        }
        viewModel.startDownload(url)
        binding.etUrl.text?.clear()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
