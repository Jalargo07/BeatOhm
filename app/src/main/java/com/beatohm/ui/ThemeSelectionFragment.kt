package com.beatohm.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.beatohm.R
import com.beatohm.data.ThemeExporter
import com.beatohm.data.UserTheme
import com.beatohm.databinding.FragmentThemeSelectionBinding
import kotlinx.coroutines.launch

class ThemeSelectionFragment : Fragment() {

    private var _binding: FragmentThemeSelectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ThemeAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentThemeSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemeManager.init(requireContext())

        adapter = ThemeAdapter { theme -> selectTheme(theme) }
        binding.rvThemes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvThemes.adapter = adapter

        binding.fabNewTheme.setOnClickListener { showCreateDialog() }

        // Export/Import buttons
        binding.btnExport.setOnClickListener { exportSelectedTheme() }
        binding.btnImport.setOnClickListener { importThemeFromClipboard() }

        loadThemes()
    }

    private fun loadThemes() {
        viewLifecycleOwner.lifecycleScope.launch {
            val activeTheme = ThemeManager.activeTheme
            val allThemes = ThemeManager.getAllThemes()

            val items = mutableListOf<ThemeAdapter.ThemeItem>()

            // Split into presets and custom
            val presets = allThemes.filter { it.isPreset }
            val custom = allThemes.filter { !it.isPreset }

            // Presets
            presets.forEach { theme ->
                items.add(ThemeAdapter.ThemeItem(
                    theme = theme,
                    isPreset = true,
                    isActive = theme.id == activeTheme?.id
                ))
            }

            // Custom
            custom.forEach { theme ->
                items.add(ThemeAdapter.ThemeItem(
                    theme = theme,
                    isPreset = false,
                    isActive = theme.id == activeTheme?.id
                ))
            }

            adapter.submitList(items)

            // Show import/export bar when there are custom themes
            if (_binding != null) {
                binding.importExportBar.visibility =
                    if (custom.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun selectTheme(theme: UserTheme) {
        viewLifecycleOwner.lifecycleScope.launch {
            ThemeManager.setActiveTheme(theme)
            requireActivity().recreate()
        }
    }

    private fun exportSelectedTheme() {
        val theme = ThemeManager.activeTheme ?: return
        ThemeExporter.copyToClipboard(requireContext(), theme)
        ThemeExporter.shareTheme(requireContext(), theme)
        Toast.makeText(requireContext(), getString(R.string.tema_copiado), Toast.LENGTH_SHORT).show()
    }

    private fun importThemeFromClipboard() {
        val userTheme = ThemeExporter.pasteFromClipboard(requireContext())
        if (userTheme == null) {
            Toast.makeText(requireContext(), getString(R.string.no_tema_valido), Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.importar_tema))
            .setMessage(getString(R.string.importar_tema_pregunta, userTheme.name))
            .setPositiveButton(getString(R.string.importar)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    ThemeManager.createCustomTheme(userTheme)
                    ThemeManager.setActiveTheme(userTheme)
                    Toast.makeText(requireContext(), getString(R.string.tema_importado), Toast.LENGTH_SHORT).show()
                    loadThemes()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showCreateDialog() {
        val ctx = requireContext()
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_create_theme, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_theme_name)

        AlertDialog.Builder(ctx)
            .setTitle(ctx.getString(R.string.theme_create_title))
            .setView(dialogView)
            .setPositiveButton(ctx.getString(R.string.theme_create_confirm)) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(ctx, ctx.getString(R.string.theme_create_empty_name), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val currentTheme = ThemeManager.activeTheme
                val newTheme = UserTheme(
                    name = name,
                    primaryColor = currentTheme?.primaryColor ?: 0xFF9D35FF.toInt(),
                    secondaryColor = currentTheme?.secondaryColor ?: 0xFFFF304F.toInt(),
                    accentColor = currentTheme?.accentColor ?: 0xFF00E5FF.toInt(),
                    backgroundColor = currentTheme?.backgroundColor ?: 0xFF0B0910.toInt(),
                    surfaceColor = currentTheme?.surfaceColor ?: 0xFF12101A.toInt(),
                    textColor = currentTheme?.textColor ?: 0xFFFFFFFF.toInt(),
                    iconPackId = currentTheme?.iconPackId ?: "default",
                    playerLayoutId = currentTheme?.playerLayoutId ?: "classic",
                    fontStyle = currentTheme?.fontStyle ?: "default",
                    isPreset = false
                )

                viewLifecycleOwner.lifecycleScope.launch {
                    ThemeManager.createCustomTheme(newTheme)
                    loadThemes()
                    Toast.makeText(ctx, ctx.getString(R.string.theme_create_success), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(ctx.getString(R.string.theme_create_cancel), null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}