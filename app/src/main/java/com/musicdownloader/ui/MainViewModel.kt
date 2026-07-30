package com.musicdownloader.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.musicdownloader.DownloadService
import com.musicdownloader.model.DownloadState
import com.musicdownloader.model.DownloadStatus
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _downloads = MutableLiveData<List<DownloadState>>(emptyList())
    val downloads: LiveData<List<DownloadState>> = _downloads

    private val _isDownloading = MutableLiveData(false)
    val isDownloading: LiveData<Boolean> = _isDownloading

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DownloadService.BROADCAST_UPDATE) {
                val id = intent.getStringExtra(DownloadService.EXTRA_ID) ?: return
                val status = intent.getStringExtra(DownloadService.EXTRA_STATE)
                    ?.let { DownloadStatus.valueOf(it) }
                val progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0)
                val message = intent.getStringExtra(DownloadService.EXTRA_MESSAGE) ?: ""

                val currentList = _downloads.value?.toMutableList() ?: mutableListOf()
                val index = currentList.indexOfFirst { it.id == id }

                val updatedState = if (index >= 0) {
                    currentList[index].copy(
                        status = status ?: currentList[index].status,
                        progress = progress,
                        errorMessage = message
                    )
                } else {
                    DownloadState(id = id, status = status ?: DownloadStatus.QUEUED, progress = progress, errorMessage = message)
                }

                if (index >= 0) {
                    currentList[index] = updatedState
                } else {
                    currentList.add(0, updatedState)
                }

                _downloads.value = currentList

                val hasActiveDownloads = currentList.any {
                    it.status == DownloadStatus.QUEUED ||
                    it.status == DownloadStatus.EXTRACTING ||
                    it.status == DownloadStatus.FETCHING_METADATA ||
                    it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.TAGGING
                }
                _isDownloading.value = hasActiveDownloads
            }
        }
    }

    init {
        getApplication<Application>().registerReceiver(
            receiver,
            IntentFilter(DownloadService.BROADCAST_UPDATE)
        )
    }

    fun startDownload(url: String) {
        val downloadId = UUID.randomUUID().toString()
        val intent = Intent(getApplication(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_DOWNLOAD
            putExtra(DownloadService.EXTRA_URL, url)
            putExtra(DownloadService.EXTRA_ID, downloadId)
        }
        getApplication<Application>().startService(intent)

        val newDownload = DownloadState(
            id = downloadId,
            url = url,
            status = DownloadStatus.QUEUED
        )
        val currentList = _downloads.value?.toMutableList() ?: mutableListOf()
        currentList.add(0, newDownload)
        _downloads.value = currentList
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(receiver)
    }
}
