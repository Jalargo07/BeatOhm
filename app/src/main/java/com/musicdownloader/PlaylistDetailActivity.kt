package com.musicdownloader

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musicdownloader.data.AppDatabase
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.data.PlaylistSong
import com.musicdownloader.data.toSong
import com.musicdownloader.ui.FilteredSongAdapter
import com.musicdownloader.ui.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlaylistDetailActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvTitle: TextView
    private lateinit var adapter: FilteredSongAdapter
    private val db by lazy { AppDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)

        tvTitle = findViewById(R.id.tv_playlist_title)
        recyclerView = findViewById(R.id.rv_playlist_songs)

        setSupportActionBar(findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar))
        supportActionBar?.title = playlistName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvTitle.text = playlistName

        adapter = FilteredSongAdapter(
            onItemClick = { song ->
                val vm = PlayerViewModel.getInstance(application)
                lifecycleScope.launch {
                    db.songDao().getSongsInPlaylist(playlistId).first().let { songs ->
                        val index = songs.indexOf(song)
                        vm.setPlaylist(songs.map { it.toSong() }, if (index >= 0) index else 0)
                    }
                    val intent = Intent(this@PlaylistDetailActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    startActivity(intent)
                }
            },
            onItemLongClick = { song ->
                AlertDialog.Builder(this)
                    .setTitle("Quitar de playlist")
                    .setMessage("¿Quitar ${song.title} de esta playlist?")
                    .setPositiveButton("Si") { _, _ ->
                        lifecycleScope.launch {
                            db.playlistDao().removeSongFromPlaylist(PlaylistSong(playlistId, song.filePath, 0))
                            Toast.makeText(this@PlaylistDetailActivity, "Canción quitada", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            db.songDao().getSongsInPlaylist(playlistId).collectLatest { songs ->
                adapter.submitList(songs)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        var playlistId: Long = 0
        var playlistName: String = ""
    }
}
