package com.musicdownloader.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.musicdownloader.R
import com.musicdownloader.databinding.FragmentDownloadsBinding
import com.musicdownloader.extractor.YouTubeExtractor
import com.musicdownloader.model.DownloadStatus
import com.musicdownloader.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: DownloadAdapter
    private lateinit var searchAdapter: SearchResultAdapter
    private val seenErrorIds = mutableSetOf<String>()
    private var previewPlayer: ExoPlayer? = null
    private var currentPreviewVideoId: String? = null
    private val previewHandler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        adapter = DownloadAdapter()

        previewPlayer = ExoPlayer.Builder(requireContext()).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        stopPreview()
                    }
                }
            })
        }

        searchAdapter = SearchResultAdapter(
            onDownload = { result ->
                searchAdapter.setDownloading(result.videoId)
                viewModel.downloadFromSearch(result)
            },
            onPlay = { result ->
                togglePreview(result)
            }
        )

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

        viewModel.downloads.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            updateEmptyVisibility()
            list.forEach { state ->
                val videoId = state.url.substringAfter("v=", "").take(11)
                if (videoId.isNotBlank()) {
                    when (state.status) {
                        DownloadStatus.COMPLETED -> searchAdapter.setCompleted(videoId)
                        DownloadStatus.ERROR -> searchAdapter.setIdle(videoId)
                        else -> Unit
                    }
                }
                if (state.status == DownloadStatus.ERROR && seenErrorIds.add(state.id)) {
                    Snackbar.make(binding.root, R.string.error_download_generic, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            searchAdapter.submitList(results)
            updateEmptyVisibility()
        }

        viewModel.isSearching.observe(viewLifecycleOwner) { searching ->
            binding.pbSearch.visibility = if (searching) View.VISIBLE else View.GONE
            binding.btnSearch.isEnabled = !searching
        }

        viewModel.searchError.observe(viewLifecycleOwner) { error ->
            binding.tvEmptySearch.text = friendlySearchError(error)
            updateEmptyVisibility()
        }
    }

    private fun friendlySearchError(raw: String?): String {
        val text = raw.orEmpty()
        if (text.isBlank()) return getString(R.string.search_empty)
        val lower = text.lowercase()
        val looksLikeNetwork = lower.contains("host") || lower.contains("connect") ||
            lower.contains("timeout") || lower.contains("internet") || lower.contains("network") ||
            lower.contains("unreachable") || lower.contains("resolve")
        return if (looksLikeNetwork) getString(R.string.error_no_internet) else text
    }

    private fun updateEmptyVisibility() {
        val downloadsEmpty = viewModel.downloads.value.orEmpty().isEmpty()
        val searchEmpty = viewModel.searchResults.value.orEmpty().isEmpty()
        val hasError = viewModel.searchError.value != null
        binding.tvEmpty.visibility = if (downloadsEmpty && searchEmpty && !hasError) View.VISIBLE else View.GONE
        binding.tvEmptySearch.visibility = if (searchEmpty && hasError) View.VISIBLE else View.GONE
    }

    private fun startSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isBlank()) {
            binding.etSearch.error = getString(R.string.search_query_required)
            return
        }
        searchAdapter.resetStates()
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

    private fun togglePreview(result: SearchResult) {
        val player = previewPlayer ?: return
        if (currentPreviewVideoId == result.videoId) {
            stopPreview()
            return
        }
        stopPreview()
        currentPreviewVideoId = result.videoId
        searchAdapter.setPlaying(result.videoId)

        lifecycleScope.launch {
            try {
                val extractor = YouTubeExtractor()
                val audioResult = withContext(Dispatchers.IO) {
                    extractor.getBestAudioStream(result.youtubeUrl)
                }
                if (audioResult.isSuccess) {
                    val audio = audioResult.getOrThrow()
                    val mediaItem = MediaItem.fromUri(audio.url)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                    player.seekTo(0)
                    previewHandler.postDelayed({ stopPreview() }, 40_000)
                } else {
                    stopPreview()
                    Toast.makeText(requireContext(), R.string.error_stream, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                stopPreview()
                Toast.makeText(requireContext(), R.string.error_stream, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopPreview() {
        previewHandler.removeCallbacksAndMessages(null)
        previewPlayer?.stop()
        previewPlayer?.clearMediaItems()
        currentPreviewVideoId?.let { searchAdapter.setIdle(it) }
        currentPreviewVideoId = null
    }

    override fun onDestroyView() {
        previewHandler.removeCallbacksAndMessages(null)
        previewPlayer?.release()
        previewPlayer = null
        _binding = null
        super.onDestroyView()
    }
}
