package com.beatohm.data

interface IWaveformRepository {
    suspend fun extractMissingWaveforms(
        songs: List<LocalSong>,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    )
    suspend fun resetWaveform(song: LocalSong)
    suspend fun updateWaveform(songId: String, json: String)
}
