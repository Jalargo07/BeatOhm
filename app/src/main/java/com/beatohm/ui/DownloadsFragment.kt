package com.beatohm.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.inmobi.ads.InMobiBanner
import com.beatohm.ads.InMobiManager
import com.beatohm.R
import com.beatohm.databinding.FragmentDownloadsBinding
import com.beatohm.importer.UrlDetector
import com.beatohm.model.DownloadStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: DownloadAdapter
    private lateinit var searchAdapter: SearchResultAdapter
    private val seenErrorIds = mutableSetOf<String>()
    private var bannerView: InMobiBanner? = null
    private var initStateJob: Job? = null
    private var bannerRetryAttempt = 0
    private val bannerRetryHandler = Handler(Looper.getMainLooper())
    private val _combinedUiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val combinedUiState: StateFlow<UiState<Unit>> = _combinedUiState.asStateFlow()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        adapter = DownloadAdapter()

        searchAdapter = SearchResultAdapter(
            onDownload = { result ->
                searchAdapter.setDownloading(result.videoId)
                viewModel.downloadFromSearch(result)
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

        TutorialManager.showTutorial(
            requireActivity(),
            "downloads",
            listOf(
                TutorialManager.TooltipStep({ binding.etUrl }, getString(R.string.tutorial_dl_url), getString(R.string.tutorial_dl_url_desc)),
                TutorialManager.TooltipStep({ binding.btnSearch }, getString(R.string.tutorial_dl_search), getString(R.string.tutorial_dl_search_desc)),
                TutorialManager.TooltipStep({ binding.rvSearchResults }, getString(R.string.tutorial_dl_results), getString(R.string.tutorial_dl_results_desc))
            )
        )

        observeInitStateAndLoadBanner()

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

    private fun observeInitStateAndLoadBanner() {
        initStateJob = viewLifecycleOwner.lifecycleScope.launch {
            InMobiManager.initState.collectLatest { state ->
                when (state) {
                    is InMobiManager.InitState.Ready -> {
                        bannerRetryAttempt = 0
                        loadBanner()
                    }
                    is InMobiManager.InitState.Initializing -> {
                        Log.d(TAG, "InMobi initializing, waiting...")
                    }
                    is InMobiManager.InitState.Failed -> {
                        Log.w(TAG, "InMobi init failed, banner not available")
                    }
                }
            }
        }
    }

    private fun loadBanner() {
        if (!InMobiManager.isInitialized) {
            Log.w(TAG, "InMobi not ready, deferring banner")
            return
        }
        if (!isAdded) return

        destroyBanner()

        val bannerContainer = binding.adBannerContainer
        val banner = InMobiManager.createBanner(
            activity = requireActivity(),
            onLoaded = {
                Log.d(TAG, "Banner loaded")
                bannerRetryAttempt = 0
            },
            onFailed = {
                Log.e(TAG, "Banner failed to load")
                scheduleBannerRetry()
            }
        )
        bannerContainer.removeAllViews()
        bannerContainer.addView(banner)
        bannerView = banner
    }

    private fun scheduleBannerRetry() {
        if (!isAdded || bannerRetryAttempt >= MAX_BANNER_RETRIES) return
        val delayMs = BANNER_RETRY_BASE_MS * (1L shl bannerRetryAttempt)
        bannerRetryAttempt++
        Log.d(TAG, "Scheduling banner retry #$bannerRetryAttempt in ${delayMs}ms")
        bannerRetryHandler.postDelayed({
            if (isAdded && _binding != null) {
                loadBanner()
            }
        }, delayMs)
    }

    private fun destroyBanner() {
        bannerRetryHandler.removeCallbacksAndMessages(null)
        bannerView?.let { banner ->
            banner.setListener(object : com.inmobi.ads.listeners.BannerAdEventListener() {})
            (banner.parent as? ViewGroup)?.removeView(banner)
        }
        bannerView = null
    }

    override fun onDestroyView() {
        destroyBanner()
        initStateJob?.cancel()
        initStateJob = null
        _binding = null
        super.onDestroyView()
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
        if (_binding == null) return
        val downloadsEmpty = viewModel.downloads.value.orEmpty().isEmpty()
        val searchEmpty = viewModel.searchResults.value.orEmpty().isEmpty()
        val hasError = viewModel.searchError.value != null
        val isEmpty = downloadsEmpty && searchEmpty && !hasError
        _combinedUiState.value = if (isEmpty) UiState.Empty(R.string.downloads_empty) else UiState.Content(Unit)

        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
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

        val detection = UrlDetector.detect(url)

        when (detection.type) {
            UrlDetector.Type.YOUTUBE_SONG -> {
                viewModel.startDownload(url)
                binding.etUrl.text?.clear()
            }
            UrlDetector.Type.YOUTUBE_PLAYLIST,
            UrlDetector.Type.DEEZER_PLAYLIST,
            UrlDetector.Type.SPOTIFY_PLAYLIST -> {
                ImportPlaylistBottomSheet.newInstance(detection.url)
                    .show(childFragmentManager, ImportPlaylistBottomSheet.TAG)
                binding.etUrl.text?.clear()
            }
            UrlDetector.Type.UNKNOWN -> {
                binding.etUrl.error = getString(R.string.download_url_invalid)
            }
        }
    }

    companion object {
        private const val TAG = "DownloadsFragment"
        private const val MAX_BANNER_RETRIES = 3
        private const val BANNER_RETRY_BASE_MS = 5_000L
    }
}
