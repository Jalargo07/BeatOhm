package com.musicdownloader.ui

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.palette.graphics.Palette
import coil.Coil
import coil.load
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.musicdownloader.R
import com.musicdownloader.data.AppDatabase
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.data.PlaylistSong
import com.musicdownloader.data.toSong
import com.musicdownloader.databinding.FragmentPlayerBinding
import com.musicdownloader.ui.player.PlayerLayoutManager
import com.musicdownloader.model.Song
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PlayerViewModel
    private lateinit var repository: MusicRepository
    private var isSeeking = false
    private var isLyricsVisible = false
    private var updateRunnable: Runnable? = null
    private var currentSongFilePath: String? = null
    private var coverBreatheAnimator: ValueAnimator? = null

    private var audioManager: AudioManager? = null
    private var isVolumeDragging = false

    private val dynamicGradient = DynamicGradientDrawable()
    private val glowDrawable = GlowDrawable()
    private var primaryColor: Int = Color.BLACK
    private var titleTextColor: Int = Color.WHITE
    private var bodyTextColor: Int = Color.WHITE

    private var downX = 0f
    private var downY = 0f
    private var isDragging = false
    private var swipeDirection = 0
    private var swipeSpring: SpringAnimation? = null
    private val swipeThreshold by lazy { 100 * resources.displayMetrics.density }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION" && !isVolumeDragging) {
                val am = audioManager ?: return
                binding.volumeSeekbar.progress = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = PlayerViewModel.getInstance(requireActivity().application as Application)
        repository = MusicRepository(requireContext())
        audioManager = requireContext().getSystemService(AudioManager::class.java)
        primaryColor = ThemeManager.primaryColor
        requireActivity().volumeControlStream = AudioManager.STREAM_MUSIC

        binding.root.background = dynamicGradient
        binding.ivGlow.background = glowDrawable

        if (Build.VERSION.SDK_INT >= 31) {
            binding.ivGlow.setRenderEffect(
                RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP)
            )
        }

        PlayerLayoutManager.applyStyle(binding.root)
        setupObservers()
        setupControls()
        setupSwipeGesture()
        setupLyricsSwipe()
        applyIconPack()

        if (viewModel.currentSong.value == null) {
            binding.waveformSeekbar.setPlaceholder()
        }

        TutorialManager.showTutorial(
            requireActivity(),
            "player",
            listOf(
                TutorialManager.TooltipStep({ binding.btnPlayPause }, getString(R.string.tutorial_player_play), getString(R.string.tutorial_player_play_desc)),
                TutorialManager.TooltipStep({ binding.btnPrev }, getString(R.string.tutorial_player_prev), getString(R.string.tutorial_player_prev_desc)),
                TutorialManager.TooltipStep({ binding.btnNext }, getString(R.string.tutorial_player_next), getString(R.string.tutorial_player_next_desc)),
                TutorialManager.TooltipStep({ binding.btnShuffle }, getString(R.string.tutorial_player_shuffle), getString(R.string.tutorial_player_shuffle_desc)),
                TutorialManager.TooltipStep({ binding.btnRepeat }, getString(R.string.tutorial_player_repeat), getString(R.string.tutorial_player_repeat_desc)),
                TutorialManager.TooltipStep({ binding.btnQueue }, getString(R.string.tutorial_player_queue), getString(R.string.tutorial_player_queue_desc)),
                TutorialManager.TooltipStep({ binding.btnLyrics }, getString(R.string.tutorial_player_lyrics), getString(R.string.tutorial_player_lyrics_desc)),
                TutorialManager.TooltipStep({ binding.btnFavorite }, getString(R.string.tutorial_player_favorito), getString(R.string.tutorial_player_favorito_desc)),
                TutorialManager.TooltipStep({ binding.waveformSeekbar }, getString(R.string.tutorial_player_waveform), getString(R.string.tutorial_player_waveform_desc))
            )
        )
    }

    override fun onStart() {
        super.onStart()
        requireContext().registerReceiver(
            volumeReceiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        )
    }

    override fun onResume() {
        super.onResume()
        audioManager?.let { am ->
            binding.volumeSeekbar.max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            binding.volumeSeekbar.progress = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
    }

    override fun onStop() {
        requireContext().unregisterReceiver(volumeReceiver)
        super.onStop()
    }

    private fun setupObservers() {
        viewModel.currentSong.observe(viewLifecycleOwner) { song ->
            isLyricsVisible = false
            applyLyricsBlur(false)
            binding.lyricsPanel.visibility = View.GONE
            binding.ivLyricsBackground.visibility = View.GONE
            binding.coverContainer.visibility = View.VISIBLE
            binding.ivGlow.visibility = View.VISIBLE
            binding.titleContainer.visibility = View.VISIBLE
            binding.waveformSeekbar.visibility = View.VISIBLE
            binding.timeRow.visibility = View.VISIBLE
            binding.controlsContainer.visibility = View.VISIBLE
            binding.bottomActions.visibility = View.VISIBLE
            if (PlayerLayoutManager.currentStyle != "vinyl") {
                binding.ivCover.visibility = View.VISIBLE
            }
            if (song != null) {
                val path = song.filePath.ifBlank { song.youtubeUrl }
                currentSongFilePath = path
                binding.emptyPlayerState.visibility = View.GONE
                PlayerLayoutManager.animateSongChange(binding.root)
                animateSongChange(song, path)
                updateFavoriteIcon(path)
                loadWaveform(path, song.duration)
            } else {
                currentSongFilePath = null
                binding.emptyPlayerState.visibility = View.VISIBLE
                binding.tvTitle.text = getString(R.string.app_name)
                binding.tvArtist.text = ""
                binding.ivCover.setImageResource(R.drawable.ic_music_note)
                binding.pbCover.visibility = View.GONE
                binding.btnFavorite.setImageResource(R.drawable.ic_bookmark_border)
                binding.btnFavorite.colorFilter = null
                binding.ivGlow.animate().alpha(0f).setDuration(200).withEndAction {
                    if (_binding != null) binding.ivGlow.visibility = View.INVISIBLE
                }.start()
                stopCoverBreathe()
                applyPalette(null)
            }
        }

        viewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            val icons = IconPackManager.getPlayerIconResIds(ThemeManager.currentIconPack)
            binding.btnPlayPause.setImageResource(
                if (playing) icons[IconPackManager.ICON_PAUSE] ?: R.drawable.ic_pause
                else icons[IconPackManager.ICON_PLAY] ?: R.drawable.ic_play
            )
            if (playing) {
                animateCoverPlaying()
                PlayerLayoutManager.startVinylRotation(binding.root)
            } else {
                stopCoverBreathe()
                PlayerLayoutManager.stopVinylRotation(binding.root)
            }
        }

        viewModel.isShuffle.observe(viewLifecycleOwner) { shuffle ->
            binding.btnShuffle.alpha = if (shuffle) 1f else 0.4f
        }

        viewModel.repeatMode.observe(viewLifecycleOwner) { mode ->
            val icons = IconPackManager.getPlayerIconResIds(ThemeManager.currentIconPack)
            when (mode) {
                PlayerViewModel.RepeatMode.ALL -> {
                    binding.btnRepeat.setImageResource(icons[IconPackManager.ICON_REPEAT] ?: R.drawable.ic_repeat)
                    binding.btnRepeat.alpha = 1f
                }
                PlayerViewModel.RepeatMode.ONE -> {
                    binding.btnRepeat.setImageResource(icons[IconPackManager.ICON_REPEAT_ONE] ?: R.drawable.ic_repeat_one)
                    binding.btnRepeat.alpha = 1f
                }
                PlayerViewModel.RepeatMode.OFF -> {
                    binding.btnRepeat.setImageResource(icons[IconPackManager.ICON_REPEAT] ?: R.drawable.ic_repeat)
                    binding.btnRepeat.alpha = 0.4f
                }
            }
        }

        viewModel.duration.observe(viewLifecycleOwner) { dur ->
            binding.waveformSeekbar.max = MAX_SEEK
            binding.tvTotalTime.text = formatTime(dur)
        }
    }

    private fun currentDurationMs(): Long = viewModel.duration.value ?: 0L

    private fun updateFavoriteIcon(filePath: String) {
        lifecycleScope.launch {
            val song = repository.getSongById(filePath)
            val isFav = song?.isFavorite ?: false
            binding.btnFavorite.setImageResource(
                if (isFav) R.drawable.ic_bookmark
                else R.drawable.ic_bookmark_border
            )
            binding.btnFavorite.colorFilter = if (isFav) {
                PorterDuffColorFilter(
                    ThemeManager.accentColor,
                    PorterDuff.Mode.SRC_IN
                )
            } else {
                null
            }
        }
    }

    private fun loadWaveform(path: String, durationMs: Long = 180_000L) {
        lifecycleScope.launch {
            val waveform = withContext(Dispatchers.IO) {
                // 1. Check memory cache first
                WaveformSeekBar.getCachedWaveform(path)
                    ?: repository.getSongById(path)?.waveformData?.takeIf { it.isNotBlank() }?.let { json ->
                        // 2. Parse from DB
                        val type = object : TypeToken<List<Float>>() {}.type
                        val list: List<Float> = try { Gson().fromJson(json, type) } catch (_: Exception) { emptyList() }
                        val data = if (list.isNotEmpty()) FloatArray(list.size) { list[it] } else null
                        // 3. Cache for next time
                        data?.also { WaveformSeekBar.cacheWaveform(path, it) }
                    }
                    ?: run {
                        // 4. Not in DB yet — extract now (background)
                        try {
                            val meta = android.media.MediaMetadataRetriever()
                            val realDurationMs = try {
                                meta.setDataSource(path)
                                meta.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: durationMs
                            } catch (_: Exception) { durationMs } finally { meta.release() }
                            val numBars = com.musicdownloader.audio.WaveformExtractor.barsForDuration(realDurationMs)
                            val data = com.musicdownloader.audio.WaveformExtractor.extract(path, numBars)
                            WaveformSeekBar.cacheWaveform(path, data)
                            // Store in DB for future
                            withContext(Dispatchers.IO) {
                                repository.getSongById(path)?.let { song ->
                                    val json = Gson().toJson(data.toList())
                                    repository.updateWaveform(song.id, json)
                                }
                            }
                            data
                        } catch (_: Exception) { null }
                    }
            }
            if (waveform != null && _binding != null) {
                binding.waveformSeekbar.setWaveformData(waveform)
            }
        }
    }

    private fun animateFavoriteHeart() {
        val btn = binding.btnFavorite
        btn.animate().cancel()
        btn.scaleX = 1f
        btn.scaleY = 1f
        btn.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                btn.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
            .start()
    }

    private fun animateSongChange(song: Song, path: String) {
        val density = resources.displayMetrics.density
        cancelSwipeAnimations()
        binding.ivCover.animate().cancel()
        binding.titleTextContainer.animate().cancel()

        binding.ivCover.animate()
            .alpha(0f)
            .setDuration(100)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (_binding == null) return@withEndAction
                binding.ivCover.translationX = 0f
                binding.ivCover.alpha = 0f
                binding.ivCover.scaleX = 1f
                binding.ivCover.scaleY = 1f
                binding.ivCoverPreview.alpha = 0f
                binding.ivCoverPreview.translationX = 0f
                binding.ivCoverPreview.setImageDrawable(null)
                binding.tvTitle.text = song.title
                binding.tvArtist.text = song.artist.ifBlank { getString(R.string.unknown_artist) }
                binding.ivCover.contentDescription = "${song.title} - ${song.artist}"
                loadCover(song, path)
                binding.titleTextContainer.translationY = 20 * density
                binding.titleTextContainer.alpha = 0f
                binding.titleTextContainer.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                binding.ivCover.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun extractBitmap(drawable: Drawable?): Bitmap? {
        return when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            is TransitionDrawable -> {
                for (i in drawable.numberOfLayers - 1 downTo 0) {
                    val layer = drawable.getDrawable(i)
                    val bitmap = (layer as? BitmapDrawable)?.bitmap
                    if (bitmap != null) return bitmap
                }
                null
            }
            else -> null
        }
    }

    private fun loadCover(song: Song, path: String) {
        if (PlayerLayoutManager.currentStyle == "vinyl") {
            loadCoverForVinyl(song, path)
            return
        }
        if (song.thumbnailUrl.isNotBlank()) {
            binding.pbCover.visibility = View.VISIBLE
            binding.ivCover.load(song.thumbnailUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_player)
                error(R.drawable.ic_player)
                listener(
                    onSuccess = { _, result ->
                        if (_binding != null) binding.pbCover.visibility = View.GONE
                        val bmp = extractBitmap(result.drawable)
                            ?: if (_binding != null) extractBitmap(binding.ivCover.drawable) else null
                        applyPalette(bmp)
                        PlayerLayoutManager.updateVinylArtwork(bmp)
                    },
                    onError = { _, _ ->
                        if (_binding != null) binding.pbCover.visibility = View.GONE
                        applyPalette(null)
                        PlayerLayoutManager.updateVinylArtwork(null)
                    }
                )
            }
        } else if (path.isNotBlank() && File(path).exists()) {
            binding.pbCover.visibility = View.GONE
            binding.ivCover.tag = path
            ArtworkLoader.loadArtFromAudioFile(binding.ivCover, path)
            lifecycleScope.launch {
                val bitmap = ArtworkLoader.loadBitmapFor(path)
                if (_binding != null) applyPalette(bitmap)
                if (_binding != null) PlayerLayoutManager.updateVinylArtwork(bitmap)
            }
        } else {
            binding.pbCover.visibility = View.GONE
            binding.ivCover.setImageResource(R.drawable.ic_player)
            applyPalette(null)
            PlayerLayoutManager.updateVinylArtwork(null)
        }
    }

    private fun loadCoverForVinyl(song: Song, path: String) {
        binding.pbCover.visibility = View.GONE
        lifecycleScope.launch {
            var bitmap: Bitmap? = null
            if (song.thumbnailUrl.isNotBlank()) {
                try {
                    val request = ImageRequest.Builder(requireContext().applicationContext)
                        .data(song.thumbnailUrl)
                        .build()
                    val result = Coil.imageLoader(requireContext().applicationContext).execute(request)
                    bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                } catch (e: Exception) {
                    bitmap = null
                }
            }
            if (bitmap == null) {
                bitmap = ArtworkLoader.loadBitmapFor(path)
            }
            if (_binding != null) {
                PlayerLayoutManager.updateVinylArtwork(bitmap)
                applyPalette(bitmap)
            }
        }
    }

    private fun applyPalette(bitmap: Bitmap?) {
        val gradientMode = ThemeManager.playerGradient
        if (bitmap == null || gradientMode == 1) {
            dynamicGradient.resetToDefault(PALETTE_DURATION)
            glowDrawable.setColor(primaryColor, PALETTE_DURATION)
            titleTextColor = Color.WHITE
            bodyTextColor = Color.WHITE
            binding.tvTitle.setTextColor(titleTextColor)
            binding.tvArtist.setTextColor(bodyTextColor)
            applyThemeFont()
            return
        }
        when (gradientMode) {
            2 -> {
                dynamicGradient.setPrimaryGradient(ThemeManager.primaryColor, PALETTE_DURATION)
                glowDrawable.setColor(ThemeManager.primaryColor, PALETTE_DURATION)
                titleTextColor = Color.WHITE
                bodyTextColor = Color.WHITE
                binding.tvTitle.setTextColor(titleTextColor)
                binding.tvArtist.setTextColor(bodyTextColor)
                applyThemeFont()
                return
            }
            3 -> {
                dynamicGradient.setNeutralDark(PALETTE_DURATION)
                glowDrawable.setColor(primaryColor, PALETTE_DURATION)
                titleTextColor = Color.WHITE
                bodyTextColor = Color.WHITE
                binding.tvTitle.setTextColor(titleTextColor)
                binding.tvArtist.setTextColor(bodyTextColor)
                applyThemeFont()
                return
            }
        }
        try {
            // createScaledBitmap siempre devuelve un bitmap software (Palette no lee HARDWARE)
            val scale = min(1f, PALETTE_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height))
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(bitmap, w, h, false)
            lifecycleScope.launch {
                try {
                    val palette = withContext(Dispatchers.Default) { Palette.from(small).generate() }
                    if (_binding == null) return@launch
                    val dominant = palette.getDominantColor(primaryColor)
                    val vibrant = palette.getVibrantColor(primaryColor)
                    val muted = palette.getMutedColor(primaryColor)
                    val darkVibrant = palette.getDarkVibrantColor(primaryColor)
                    val darkMuted = palette.getDarkMutedColor(primaryColor)
                    val lightVibrant = palette.getLightVibrantColor(primaryColor)
                    val dominantSwatch = palette.dominantSwatch
                    titleTextColor = dominantSwatch?.titleTextColor ?: Color.WHITE
                    bodyTextColor = dominantSwatch?.bodyTextColor ?: Color.WHITE
                    binding.tvTitle.setTextColor(titleTextColor)
                    binding.tvArtist.setTextColor(bodyTextColor)
                    dynamicGradient.setColors(
                        dominant,
                        vibrant,
                        muted,
                        darkVibrant,
                        darkMuted,
                        lightVibrant,
                        PALETTE_DURATION
                    )
                    glowDrawable.setColor(dominant, PALETTE_DURATION)
                    applyThemeFont()
                } catch (e: Exception) {
                    // Palette generation failed, use defaults
                    dynamicGradient.resetToDefault(PALETTE_DURATION)
                    glowDrawable.setColor(primaryColor, PALETTE_DURATION)
                    applyThemeFont()
                }
            }
        } catch (e: Exception) {
            // Bitmap scaling failed, use defaults
            dynamicGradient.resetToDefault(PALETTE_DURATION)
            glowDrawable.setColor(primaryColor, PALETTE_DURATION)
            applyThemeFont()
        }
    }

    private fun applyThemeFont() {
        val typeface = when (ThemeManager.fontFamily) {
            "serif" -> Typeface.create("serif", Typeface.NORMAL)
            "monospace" -> Typeface.create("monospace", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
        binding.tvTitle.typeface = Typeface.create(typeface, Typeface.BOLD)
        binding.tvArtist.typeface = typeface
    }

    private fun animateCoverPlaying() {
        stopCoverBreathe()
        val sf = PlayerLayoutManager.currentScaleFactor
        binding.coverContainer.scaleX = sf * 0.95f
        binding.coverContainer.scaleY = sf * 0.95f
        binding.coverContainer.animate()
            .scaleX(sf)
            .scaleY(sf)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (_binding != null) startCoverBreathe()
            }
            .start()
    }

    private fun startCoverBreathe() {
        val sf = PlayerLayoutManager.currentScaleFactor
        val animator = ValueAnimator.ofFloat(sf * 0.98f, sf).apply {
            duration = 1600L
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                binding.coverContainer.scaleX = value
                binding.coverContainer.scaleY = value
            }
            start()
        }
        coverBreatheAnimator = animator
    }

    private fun stopCoverBreathe() {
        coverBreatheAnimator?.cancel()
        coverBreatheAnimator = null
        binding.coverContainer.animate().cancel()
        binding.coverContainer.alpha = 1f
        binding.coverContainer.scaleX = PlayerLayoutManager.currentScaleFactor
        binding.coverContainer.scaleY = PlayerLayoutManager.currentScaleFactor
    }

    private fun animatePlayPausePress() {
        binding.btnPlayPause.scaleX = 0.9f
        binding.btnPlayPause.scaleY = 0.9f
        binding.btnPlayPause.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(BounceInterpolator())
            .start()
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            val song = viewModel.currentSong.value ?: return@setOnClickListener
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService
            if (service == null) return@setOnClickListener

            animatePlayPausePress()

            if (service.isPlaying()) service.pause() else service.play()
        }

        binding.btnNext.setOnClickListener {
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService ?: return@setOnClickListener
            val nextSong = viewModel.nextSong() ?: return@setOnClickListener
            val path = nextSong.filePath.ifBlank { nextSong.youtubeUrl }
            if (path.isNotBlank()) {
                service.playFile(path)
            }
        }

        binding.btnPrev.setOnClickListener {
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService ?: return@setOnClickListener
            val prevSong = viewModel.prevSong() ?: return@setOnClickListener
            val path = prevSong.filePath.ifBlank { prevSong.youtubeUrl }
            if (path.isNotBlank()) {
                service.playFile(path)
            }
        }

        binding.btnShuffle.setOnClickListener {
            viewModel.toggleShuffle()
        }

        binding.btnRepeat.setOnClickListener {
            viewModel.toggleRepeatMode()
        }

        binding.btnQueue.setOnClickListener {
            QueueBottomSheetDialogFragment().show(childFragmentManager, "queue")
        }

        binding.btnEqualizer.setOnClickListener {
            val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return@setOnClickListener
            val service = activity.playbackService ?: return@setOnClickListener
            EqualizerDialog(requireContext(), service.getAudioSessionId()).show()
        }

        binding.btnLyrics.setOnClickListener {
            val song = viewModel.currentSong.value ?: return@setOnClickListener
            var lyrics = song.lyrics
            // Try reading from file tags if DB lyrics are empty
            if (lyrics.isBlank()) {
                val path = song.filePath.ifBlank { song.youtubeUrl }
                if (path.isNotBlank()) {
                    lyrics = com.musicdownloader.data.AudioTagReader.readLyrics(path)
                }
            }
            if (lyrics.isBlank()) {
                Toast.makeText(requireContext(), R.string.no_lyrics, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleLyrics()
        }

        binding.btnFavorite.setOnClickListener {
            val path = currentSongFilePath ?: return@setOnClickListener
            lifecycleScope.launch {
                val song = repository.getSongById(path)
                if (song != null) {
                    val newFav = !song.isFavorite
                    repository.setFavorite(path, newFav)
                    updateFavoriteIcon(path)
                    if (newFav) animateFavoriteHeart()
                }
            }
        }

        binding.btnAddPlaylist.setOnClickListener {
            val path = currentSongFilePath ?: return@setOnClickListener
            showAddToPlaylistDialog(path)
        }

        binding.waveformSeekbar.max = MAX_SEEK
        binding.waveformSeekbar.onProgressChanged = { progress ->
            isSeeking = true
            val dur = currentDurationMs()
            binding.tvCurrentTime.text = formatTime(if (dur > 0) progress.toLong() * dur / MAX_SEEK else 0L)
            binding.controlsContainer.animate().cancel()
            binding.controlsContainer.alpha = 0.1f
        }
        binding.waveformSeekbar.onProgressStop = { progress ->
            isSeeking = false
            val activity = requireActivity() as? com.musicdownloader.MainActivity
            val dur = currentDurationMs()
            val target = if (dur > 0) progress.toLong() * dur / MAX_SEEK else 0L
            activity?.playbackService?.seekTo(target)
            binding.controlsContainer.animate().cancel()
            binding.controlsContainer.animate().alpha(1f).setDuration(120).start()
        }

        binding.waveformSeekbar.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.controlsContainer.animate().cancel()
                    binding.controlsContainer.animate().alpha(1f).setDuration(120).start()
                }
            }
            false
        }

        binding.volumeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isVolumeDragging = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { isVolumeDragging = false }
        })

        startPositionUpdater()
    }

    private fun setupSwipeGesture() {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        binding.coverContainer.setOnTouchListener { _, event ->
            if (isLyricsVisible || viewModel.currentSong.value == null) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    isDragging = true
                    swipeDirection = 0
                    cancelSwipeAnimations()
                    binding.ivCover.animate()
                        .scaleX(0.95f).scaleY(0.95f)
                        .setDuration(100)
                        .start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging) return@setOnTouchListener false
                    val deltaX = event.x - downX
                    if (swipeDirection == 0 && abs(deltaX) > touchSlop) {
                        swipeDirection = if (deltaX > 0) 1 else -1
                        val song = if (swipeDirection > 0) viewModel.peekNext() else viewModel.peekPrev()
                        loadPreviewCover(song)
                    }
                    if (swipeDirection != 0) {
                        val coverWidth = coverWidthPx()
                        binding.ivCover.translationX = deltaX
                        binding.ivCoverPreview.translationX = deltaX - swipeDirection * coverWidth * 0.35f
                        val progress = min(abs(deltaX) / swipeThreshold, 1f)
                        binding.ivCoverPreview.alpha = progress * 0.9f
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) return@setOnTouchListener false
                    isDragging = false
                    val deltaX = event.x - downX
                    if (abs(deltaX) > swipeThreshold) {
                        commitSwipe(if (deltaX > 0) 1 else -1)
                    } else {
                        springCoverBack()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    springCoverBack()
                    true
                }
                else -> false
            }
        }
    }

    private fun coverWidthPx(): Float = binding.coverContainer.width.toFloat().coerceAtLeast(1f)

    private fun setupLyricsSwipe() {
        val swipeCloseThreshold = 100 * resources.displayMetrics.density
        binding.lyricsPanel.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> downY = event.y
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val deltaY = event.y - downY
                    if (isLyricsVisible && deltaY > swipeCloseThreshold) {
                        closeLyrics()
                    }
                }
                else -> {}
            }
            false
        }
    }

    private fun loadPreviewCover(song: Song?) {
        val iv = binding.ivCoverPreview
        if (song == null) {
            iv.setImageResource(R.drawable.ic_player)
            return
        }
        val path = song.filePath.ifBlank { song.youtubeUrl }
        if (song.thumbnailUrl.isNotBlank()) {
            iv.load(song.thumbnailUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_player)
                error(R.drawable.ic_player)
            }
        } else if (path.isNotBlank() && File(path).exists()) {
            ArtworkLoader.loadArtFromAudioFile(iv, path)
        } else {
            iv.setImageResource(R.drawable.ic_player)
        }
    }

    private fun commitSwipe(direction: Int) {
        cancelSwipeAnimations()
        val sign = direction.toFloat()
        val coverWidth = coverWidthPx()
        binding.ivCoverPreview.animate().alpha(0f).setDuration(120).start()
        binding.ivCover.animate()
            .translationX(sign * coverWidth * 0.6f)
            .alpha(0f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (_binding == null) return@withEndAction
                binding.ivCover.translationX = 0f
                binding.ivCover.alpha = 0f
                performSongChange(direction)
            }
            .start()
    }

    private fun performSongChange(direction: Int) {
        val activity = requireActivity() as? com.musicdownloader.MainActivity ?: return
        val service = activity.playbackService ?: return
        val song = if (direction > 0) viewModel.nextSong() else viewModel.prevSong()
        if (song != null) {
            val path = song.filePath.ifBlank { song.youtubeUrl }
            if (path.isNotBlank()) service.playFile(path)
        } else {
            binding.ivCover.translationX = 0f
            binding.ivCover.alpha = 1f
            springCoverBack()
        }
    }

    private fun springCoverBack() {
        cancelSwipeAnimations()
        swipeSpring = SpringAnimation(binding.ivCover, DynamicAnimation.TRANSLATION_X, 0f).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = 0.7f
                stiffness = 300f
            }
            start()
        }
        binding.ivCover.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(150)
            .start()
        binding.ivCoverPreview.animate().alpha(0f).setDuration(150).start()
        binding.ivCoverPreview.translationX = 0f
    }

    private fun cancelSwipeAnimations() {
        swipeSpring?.cancel()
        swipeSpring = null
        binding.ivCover.animate().cancel()
        binding.ivCoverPreview.animate().cancel()
    }

    private fun toggleLyrics() {
        isLyricsVisible = !isLyricsVisible
        if (isLyricsVisible) {
            val song = viewModel.currentSong.value
            var lyrics = song?.lyrics.orEmpty()
            // Try reading from file tags if DB lyrics are empty
            if (lyrics.isBlank()) {
                val path = song?.filePath?.ifBlank { song.youtubeUrl }.orEmpty()
                if (path.isNotBlank()) {
                    lyrics = com.musicdownloader.data.AudioTagReader.readLyrics(path)
                }
            }
            binding.syncedLyricsView.setLyrics(lyrics)
            binding.syncedLyricsView.onLineClicked = { positionMs ->
                (requireActivity() as? com.musicdownloader.MainActivity)?.playbackService?.seekTo(positionMs)
            }
            binding.syncedLyricsView.onSwipeDown = {
                closeLyrics()
            }
            loadLyricsBackground(song)
            binding.ivLyricsBackground.visibility = View.VISIBLE
            binding.lyricsPanel.visibility = View.VISIBLE
            binding.coverContainer.visibility = View.INVISIBLE
            binding.ivCover.visibility = View.INVISIBLE
            binding.ivGlow.visibility = View.INVISIBLE
            binding.titleContainer.visibility = View.INVISIBLE
            binding.waveformSeekbar.visibility = View.INVISIBLE
            binding.timeRow.visibility = View.INVISIBLE
            binding.controlsContainer.visibility = View.INVISIBLE
            binding.bottomActions.visibility = View.INVISIBLE

            binding.lyricsPanel.alpha = 0f
            binding.lyricsPanel.scaleX = 0.97f
            binding.lyricsPanel.scaleY = 0.97f
            binding.lyricsPanel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .start()

            applyLyricsBlur(true)
            val pos = (requireActivity() as? com.musicdownloader.MainActivity)?.playbackService?.getCurrentPosition() ?: 0L
            binding.syncedLyricsView.updatePosition(pos)

            TutorialManager.showTutorial(
                requireActivity(),
                "lyrics",
                listOf(
                    TutorialManager.TooltipStep({ binding.syncedLyricsView }, getString(R.string.tutorial_lyrics_tap), getString(R.string.tutorial_lyrics_tap_desc)),
                    TutorialManager.TooltipStep({ binding.syncedLyricsView }, getString(R.string.tutorial_lyrics_swipe), getString(R.string.tutorial_lyrics_swipe_desc))
                )
            )
        } else {
            closeLyrics()
        }
    }

    private fun loadLyricsBackground(song: Song?) {
        val iv = binding.ivLyricsBackground
        if (song != null && song.thumbnailUrl.isNotBlank()) {
            iv.load(song.thumbnailUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_player)
                error(R.drawable.ic_player)
            }
        } else {
            val path = when {
                song == null -> ""
                song.filePath.isNotBlank() -> song.filePath
                else -> song.youtubeUrl
            }
            if (path.isNotBlank() && File(path).exists()) {
                ArtworkLoader.loadArtFromAudioFile(iv, path)
            } else {
                iv.setImageResource(R.drawable.ic_player)
            }
        }
        applyLyricsBackgroundTint()
    }

    private fun applyLyricsBackgroundTint() {
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0.35f)
        }
        binding.ivLyricsBackground.colorFilter = ColorMatrixColorFilter(colorMatrix)
        if (Build.VERSION.SDK_INT >= 31) {
            binding.ivLyricsBackground.setRenderEffect(
                RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.CLAMP)
            )
        }
    }

    private fun closeLyrics() {
        isLyricsVisible = false
        applyLyricsBlur(false)
        val panelHeight = binding.lyricsPanel.height.toFloat()
        binding.lyricsPanel.animate()
            .alpha(0f)
            .translationY(panelHeight * 0.3f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (_binding != null) {
                    binding.lyricsPanel.translationY = 0f
                    binding.lyricsPanel.visibility = View.GONE
                    binding.ivLyricsBackground.visibility = View.GONE
                    binding.ivLyricsBackground.setImageDrawable(null)
                    binding.ivLyricsBackground.colorFilter = null
                    if (Build.VERSION.SDK_INT >= 31) {
                        binding.ivLyricsBackground.setRenderEffect(null)
                    }
                    binding.coverContainer.visibility = View.VISIBLE
                    binding.ivGlow.visibility = View.VISIBLE
                    if (PlayerLayoutManager.currentStyle != "vinyl") {
                        binding.ivCover.visibility = View.VISIBLE
                    }
                    binding.titleContainer.visibility = View.VISIBLE
                    binding.waveformSeekbar.visibility = View.VISIBLE
                    binding.timeRow.visibility = View.VISIBLE
                    binding.controlsContainer.visibility = View.VISIBLE
                    binding.bottomActions.visibility = View.VISIBLE
                }
            }
            .start()
    }

    private fun applyLyricsBlur(blur: Boolean) {
        // Blur effect removed - not critical for functionality
        // Can be re-implemented later with compatible API
    }

    private fun showAddToPlaylistDialog(songFilePath: String) {
        val db = AppDatabase.getInstance(requireContext())
        lifecycleScope.launch {
            val list = db.playlistDao().getAllPlaylists().first()
            if (list.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.sin_playlists))
                    .setMessage(getString(R.string.crea_playlist_primero))
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }
            val names = list.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.agregar_a_playlist))
                .setItems(names) { _, which ->
                    val playlist = list[which]
                    lifecycleScope.launch {
                        db.playlistDao().addSongToPlaylist(
                            PlaylistSong(playlist.id, songFilePath, 0)
                        )
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun startPositionUpdater() {
        updateRunnable = object : Runnable {
            override fun run() {
                val b = _binding ?: return
                val activity = b.root.context as? com.musicdownloader.MainActivity
                val service = activity?.playbackService
                if (service != null && !isSeeking) {
                    val pos = service.getCurrentPosition()
                    var dur = currentDurationMs()
                    if (dur <= 0L) {
                        val serviceDur = service.getDuration()
                        if (serviceDur > 0L && serviceDur != C.TIME_UNSET) {
                            dur = serviceDur
                            viewModel.setDuration(dur)
                        }
                    }
                    val progress = if (dur > 0) ((pos * MAX_SEEK) / dur).toInt() else 0
                    b.waveformSeekbar.setProgress(progress.coerceIn(0, MAX_SEEK))
                    b.tvCurrentTime.text = formatTime(pos)
                    viewModel.setPosition(pos)
                    if (isLyricsVisible) {
                        b.syncedLyricsView.updatePosition(pos)
                    }
                }
                b.root.postDelayed(this, 500)
            }
        }
        _binding?.root?.postDelayed(updateRunnable!!, 500)
    }

    private fun formatTime(millis: Long): String {
        val mins = TimeUnit.MILLISECONDS.toMinutes(millis)
        val secs = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return "%d:%02d".format(mins, secs)
    }

    override fun onDestroyView() {
        PlayerLayoutManager.stopVinylRotation(binding.root)
        PlayerLayoutManager.removeVinylViewIfAny(binding.root)
        stopCoverBreathe()
        cancelSwipeAnimations()
        updateRunnable?.let { binding.root.removeCallbacks(it) }
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val MAX_SEEK = 1000
        private const val PALETTE_DURATION = 2000L
        private const val PALETTE_SIZE = 128
    }

    /**
     * Apply the active icon pack's icons to player control buttons.
     */
    private fun applyIconPack() {
        val icons = IconPackManager.getPlayerIconResIds(ThemeManager.currentIconPack)
        val isPlaying = viewModel.isPlaying.value == true

        binding.btnPlayPause.setImageResource(
            if (isPlaying) icons[IconPackManager.ICON_PAUSE] ?: R.drawable.ic_pause
            else icons[IconPackManager.ICON_PLAY] ?: R.drawable.ic_play
        )
        binding.btnNext.setImageResource(icons[IconPackManager.ICON_NEXT] ?: R.drawable.ic_next)
        binding.btnPrev.setImageResource(icons[IconPackManager.ICON_PREV] ?: R.drawable.ic_prev)
        binding.btnShuffle.setImageResource(icons[IconPackManager.ICON_SHUFFLE] ?: R.drawable.ic_shuffle)
        binding.btnRepeat.setImageResource(icons[IconPackManager.ICON_REPEAT] ?: R.drawable.ic_repeat)
        binding.btnEqualizer.setImageResource(icons[IconPackManager.ICON_EQUALIZER] ?: R.drawable.ic_equalizer)
        binding.btnQueue.setImageResource(icons[IconPackManager.ICON_QUEUE] ?: R.drawable.ic_queue_music)
        binding.btnLyrics.setImageResource(icons[IconPackManager.ICON_LYRICS] ?: R.drawable.ic_lyrics)
    }
}
