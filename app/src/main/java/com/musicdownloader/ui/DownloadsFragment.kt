package com.musicdownloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicdownloader.databinding.FragmentDownloadsBinding

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: DownloadAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        adapter = DownloadAdapter()

        binding.rvDownloads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDownloads.adapter = adapter

        binding.etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { startDownload(); true } else false
        }

        binding.btnDownload.setOnClickListener { startDownload() }

        viewModel.downloads.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun startDownload() {
        val url = binding.etUrl.text.toString().trim()
        if (url.isBlank()) { binding.etUrl.error = "Ingresa una URL"; return }
        if (!url.matches(Regex("(https?://)?(www\\.|m\\.)?(youtube\\.com|youtu\\.be|music\\.youtube\\.com)/.*"))) {
            binding.etUrl.error = "URL de YouTube inválida"; return
        }
        viewModel.startDownload(url)
        binding.etUrl.text?.clear()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
