package com.beatohm.ui

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.beatohm.R
import com.beatohm.data.MusicRepository
import com.beatohm.data.toSong
import com.beatohm.databinding.FragmentSongListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class MostPlayedFragment : Fragment() {

    private enum class Period { ALL, THIS_MONTH }

    private var _binding: FragmentSongListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FilteredSongAdapter
    private lateinit var repository: MusicRepository
    private var selectedPeriod: Period = Period.ALL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSongListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = MusicRepository(requireContext())
        binding.tvListTitle.text = getString(R.string.most_played)
        binding.spinnerSort.visibility = View.GONE

        setupPeriodChips()

        adapter = FilteredSongAdapter(onItemClick = { song ->
            val activity = requireActivity() as? com.beatohm.MainActivity
            val service = activity?.playbackService
            if (service != null) {
                val vm = PlayerViewModel.getInstance(requireActivity().application as Application)
                val songs = adapter.currentList
                val index = songs.indexOf(song)
                vm.setPlaylist(songs.map { it.toSong() }, if (index >= 0) index else 0)
                service.playFile(song.filePath, isManual = true)
            }
        })
        binding.rvSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSongs.adapter = adapter

        loadSongs()
    }

    private fun setupPeriodChips() {
        val chipGroup = LayoutInflater.from(requireContext())
            .inflate(R.layout.chip_period_filter, binding.root as ViewGroup, false)
        val root = binding.root as ViewGroup
        root.addView(chipGroup, 2)

        chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chipPeriodAll)
            .setOnClickListener {
                selectedPeriod = Period.ALL
                loadSongs()
            }
        chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chipPeriodThisMonth)
            .setOnClickListener {
                selectedPeriod = Period.THIS_MONTH
                loadSongs()
            }
    }

    private fun updateEmptyText() {
        binding.tvEmpty.setText(R.string.empty_most_played)
    }

    private fun loadSongs() {
        updateEmptyText()
        val sinceTimestamp = when (selectedPeriod) {
            Period.ALL -> 0L
            Period.THIS_MONTH -> getFirstDayOfMonth()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getTopPlayedSongs(sinceTimestamp).collectLatest { songs ->
                adapter.submitList(songs)
                binding.llSongListEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun getFirstDayOfMonth(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
