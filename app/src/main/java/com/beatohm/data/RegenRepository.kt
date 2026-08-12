package com.beatohm.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.Flow

class RegenRepository(private val context: Context) : IRegenRepository {
    private val regenStatusDao = AppDatabase.getInstance(context).regenStatusDao()

    companion object {
        private val _regenProgressStatic = MutableLiveData<Pair<Int, Int>?>()
        val regenProgressStatic: LiveData<Pair<Int, Int>?> = _regenProgressStatic
    }

    override val regenProgress: LiveData<Pair<Int, Int>?> get() = _regenProgressStatic

    override fun startRegenProgress(total: Int) { _regenProgressStatic.postValue(0 to total) }
    override fun updateRegenProgress(done: Int, total: Int) { _regenProgressStatic.postValue(done to total) }
    override fun finishRegenProgress() { _regenProgressStatic.postValue(null) }

    override suspend fun markPending(songIds: List<String>) {
        regenStatusDao.insertAll(songIds.map { RegenStatus(it, "pending") })
    }
    override suspend fun markSuccess(songId: String) { regenStatusDao.delete(songId) }
    override suspend fun markFailed(songId: String) { regenStatusDao.insert(RegenStatus(songId, "failed")) }
    override fun getFailedSongs(): Flow<List<RegenStatus>> = regenStatusDao.getFailed()
    override fun getPendingAndFailedSongs(): Flow<List<RegenStatus>> = regenStatusDao.getPendingAndFailed()
    override suspend fun getFailedSongsNow(): List<RegenStatus> = regenStatusDao.getFailedNow()
    override suspend fun getPendingAndFailedCount(): Int = regenStatusDao.getPendingAndFailedCount()
    override suspend fun getPendingAndFailedSongsNow(): List<RegenStatus> = regenStatusDao.getPendingAndFailedNow()
    override suspend fun clearRegenStatus() = regenStatusDao.clearAll()
    override suspend fun getFailedCount(): Int = regenStatusDao.getFailedCount()
}
