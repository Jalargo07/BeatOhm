package com.musicdownloader.ui

import android.content.ClipData
import android.content.Context
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.BuildConfig
import com.musicdownloader.R
import com.musicdownloader.databinding.FragmentSettingsBinding
import com.musicdownloader.model.PatternToken
import com.musicdownloader.model.Song
import com.musicdownloader.util.FolderPatternParser

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var tokenBankAdapter: TokenBankAdapter
    private lateinit var patternBuilderAdapter: PatternBuilderAdapter
    private val patternTokens = mutableListOf<PatternToken>()

    private val sampleSong = Song(
        title = "In The Flesh?",
        artist = "Pink Floyd",
        album = "The Wall",
        trackNumber = 1,
        year = "1979",
        genre = "Rock"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemeManager.init(requireContext())

        patternTokens.clear()
        patternTokens.addAll(tokensFromPattern(currentPattern()))

        setupTokenBank()
        setupPatternBuilder()
        refreshFromBuilder()

        binding.btnSave.setOnClickListener {
            val pattern = buildPatternString().trim()
            if (pattern.isBlank()) {
                Toast.makeText(requireContext(), R.string.folder_pattern_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs().edit().putString(FolderPatternParser.KEY_FOLDER_PATTERN, pattern).apply()
            Toast.makeText(requireContext(), R.string.folder_pattern_saved, Toast.LENGTH_SHORT).show()
        }

        binding.btnReset.setOnClickListener {
            patternTokens.clear()
            patternTokens.addAll(tokensFromPattern(FolderPatternParser.DEFAULT_PATTERN))
            refreshFromBuilder()
            prefs().edit().putString(FolderPatternParser.KEY_FOLDER_PATTERN, FolderPatternParser.DEFAULT_PATTERN).apply()
            Toast.makeText(requireContext(), R.string.folder_pattern_saved, Toast.LENGTH_SHORT).show()
        }

        setupAppearance()
    }

    private fun setupAppearance() {
        binding.tvAboutVersion.text = getString(R.string.settings_about_version, BuildConfig.VERSION_NAME)

        setupPrimaryColorGrid()
        setupAccentColorGrid()
        setupFontChips()
        setupNightModeChips()
        setupGradientChips()
    }

    private fun setupPrimaryColorGrid() {
        val adapter = ColorPaletteAdapter(
            ThemeManager.PRIMARY_COLORS,
            ThemeManager.primaryColorIndex()
        ) { color ->
            ThemeManager.primaryColor = color
            requireActivity().recreate()
        }
        binding.rvPrimaryColors.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.rvPrimaryColors.adapter = adapter
    }

    private fun setupAccentColorGrid() {
        val adapter = ColorPaletteAdapter(
            ThemeManager.ACCENT_COLORS,
            ThemeManager.accentColorIndex()
        ) { color ->
            ThemeManager.accentColor = color
            requireActivity().recreate()
        }
        binding.rvAccentColors.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.rvAccentColors.adapter = adapter
    }

    private fun setupFontChips() {
        val currentFont = ThemeManager.fontFamily
        when (currentFont) {
            "default" -> binding.chipFontDefault.isChecked = true
            "serif" -> binding.chipFontSerif.isChecked = true
            "monospace" -> binding.chipFontMonospace.isChecked = true
        }

        binding.chipGroupFont.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val font = when (checkedIds.first()) {
                R.id.chip_font_serif -> "serif"
                R.id.chip_font_monospace -> "monospace"
                else -> "default"
            }
            ThemeManager.fontFamily = font
            requireActivity().recreate()
        }
    }

    private fun setupNightModeChips() {
        val currentMode = ThemeManager.nightMode
        when (currentMode) {
            0 -> binding.chipNightSystem.isChecked = true
            1 -> binding.chipNightLight.isChecked = true
            2 -> binding.chipNightDark.isChecked = true
        }

        binding.chipGroupNight.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val mode = when (checkedIds.first()) {
                R.id.chip_night_light -> 1
                R.id.chip_night_dark -> 2
                else -> 0
            }
            ThemeManager.nightMode = mode
            ThemeManager.applyNightMode()
            requireActivity().recreate()
        }
    }

    private fun setupGradientChips() {
        val currentGradient = ThemeManager.playerGradient
        when (currentGradient) {
            0 -> binding.chipGradientAuto.isChecked = true
            1 -> binding.chipGradientStatic.isChecked = true
            2 -> binding.chipGradientPrimary.isChecked = true
            3 -> binding.chipGradientDark.isChecked = true
        }

        binding.chipGroupGradient.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val gradient = when (checkedIds.first()) {
                R.id.chip_gradient_static -> 1
                R.id.chip_gradient_primary -> 2
                R.id.chip_gradient_dark -> 3
                else -> 0
            }
            ThemeManager.playerGradient = gradient
        }
    }

    private fun setupTokenBank() {
        tokenBankAdapter = TokenBankAdapter { token, view ->
            val index = PatternToken.available.indexOf(token)
            if (index >= 0) {
                val clip = ClipData.newPlainText(CLIP_LABEL, index.toString())
                view.startDragAndDrop(clip, View.DragShadowBuilder(view), null, 0)
            }
        }
        binding.rvTokenBank.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.rvTokenBank.adapter = tokenBankAdapter
    }

    private fun setupPatternBuilder() {
        patternBuilderAdapter = PatternBuilderAdapter(onRemove = { position ->
            patternTokens.removeAt(position)
            refreshFromBuilder()
        })
        binding.rvPatternBuilder.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.rvPatternBuilder.adapter = patternBuilderAdapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                patternBuilderAdapter.moveItem(from, to)
                syncTokensFromAdapter()
                updateHintAndPreview()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = true
        })
        touchHelper.attachToRecyclerView(binding.rvPatternBuilder)

        binding.rvPatternBuilder.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    val isToken = event.clipData?.description?.label == CLIP_LABEL
                    if (isToken) {
                        binding.cardPatternDrop.setCardBackgroundColor(
                            ContextCompat.getColor(requireContext(), R.color.surface_high)
                        )
                    }
                    isToken
                }
                DragEvent.ACTION_DROP -> {
                    val isToken = event.clipData?.description?.label == CLIP_LABEL
                    if (isToken) {
                        val tokenIndex = event.clipData
                            ?.getItemAt(0)
                            ?.text
                            ?.toString()
                            ?.toIntOrNull()
                        val token = tokenIndex?.let { PatternToken.available.getOrNull(it) }
                        if (token != null) {
                            val position = dropPositionFromX(v as RecyclerView, event.x)
                            patternTokens.add(position.coerceIn(0, patternTokens.size), token)
                            refreshFromBuilder()
                        }
                    }
                    isToken
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    binding.cardPatternDrop.setCardBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.surface)
                    )
                    true
                }
                else -> true
            }
        }
    }

    private fun syncTokensFromAdapter() {
        patternTokens.clear()
        patternTokens.addAll(patternBuilderAdapter.currentTokens())
    }

    private fun refreshFromBuilder() {
        patternBuilderAdapter.submit(patternTokens)
        updateHintAndPreview()
    }

    private fun updateHintAndPreview() {
        binding.tvBuilderHint.visibility = if (patternTokens.isEmpty()) View.VISIBLE else View.GONE
        refreshPreview()
    }

    private fun dropPositionFromX(rv: RecyclerView, x: Float): Int {
        val lm = rv.layoutManager as? LinearLayoutManager ?: return 0
        for (i in 0 until lm.childCount) {
            val child = lm.getChildAt(i) ?: continue
            if (x < child.left + child.width / 2f) {
                return rv.getChildAdapterPosition(child)
            }
        }
        return rv.adapter?.itemCount ?: 0
    }

    private fun tokensFromPattern(pattern: String): List<PatternToken> {
        val tokens = mutableListOf<PatternToken>()
        val placeholderRegex = Regex("\\{([A-Za-z]+)\\}")
        var lastIndex = 0
        for (match in placeholderRegex.findAll(pattern)) {
            if (match.range.first > lastIndex) {
                val separator = pattern.substring(lastIndex, match.range.first)
                if (separator.isNotBlank()) {
                    tokens.add(PatternToken(PatternToken.Type.SEPARATOR, separator, separator))
                }
            }
            val key = match.groupValues[1]
            val known = PatternToken.available.firstOrNull {
                it.type == PatternToken.Type.PLACEHOLDER && it.key == key
            }
            if (known != null) {
                tokens.add(PatternToken(PatternToken.Type.PLACEHOLDER, key, known.displayName))
            } else {
                tokens.add(PatternToken(PatternToken.Type.SEPARATOR, "{$key}", "{$key}"))
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < pattern.length) {
            val separator = pattern.substring(lastIndex)
            if (separator.isNotBlank()) {
                tokens.add(PatternToken(PatternToken.Type.SEPARATOR, separator, separator))
            }
        }
        return tokens
    }

    private fun buildPatternString(): String = patternTokens.joinToString("") { it.toPatternString() }

    private fun currentPattern(): String {
        return prefs().getString(FolderPatternParser.KEY_FOLDER_PATTERN, FolderPatternParser.DEFAULT_PATTERN)
            ?: FolderPatternParser.DEFAULT_PATTERN
    }

    private fun prefs(): android.content.SharedPreferences {
        return requireContext().getSharedPreferences(FolderPatternParser.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun refreshPreview() {
        val pattern = buildPatternString()
        val (subDir, fileName) = FolderPatternParser.resolvePattern(pattern, sampleSong)
        val path = buildString {
            append("MusicDownloader")
            if (subDir.isNotBlank()) {
                append("/").append(subDir)
            }
            append("/").append(fileName).append(".mp3")
        }
        binding.tvPreview.text = path
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val CLIP_LABEL = "pattern_token"
    }
}
