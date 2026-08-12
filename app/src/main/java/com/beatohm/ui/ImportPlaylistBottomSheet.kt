package com.beatohm.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.beatohm.R
import com.beatohm.databinding.BottomSheetImportPlaylistBinding
import com.beatohm.importer.DeezerImporter
import com.beatohm.importer.PlaylistImportManager
import com.beatohm.importer.SpotifyImporter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportPlaylistBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetImportPlaylistBinding? = null
    private val binding get() = _binding!!

    private lateinit var importManager: PlaylistImportManager
    private var importJob: Job? = null

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

        importManager = PlaylistImportManager(requireContext())

        setupListeners()
        checkForActiveSession()
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

        binding.llProgress.visibility = View.VISIBLE
        binding.btnImport.visibility = View.GONE
        binding.btnCancel.visibility = View.VISIBLE
        binding.etPlaylistUrl.isEnabled = false

        importManager.onProgress = { completed, total, currentTrack ->
            lifecycleScope.launch {
                binding.progressBar.max = total
                binding.progressBar.progress = completed
                binding.tvProgress.text = getString(R.string.import_playlist_progress, completed, total, currentTrack)
            }
        }

        importManager.onComplete = { imported, failed, skipped ->
            lifecycleScope.launch {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.import_playlist_complete, imported, failed, skipped),
                    Toast.LENGTH_LONG
                ).show()
                dismiss()
            }
        }

        importJob = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    importManager.startImport(url)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.import_playlist_error, e.message),
                    Toast.LENGTH_LONG
                ).show()
                dismiss()
            }
        }
    }

    private fun cancelImport() {
        importManager.cancel()
        importJob?.cancel()
        dismiss()
    }

    private fun checkForActiveSession() {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                importManager.getActiveSession()
            }
            if (session != null && session.status == "ACTIVE") {
                binding.tvPlatform.visibility = View.VISIBLE
                binding.tvPlatform.text = getString(R.string.import_playlist_resume_available)
                binding.btnImport.text = getString(R.string.import_playlist_resume)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ImportPlaylistBottomSheet"

        fun newInstance(): ImportPlaylistBottomSheet {
            return ImportPlaylistBottomSheet()
        }
    }
}
