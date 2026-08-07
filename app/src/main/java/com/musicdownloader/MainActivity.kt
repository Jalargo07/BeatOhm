package com.musicdownloader

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import coil.load
import com.google.android.material.imageview.ShapeableImageView
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.databinding.ActivityMainBinding
import com.musicdownloader.model.Song
import com.musicdownloader.ui.ArtworkLoader
import com.musicdownloader.ui.MainViewModel
import com.musicdownloader.ui.PlayerViewModel
import java.io.File

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    var playbackService: MusicPlaybackService? = null
        private set
    private lateinit var navController: NavController
    private lateinit var playerViewModel: PlayerViewModel
    private var miniPlayerVisible = false
    private val miniHandler = Handler(Looper.getMainLooper())
    private val miniProgressUpdate: Runnable = object : Runnable {
        override fun run() {
            updateMiniProgress()
            miniHandler.postDelayed(this, 500)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            playbackService = (service as MusicPlaybackService.LocalBinder).getService()
            playbackService?.setViewModel(PlayerViewModel.getInstance(application))
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ArtworkLoader.init(MusicRepository(this).getAlbumArtCacheDir())

        ViewModelProvider(this)[MainViewModel::class.java]

        playerViewModel = PlayerViewModel.getInstance(application)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.playerFragment, R.id.libraryFragment, R.id.downloadsFragment)
        )
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.toolbar.visibility =
                if (destination.id == R.id.playerFragment) View.GONE else View.VISIBLE
        }

        setupMiniPlayer()

        checkPermissions()
        bindPlaybackService()
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()

    private fun setupMiniPlayer() {
        findViewById<View>(R.id.mini_player_container).setOnClickListener {
            navController.navigate(R.id.playerFragment)
        }

        findViewById<ImageButton>(R.id.btn_mini_play_pause).setOnClickListener {
            val service = playbackService ?: return@setOnClickListener
            if (service.isPlaying()) service.pause() else service.play()
        }

        playerViewModel.currentSong.observe(this) { song ->
            if (song == null) {
                hideMiniPlayer()
            } else {
                updateMiniPlayer(song)
                if (navController.currentDestination?.id != R.id.playerFragment) {
                    showMiniPlayer()
                }
            }
        }

        playerViewModel.isPlaying.observe(this) { playing ->
            findViewById<ImageButton>(R.id.btn_mini_play_pause).setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
            if (playing) {
                miniHandler.post(miniProgressUpdate)
            } else {
                miniHandler.removeCallbacks(miniProgressUpdate)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.playerFragment) {
                hideMiniPlayer()
            } else if (playerViewModel.currentSong.value != null) {
                showMiniPlayer()
            }
        }
    }

    private fun showMiniPlayer() {
        if (miniPlayerVisible) return
        miniPlayerVisible = true
        val container = findViewById<View>(R.id.mini_player_container)
        container.visibility = View.VISIBLE
        container.alpha = 0f
        val offset = 48 * resources.displayMetrics.density
        container.translationY = offset
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun hideMiniPlayer() {
        if (!miniPlayerVisible) return
        miniPlayerVisible = false
        miniHandler.removeCallbacks(miniProgressUpdate)
        val offset = 48 * resources.displayMetrics.density
        val container = findViewById<View>(R.id.mini_player_container)
        container.animate()
            .alpha(0f)
            .translationY(offset)
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (!miniPlayerVisible) container.visibility = View.GONE
            }
            .start()
    }

    private fun updateMiniPlayer(song: Song) {
        findViewById<TextView>(R.id.tv_mini_title).text = song.title
        findViewById<TextView>(R.id.tv_mini_artist).text = song.artist.ifBlank { getString(R.string.unknown_artist) }

        val cover = findViewById<ShapeableImageView>(R.id.iv_mini_cover)
        cover.alpha = 0.4f
        val path = song.filePath.ifBlank { song.youtubeUrl }
        if (song.thumbnailUrl.isNotBlank()) {
            cover.tag = null
            cover.load(song.thumbnailUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_player)
                error(R.drawable.ic_player)
            }
        } else if (path.isNotBlank() && File(path).exists()) {
            cover.tag = path
            ArtworkLoader.loadArtFromAudioFile(cover, path)
        } else {
            cover.tag = null
            cover.setImageResource(R.drawable.ic_player)
        }
        cover.animate().alpha(1f).setDuration(200).start()
    }

    private fun updateMiniProgress() {
        val service = playbackService ?: return
        val pos = service.getCurrentPosition()
        val dur = service.getDuration()
        if (dur > 0) {
            findViewById<ProgressBar>(R.id.mini_progress_bar).progress = (pos * 1000 / dur).toInt()
        }
    }

    private fun bindPlaybackService() {
        val intent = Intent(this, MusicPlaybackService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun getMusicDir(): java.io.File {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val target = java.io.File(dir, "MusicDownloader")
        if (!target.exists()) target.mkdirs()
        return target
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    override fun onDestroy() {
        try { unbindService(serviceConnection) } catch (_: Exception) {}
        super.onDestroy()
    }
}
