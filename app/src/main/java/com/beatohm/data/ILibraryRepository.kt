package com.beatohm.data

import java.io.File
import kotlinx.coroutines.flow.Flow

interface ILibraryRepository {
    fun getLibraryFolders(): List<String>
    fun getSongsInFolder(folderPath: String): Flow<List<LocalSong>>
    fun addLibraryFolder(path: String)
    fun removeLibraryFolder(path: String)
    suspend fun deleteSongsInFolder(folderPath: String)
    fun getAlbumCoverOverride(album: String): String?
    fun setAlbumCoverOverride(album: String, coverPath: String)
    fun getMusicDir(): File
    fun getAlbumArtCacheDir(): File
    fun downloadArtwork(url: String, dest: File)
    suspend fun scanLibrary(): LibraryRepository.ScanResult
    suspend fun fastScan(): LibraryRepository.ScanResult
}
