package com.musicdownloader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.widget.ImageView
import com.musicdownloader.R
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

object ArtworkLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(3)
    private var cacheDir: File? = null

    fun init(cacheDir: File) {
        this.cacheDir = cacheDir
    }

    fun loadArtFromAudioFile(imageView: ImageView, filePath: String) {
        imageView.setImageResource(R.drawable.ic_player)
        imageView.tag = filePath
        scope.launch {
            semaphore.acquire()
            try {
                val bitmap = loadBitmap(filePath)
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        if (imageView.tag == filePath) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } finally {
                semaphore.release()
            }
        }
    }

    private suspend fun loadBitmap(filePath: String): Bitmap? = withContext(Dispatchers.IO) {
        val dir = cacheDir ?: return@withContext null
        val cacheFile = File(dir, "${File(filePath).nameWithoutExtension}.jpg")
        if (cacheFile.exists()) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return@withContext it }
        }
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val art = retriever.embeddedPicture
            retriever.release()
            if (art == null) return@withContext null
            val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size) ?: return@withContext null
            try {
                cacheFile.parentFile?.mkdirs()
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            } catch (_: Exception) {}
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
