package com.musicdownloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.databinding.FragmentLibraryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private lateinit var playerViewModel: PlayerViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playerViewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

        val tabs = listOf("Canciones", "Albumes", "Artistas", "Generos")
        val pager = binding.viewPager
        pager.isUserInputEnabled = true

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 4
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> SongListFragment()
                    1 -> CategoryListFragment().apply {
                        arguments = Bundle().apply { putString("category", "album") }
                    }
                    2 -> CategoryListFragment().apply {
                        arguments = Bundle().apply { putString("category", "artist") }
                    }
                    3 -> CategoryListFragment().apply {
                        arguments = Bundle().apply { putString("category", "genre") }
                    }
                    else -> SongListFragment()
                }
            }
        }

        TabLayoutMediator(binding.tabLayout, pager) { tab, position ->
            tab.text = tabs[position]
        }.attach()
    }

    override fun onResume() {
        super.onResume()
        val repo = MusicRepository(requireContext())
        lifecycleScope.launch(Dispatchers.IO) { repo.scanMusicFolder() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
