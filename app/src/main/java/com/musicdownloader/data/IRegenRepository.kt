package com.musicdownloader.data

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow

interface IRegenRepository {
    val regenProgress: LiveData<Pair<Int, Int>?>
    fun startRegenProgress(total: Int)
    fun updateRegenProgress(done: Int, total: Int)
    fun finishRegenProgress()
    suspend fun markPending(songIds: List<String>)
    suspend fun markSuccess(songId: String)
    suspend fun markFailed(songId: String)
    fun getFailedSongs(): Flow<List<RegenStatus>>
    fun getPendingAndFailedSongs(): Flow<List<RegenStatus>>
    suspend fun getFailedSongsNow(): List<RegenStatus>
    suspend fun getPendingAndFailedCount(): Int
    suspend fun getPendingAndFailedSongsNow(): List<RegenStatus>
    suspend fun clearRegenStatus()
    suspend fun getFailedCount(): Int
}
