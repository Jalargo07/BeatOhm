package com.beatohm.data

import android.content.Context
import android.database.CursorWindow
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.beatohm.importer.ImportSession
import com.beatohm.importer.ImportSessionDao
import com.beatohm.importer.ImportTrackStatus
import com.beatohm.importer.ImportTrackStatusDao
import java.lang.reflect.Field

@Database(
    entities = [
        LocalSong::class,
        Playlist::class,
        PlaylistSong::class,
        UserTheme::class,
        RegenStatus::class,
        ImportSession::class,
        ImportTrackStatus::class,
        PlaybackEvent::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun themeDao(): ThemeDao
    abstract fun regenStatusDao(): RegenStatusDao
    abstract fun importSessionDao(): ImportSessionDao
    abstract fun importTrackStatusDao(): ImportTrackStatusDao
    abstract fun playbackEventDao(): PlaybackEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN waveformData TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_themes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        primaryColor INTEGER NOT NULL,
                        secondaryColor INTEGER NOT NULL,
                        accentColor INTEGER NOT NULL,
                        backgroundColor INTEGER NOT NULL,
                        surfaceColor INTEGER NOT NULL,
                        textColor INTEGER NOT NULL,
                        iconPackId TEXT NOT NULL DEFAULT 'default',
                        playerLayoutId TEXT NOT NULL DEFAULT 'classic',
                        fontStyle TEXT NOT NULL DEFAULT 'default',
                        isPreset INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN dominantColor INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS regen_status (songId TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS import_sessions (
                        sessionId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        playlistUrl TEXT NOT NULL,
                        platform TEXT NOT NULL,
                        playlistName TEXT NOT NULL DEFAULT '',
                        totalTracks INTEGER NOT NULL DEFAULT 0,
                        completedTracks INTEGER NOT NULL DEFAULT 0,
                        failedTracks INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS import_track_status (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        durationSec INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        youtubeUrl TEXT,
                        localPath TEXT,
                        errorMessage TEXT,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (sessionId) REFERENCES import_sessions(sessionId) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS index_import_track_status_sessionId ON import_track_status(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_import_track_status_sessionId_status ON import_track_status(sessionId, status)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playback_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        songId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        score INTEGER NOT NULL,
                        FOREIGN KEY (songId) REFERENCES songs(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_events_songId ON playback_events(songId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_events_timestamp ON playback_events(timestamp)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_downloader_db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try {
                                val field: Field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
                                field.isAccessible = true
                                field.set(null, 10 * 1024 * 1024) // 10MB
                            } catch (_: Exception) {}
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }
    }
}
