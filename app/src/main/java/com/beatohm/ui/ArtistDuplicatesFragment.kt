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
import com.beatohm.data.ArtistSimilarityDetector
import com.beatohm.data.ArtistSimilarityDetector.DuplicatePair
import com.beatohm.databinding.FragmentArtistDuplicatesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment que muestra pares de artistas duplicados detectados por
 * [ArtistSimilarityDetector] y permite al usuario unirlos o descartarlos.
 *
 * - Ejecuta la detección en background (Dispatchers.IO).
 * - Muestra lista de pares con botones "Unir" / "No son iguales".
 * - Al unir, actualiza `songs.artist` y refresca la lista.
 * - Empty state cuando no hay más pares.
 */
class ArtistDuplicatesFragment : Fragment() {

    private var _binding: FragmentArtistDuplicatesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ArtistDuplicatesAdapter
    private lateinit var db: AppDatabase

    private val allPairs = mutableListOf<DuplicatePair>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArtistDuplicatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getInstance(requireContext())

        adapter = ArtistDuplicatesAdapter(
            onMerge = { pair, winner -> mergeArtists(pair, winner) },
            onDismiss = { pair -> dismissPair(pair) }
        )
        binding.rvDuplicates.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDuplicates.adapter = adapter

        loadDuplicates()
    }

    private fun loadDuplicates() {
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val pairs = withContext(Dispatchers.IO) {
                ArtistSimilarityDetector.findDuplicatePairs(db)
            }
            allPairs.clear()
            allPairs.addAll(pairs)
            submitList()
            showLoading(false)
        }
    }

    private fun submitList() {
        adapter.clearSelections()
        adapter.submitList(allPairs.toList())
        binding.llEmpty.visibility = if (allPairs.isEmpty()) View.VISIBLE else View.GONE
        binding.rvDuplicates.visibility = if (allPairs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun mergeArtists(pair: DuplicatePair, winner: String) {
        val loser = if (winner == pair.artist1) pair.artist2 else pair.artist1
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ArtistSimilarityDetector.mergeArtists(db, loser, winner)
            }
            allPairs.remove(pair)
            submitList()
            Toast.makeText(
                requireContext(),
                getString(R.string.artist_duplicates_merged, loser, winner),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun dismissPair(pair: DuplicatePair) {
        allPairs.remove(pair)
        submitList()
    }

    private fun showLoading(loading: Boolean) {
        binding.progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.tvLoading.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            binding.llEmpty.visibility = View.GONE
            binding.rvDuplicates.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
