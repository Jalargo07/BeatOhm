package com.musicdownloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.musicdownloader.MainActivity
import com.musicdownloader.R
import com.musicdownloader.audio.EqualizerEffect
import com.musicdownloader.data.EqualizerRepository
import com.musicdownloader.model.EqualizerPreset
import java.util.UUID

class EqualizerBottomSheet : BottomSheetDialogFragment() {

    private lateinit var equalizerEffect: EqualizerEffect
    private lateinit var repository: EqualizerRepository
    private lateinit var seekBars: List<VerticalSeekBar>
    private lateinit var dbLabels: List<TextView>
    private lateinit var spinner: Spinner

    private var allPresets = mutableListOf<EqualizerPreset>()
    private var activePresetIndex = 0
    private var isCustomState = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_equalizer_modern, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as? MainActivity ?: return
        val service = activity.playbackService ?: return
        equalizerEffect = service.getEqualizerEffect()
        repository = EqualizerRepository(requireContext())

        spinner = view.findViewById(R.id.spinner_preset)
        seekBars = listOf(
            view.findViewById(R.id.seekbar_band_0),
            view.findViewById(R.id.seekbar_band_1),
            view.findViewById(R.id.seekbar_band_2),
            view.findViewById(R.id.seekbar_band_3),
            view.findViewById(R.id.seekbar_band_4)
        )
        dbLabels = listOf(
            view.findViewById(R.id.tv_band_0_db),
            view.findViewById(R.id.tv_band_1_db),
            view.findViewById(R.id.tv_band_2_db),
            view.findViewById(R.id.tv_band_3_db),
            view.findViewById(R.id.tv_band_4_db)
        )

        loadPresets()
        loadActivePreset()
        setupSeekBars()
        setupButtons(view)
    }

    private fun loadPresets() {
        allPresets.clear()
        allPresets.addAll(EqualizerPreset.builtinPresets())
        allPresets.addAll(repository.getCustomPresets())

        val names = allPresets.map { it.getName(requireContext()) }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (activePresetIndex == position && !isCustomState) return
                if (position < allPresets.size) {
                    applyPreset(allPresets[position])
                    activePresetIndex = position
                    isCustomState = false
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun loadActivePreset() {
        val savedGains = repository.getBandGains()
        equalizerEffect.setGains(savedGains)
        for (i in 0 until 5) {
            val gainMb = savedGains[i]
            val progress = gainToProgress(gainMb)
            seekBars[i].setProgressWithoutCallback(progress)
            dbLabels[i].text = getString(R.string.equalizer_db_value, gainMb / 100)
        }

        val matchedPreset = allPresets.find { it.gainsMb == savedGains }
        if (matchedPreset != null) {
            val idx = allPresets.indexOfFirst { it.id == matchedPreset.id }
            if (idx >= 0) {
                spinner.setSelection(idx)
                activePresetIndex = idx
                isCustomState = false
            }
        } else {
            isCustomState = true
        }
    }

    private fun applyPreset(preset: EqualizerPreset) {
        equalizerEffect.setGains(preset.gainsMb)
        for (i in 0 until 5) {
            val gainMb = preset.gainsMb[i]
            val progress = gainToProgress(gainMb)
            seekBars[i].setProgressWithoutCallback(progress)
            dbLabels[i].text = getString(R.string.equalizer_db_value, gainMb / 100)
        }
    }

    private fun setupSeekBars() {
        for (i in 0 until 5) {
            seekBars[i].onProgressChangedCallback = { gainMb ->
                equalizerEffect.setBandGain(i, gainMb)
                dbLabels[i].text = getString(R.string.equalizer_db_value, gainMb / 100)
                onBarChanged()
            }
        }
    }

    private fun onBarChanged() {
        if (!isCustomState) {
            isCustomState = true
        }
    }

    private fun setupButtons(view: View) {
        view.findViewById<MaterialButton>(R.id.btn_save).setOnClickListener {
            showSaveDialog()
        }
        view.findViewById<MaterialButton>(R.id.btn_reset).setOnClickListener {
            resetToFlat()
        }
    }

    private fun showSaveDialog() {
        val gains = equalizerEffect.getGains()
        val currentPreset = allPresets.getOrNull(activePresetIndex)

        if (currentPreset != null && currentPreset.isBuiltin && gains == currentPreset.gainsMb) {
            Toast.makeText(requireContext(), R.string.equalizer_no_changes, Toast.LENGTH_SHORT).show()
            return
        }

        if (currentPreset != null && !currentPreset.isBuiltin) {
            val updated = currentPreset.copy(gainsMb = gains)
            repository.saveCustomPreset(updated)
            repository.setActivePreset(updated.id)
            isCustomState = false
            loadPresets()
            val idx = allPresets.indexOfFirst { it.id == updated.id }
            if (idx >= 0) {
                spinner.setSelection(idx)
                activePresetIndex = idx
            }
            Toast.makeText(requireContext(), R.string.equalizer_preset_saved, Toast.LENGTH_SHORT).show()
            return
        }

        val input = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.equalizer_preset_name)
            setPadding(48, 32, 48, 16)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.equalizer_save_preset)
            .setView(input)
            .setPositiveButton(R.string.equalizer_save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(requireContext(), R.string.equalizer_preset_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val id = UUID.randomUUID().toString()
                val preset = EqualizerPreset(id, name, gains, false)
                repository.saveCustomPreset(preset)
                repository.setActivePreset(id)
                isCustomState = false
                loadPresets()
                val idx = allPresets.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    spinner.setSelection(idx)
                    activePresetIndex = idx
                }
                Toast.makeText(requireContext(), R.string.equalizer_preset_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun resetToFlat() {
        equalizerEffect.resetToFlat()
        for (i in 0 until 5) {
            seekBars[i].setProgressWithoutCallback(gainToProgress(0))
            dbLabels[i].text = getString(R.string.equalizer_db_value, 0)
        }
        isCustomState = false
        val flatIdx = allPresets.indexOfFirst { it.id == "flat" }
        if (flatIdx >= 0) {
            spinner.setSelection(flatIdx)
            activePresetIndex = flatIdx
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        val currentGains = equalizerEffect.getGains()
        repository.setBandGains(currentGains)
        if (!isCustomState) {
            val currentPreset = allPresets.getOrNull(activePresetIndex)
            if (currentPreset != null) {
                repository.setActivePreset(currentPreset.id)
            }
        }
    }

    private fun gainToProgress(gainMb: Int): Int {
        return ((gainMb + 1200).toFloat() / 2400f * 2000f).toInt().coerceIn(0, 2000)
    }

    companion object {
        const val TAG = "EqualizerBottomSheet"
    }
}
