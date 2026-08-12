package com.beatohm.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.beatohm.BuildConfig
import com.beatohm.R
import com.beatohm.databinding.FragmentSettingsBinding
import com.beatohm.model.PatternToken
import com.beatohm.model.Song
import com.beatohm.ui.player.PlayerLayoutManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.beatohm.util.FolderPatternParser

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

        initPatternBuilder()
        initPlayerSettings()
        initActionButtons()
        setupAppearance()
        setupLanguageChips()
    }

    private fun initPatternBuilder() {
        setupTokenBank()
        setupPatternBuilder()
        refreshFromBuilder()
    }

    private fun initPlayerSettings() {
        setupPlayerLayoutChips()
        setupIconPackChips()
        setupWaveAnimationToggle()
    }

    private fun initActionButtons() {
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

        binding.btnResetTutorial.setOnClickListener {
            TutorialManager.resetAll(requireContext())
            Toast.makeText(requireContext(), R.string.reset_tutorial, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAppearance() {
        binding.btnSelectTheme.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_themeSelector)
        }
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

    private fun setupLanguageChips() {
        val current = AppCompatDelegate.getApplicationLocales()
        val currentLang = if (current.isEmpty) "es" else current[0]?.language ?: "es"
        when (currentLang) {
            "en" -> binding.chipLangEn.isChecked = true
            "pt" -> binding.chipLangPt.isChecked = true
            else -> binding.chipLangEs.isChecked = true
        }

        binding.chipGroupLanguage.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val locale = when (checkedIds.first()) {
                R.id.chip_lang_en -> "en"
                R.id.chip_lang_pt -> "pt"
                else -> "es"
            }
            val appLocales = LocaleListCompat.forLanguageTags(locale)
            AppCompatDelegate.setApplicationLocales(appLocales)
        }
    }

    private fun setupTokenBank() {
        tokenBankAdapter = TokenBankAdapter { token ->
            patternTokens.add(token)
            refreshFromBuilder()
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

    private fun setupPlayerLayoutChips() {
        val chipGroup = binding.chipGroupPlayerLayout
        when (ThemeManager.currentPlayerLayout) {
            "compact" -> chipGroup.check(R.id.chip_layout_compact)
            "vinyl" -> chipGroup.check(R.id.chip_layout_vinyl)
            else -> chipGroup.check(R.id.chip_layout_classic)
        }
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val layoutId = when (checkedIds.first()) {
                R.id.chip_layout_compact -> "compact"
                R.id.chip_layout_vinyl -> "vinyl"
                else -> "classic"
            }
            val current = ThemeManager.activeTheme ?: return@setOnCheckedStateChangeListener
            val updated = current.copy(playerLayoutId = layoutId)
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                ThemeManager.updateTheme(updated)
                ThemeManager.setActiveThemeBlocking(updated)
            }
            PlayerLayoutManager.currentStyle = layoutId
        }
    }

    private fun setupIconPackChips() {
        val chipGroup = binding.chipGroupIconPack
        when (ThemeManager.currentIconPack) {
            "darknova" -> chipGroup.check(R.id.chip_icon_bold)
            "heroic" -> chipGroup.check(R.id.chip_icon_heroic)
            else -> chipGroup.check(R.id.chip_icon_material)
        }
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val packId = when (checkedIds.first()) {
                R.id.chip_icon_bold -> "darknova"
                R.id.chip_icon_heroic -> "heroic"
                else -> "default"
            }
            val current = ThemeManager.activeTheme ?: return@setOnCheckedStateChangeListener
            val updated = current.copy(iconPackId = packId)
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                ThemeManager.updateTheme(updated)
                ThemeManager.setActiveThemeBlocking(updated)
                withContext(Dispatchers.Main) {
                    if (isAdded) requireActivity().recreate()
                }
            }
        }
    }

    private fun setupWaveAnimationToggle() {
        val switchWave = binding.switchWaveAnimation
        switchWave.isChecked = prefs().getBoolean("show_wave_animation", true)
        switchWave.setOnCheckedChangeListener { _, isChecked ->
            prefs().edit().putBoolean("show_wave_animation", isChecked).apply()
        }
    }

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
}