package com.beatohm.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.beatohm.R
import com.beatohm.data.AppDatabase
import com.beatohm.data.MetadataCandidateRepository
import com.beatohm.data.MusicRepository
import com.beatohm.databinding.FragmentMetadataCandidatesBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * UI de "Canciones pendientes de elección" (T10).
 *
 * Muestra las canciones con candidatos de metadata ambiguos (PENDING) para
 * que el usuario elija el candidato correcto. Observa el Flow reactivo
 * `getPendingCandidates()` del [MetadataCandidateRepository]; cada registro
 * PENDING se agrupa en un header de canción (título/artista actual) + N
 * opciones candidatas (con su metadata, fuente, confianza y botón Aplicar).
 *
 * - "Aplicar" → [MetadataCandidateRepository.applyCandidate] (marca APPLIED,
 *   el Flow re-emite solo al cambiar el status).
 * - "Saltar" → [MetadataCandidateRepository.skipCandidate] (marca SKIPPED).
 *
 * Empty state visible cuando no hay registros PENDING.
 */
class MetadataCandidatesFragment : Fragment() {

    private var _binding: FragmentMetadataCandidatesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MetadataCandidatesAdapter
    private lateinit var candidateRepo: MetadataCandidateRepository
    private lateinit var musicRepository: MusicRepository
    private val _uiState = MutableStateFlow<UiState<List<MetadataCandidatesItem>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<MetadataCandidatesItem>>> = _uiState.asStateFlow()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMetadataCandidatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getInstance(requireContext())
        candidateRepo = MetadataCandidateRepository(db.metadataCandidateDao())
        musicRepository = MusicRepository(requireContext(), metadataCandidateRepo = candidateRepo)

        adapter = MetadataCandidatesAdapter(
            onApply = { entityId, selectedIndex -> applyCandidate(entityId, selectedIndex) },
            onSkip = { entityId -> skipCandidate(entityId) }
        )
        binding.rvCandidates.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCandidates.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            uiState.collectLatest { state ->
                UiStateHelper.bind(
                    state = state,
                    recyclerView = binding.rvCandidates,
                    emptyView = binding.llEmpty
                )
            }
        }

        observePendingCandidates()
    }

    private fun observePendingCandidates() {
        viewLifecycleOwner.lifecycleScope.launch {
            candidateRepo.getPendingCandidates().collectLatest { entities ->
                val items = buildList {
                    for (entity in entities) {
                        val song = musicRepository.getSongById(entity.songId) ?: continue
                        val candidates = candidateRepo.deserializeCandidates(entity.candidatesJson)
                        if (candidates.isEmpty()) continue
                        add(
                            MetadataCandidatesItem.SongHeader(
                                entityId = entity.id,
                                songId = song.id,
                                title = song.title,
                                artist = song.artist,
                                thumbnailUrl = song.thumbnailUrl,
                                filePath = song.filePath
                            )
                        )
                        candidates.forEachIndexed { index, candidate ->
                            add(
                                MetadataCandidatesItem.CandidateOption(
                                    entityId = entity.id,
                                    candidate = candidate,
                                    index = index
                                )
                            )
                        }
                    }
                }
                adapter.submitList(items)
                _uiState.value = if (items.isEmpty()) UiState.Empty(R.string.metadata_candidates_empty) else UiState.Content(items)
            }
        }
    }

    private fun applyCandidate(entityId: Long, selectedIndex: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            // P2.4: Use MusicRepository.applyCandidateWithFinalize for unified
            // rename + tags + DB migration + mark APPLIED
            musicRepository.applyCandidateWithFinalize(entityId, selectedIndex)
            Toast.makeText(requireContext(), R.string.metadata_candidates_applied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun skipCandidate(entityId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            candidateRepo.skipCandidate(entityId)
            Toast.makeText(requireContext(), R.string.metadata_candidates_skipped, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
