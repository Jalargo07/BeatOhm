package com.beatohm.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.beatohm.ImportPlaylistService
import com.beatohm.R
import com.beatohm.databinding.BottomSheetImportPlaylistBinding
import com.beatohm.importer.DeezerImporter
import com.beatohm.importer.SpotifyImporter
import com.beatohm.importer.YouTubeImporter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ImportPlaylistBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetImportPlaylistBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetImportPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        checkForActiveSession()

        arguments?.getString(ARG_URL)?.let { url ->
            binding.etPlaylistUrl.setText(url)
            detectPlatform(url)
        }
    }

    private fun setupListeners() {
        binding.etPlaylistUrl.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                detectPlatform(s?.toString() ?: "")
            }
        })

        binding.etPlaylistUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                startImport()
                true
            } else false
        }

        binding.btnImport.setOnClickListener {
            startImport()
        }

        binding.btnCancel.setOnClickListener {
            cancelImport()
        }
    }

    private fun detectPlatform(url: String) {
        when {
            DeezerImporter.canHandle(url) -> {
                binding.tvPlatform.visibility = View.VISIBLE
                binding.tvPlatform.text = getString(R.string.import_playlist_platform_deezer)
                binding.btnImport.isEnabled = true
            }
            SpotifyImporter.canHandle(url) -> {
                binding.tvPlatform.visibility = View.VISIBLE
                binding.tvPlatform.text = getString(R.string.import_playlist_platform_spotify)
                binding.btnImport.isEnabled = true
            }
            YouTubeImporter.canHandle(url) -> {
                binding.tvPlatform.visibility = View.VISIBLE
                binding.tvPlatform.text = getString(R.string.import_playlist_platform_youtube)
                binding.btnImport.isEnabled = true
            }
            url.isBlank() -> {
                binding.tvPlatform.visibility = View.GONE
                binding.btnImport.isEnabled = false
            }
            else -> {
                binding.tvPlatform.visibility = View.VISIBLE
                binding.tvPlatform.text = getString(R.string.import_playlist_invalid_url)
                binding.btnImport.isEnabled = false
            }
        }
    }

    private fun startImport() {
        val url = binding.etPlaylistUrl.text?.toString()?.trim() ?: return
        if (url.isBlank()) return

        ImportPlaylistService.start(requireContext(), url)
        Toast.makeText(requireContext(), getString(R.string.import_playlist_start), Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun cancelImport() {
        val intent = android.content.Intent(requireContext(), ImportPlaylistService::class.java).apply {
            action = ImportPlaylistService.ACTION_CANCEL
        }
        requireContext().startService(intent)
        dismiss()
    }

    private fun checkForActiveSession() {
        // TODO: Check for active import session via service binding
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ImportPlaylistBottomSheet"
        private const val ARG_URL = "playlist_url"

        fun newInstance(url: String? = null): ImportPlaylistBottomSheet {
            return ImportPlaylistBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                }
            }
        }
    }
}
