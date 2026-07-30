package com.musicdownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicdownloader.databinding.ActivityMainBinding
import com.musicdownloader.ui.DownloadAdapter
import com.musicdownloader.ui.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: DownloadAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        adapter = DownloadAdapter()

        setupUI()
        setupObservers()
        checkPermissions()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        binding.rvDownloads.layoutManager = LinearLayoutManager(this)
        binding.rvDownloads.adapter = adapter

        binding.etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                startDownload()
                true
            } else false
        }

        binding.btnDownload.setOnClickListener { startDownload() }
    }

    private fun setupObservers() {
        viewModel.downloads.observe(this) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun startDownload() {
        val url = binding.etUrl.text.toString().trim()
        if (url.isBlank()) {
            binding.etUrl.error = "Ingresa una URL"
            return
        }

        if (!isValidYouTubeUrl(url)) {
            binding.etUrl.error = "URL de YouTube inválida"
            return
        }

        viewModel.startDownload(url)
        binding.etUrl.text?.clear()
    }

    private fun isValidYouTubeUrl(url: String): Boolean {
        return url.matches(Regex(
            "(https?://)?(www\\.|m\\.)?(youtube\\.com|youtu\\.be|music\\.youtube\\.com)/.*"
        ))
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                )
            }
        }
    }
}
