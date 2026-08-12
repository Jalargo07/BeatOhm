package com.beatohm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.beatohm.ui.IconPackManager
import com.beatohm.ui.PlayerViewModel
import com.beatohm.ui.ThemeManager

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) { super.onEnabled(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context) }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds) }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    companion object {
        private const val TAG = "MusicWidget"
        // Umbral para distinguir 4x1 (1 celda ≈ 110-130dp) de 4x2 (2 celdas ≈ 220-260dp)
        private const val HEIGHT_THRESHOLD_4X2 = 150

        fun updateAllWidgets(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, MusicWidgetProvider::class.java))
                for (id in ids) updateWidget(context, mgr, id)
            } catch (e: Exception) { Log.e(TAG, "updateAllWidgets failed", e) }
        }

        private fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_player)

                // Determinar tamaño: 1 celda ≈ 110-130dp, 2 celdas ≈ 220-260dp
                val is4x2 = try {
                    val opts = mgr.getAppWidgetOptions(widgetId)
                    opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) > HEIGHT_THRESHOLD_4X2
                } catch (_: Exception) { true }

                views.setViewVisibility(R.id.widget_4x2, if (is4x2) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widget_4x1, if (is4x2) View.GONE else View.VISIBLE)

                // Datos de la canción
                val vm = PlayerViewModel.getInstance(context.applicationContext as android.app.Application)
                val song = vm.currentSong.value
                val playing = vm.isPlaying.value ?: false
                val position = vm.currentPosition.value ?: 0L
                val duration = vm.duration.value ?: 0L

                if (song != null) {
                    // Info común
                    val title = song.title
                    val artist = song.artist.ifBlank { "Desconocido" }

                    views.setTextViewText(R.id.widget_title, title)
                    views.setTextViewText(R.id.widget_artist, artist)
                    views.setTextViewText(R.id.widget_title_small, title)
                    views.setTextViewText(R.id.widget_artist_small, artist)

                    // Play/Pause icons
                    val miniIcons = IconPackManager.getMiniPlayerIconResIds(ThemeManager.currentIconPack)
                    val icon = if (playing) miniIcons[IconPackManager.ICON_PAUSE] ?: R.drawable.ic_pause
                    else miniIcons[IconPackManager.ICON_PLAY] ?: R.drawable.ic_play
                    views.setImageViewResource(R.id.widget_play_pause, icon)
                    views.setImageViewResource(R.id.widget_play_pause_small, icon)

                    // Barra de progreso
                    if (duration > 0) {
                        val progress = ((position * 1000) / duration).toInt()
                        views.setInt(R.id.widget_progress, "setProgress", progress)
                        views.setInt(R.id.widget_progress_small, "setProgress", progress)
                        views.setTextViewText(R.id.widget_current_time, formatTime(position))
                        views.setTextViewText(R.id.widget_total_time, formatTime(duration))
                        views.setTextViewText(R.id.widget_current_time_small, formatTime(position))
                        views.setTextViewText(R.id.widget_total_time_small, formatTime(duration))
                    }

                    // Carátula
                    val artworkFile = java.io.File(song.thumbnailUrl)
                    if (artworkFile.exists()) {
                        val bmp = android.graphics.BitmapFactory.decodeFile(artworkFile.absolutePath)
                        if (bmp != null) {
                            views.setImageViewBitmap(R.id.widget_cover, bmp)
                            views.setImageViewBitmap(R.id.widget_cover_small, bmp)
                        } else {
                            views.setImageViewResource(R.id.widget_cover, R.drawable.ic_player)
                            views.setImageViewResource(R.id.widget_cover_small, R.drawable.ic_player)
                        }
                    } else {
                        views.setImageViewResource(R.id.widget_cover, R.drawable.ic_player)
                        views.setImageViewResource(R.id.widget_cover_small, R.drawable.ic_player)
                    }
                } else {
                    views.setTextViewText(R.id.widget_title, "Sin canción")
                    views.setTextViewText(R.id.widget_artist, "—")
                    views.setTextViewText(R.id.widget_title_small, "Sin canción")
                    views.setTextViewText(R.id.widget_artist_small, "—")
                    val miniIcons = IconPackManager.getMiniPlayerIconResIds(ThemeManager.currentIconPack)
                    views.setImageViewResource(R.id.widget_play_pause, miniIcons[IconPackManager.ICON_PLAY] ?: R.drawable.ic_play)
                    views.setImageViewResource(R.id.widget_play_pause_small, miniIcons[IconPackManager.ICON_PLAY] ?: R.drawable.ic_play)
                    views.setImageViewResource(R.id.widget_cover, R.drawable.ic_player)
                    views.setImageViewResource(R.id.widget_cover_small, R.drawable.ic_player)
                }

                // Click intents - Abrir app
                val mainIntent = Intent(context, MainActivity::class.java)
                val mainPending = PendingIntent.getActivity(context, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_cover, mainPending)
                views.setOnClickPendingIntent(R.id.widget_cover_small, mainPending)
                views.setOnClickPendingIntent(R.id.widget_title, mainPending)
                views.setOnClickPendingIntent(R.id.widget_artist, mainPending)
                views.setOnClickPendingIntent(R.id.widget_title_small, mainPending)
                views.setOnClickPendingIntent(R.id.widget_artist_small, mainPending)

                // Play/Pause
                val ppIntent = Intent(context, MusicPlaybackService::class.java).apply { action = MusicPlaybackService.ACTION_PLAY_PAUSE }
                val ppPending = PendingIntent.getService(context, 1, ppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_play_pause, ppPending)
                views.setOnClickPendingIntent(R.id.widget_play_pause_small, ppPending)

                // Previous
                val prevIntent = Intent(context, MusicPlaybackService::class.java).apply { action = MusicPlaybackService.ACTION_PREV }
                val prevPending = PendingIntent.getService(context, 2, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_prev, prevPending)
                views.setOnClickPendingIntent(R.id.widget_prev_small, prevPending)

                // Next
                val nextIntent = Intent(context, MusicPlaybackService::class.java).apply { action = MusicPlaybackService.ACTION_NEXT }
                val nextPending = PendingIntent.getService(context, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_next, nextPending)
                views.setOnClickPendingIntent(R.id.widget_next_small, nextPending)

                mgr.updateAppWidget(widgetId, views)
            } catch (e: Exception) { Log.e(TAG, "updateWidget FAILED", e) }
        }

        private fun formatTime(ms: Long): String {
            val totalSec = ms / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }
    }
}
