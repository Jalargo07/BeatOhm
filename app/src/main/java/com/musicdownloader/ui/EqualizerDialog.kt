package com.musicdownloader.ui

import android.content.Context
import android.media.audiofx.Equalizer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.musicdownloader.R

class EqualizerDialog(private val context: Context, private val audioSessionId: Int) {

    fun show() {
        if (audioSessionId <= 0) {
            Toast.makeText(context, R.string.equalizer_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        val equalizer = try {
            Equalizer(0, audioSessionId)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.equalizer_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val inflater = android.view.LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_equalizer, null)
        val container = view.findViewById<LinearLayout>(R.id.equalizer_container)

        val range = equalizer.bandLevelRange
        val minLevel = range[0]
        val maxLevel = range[1]
        val bandCount = equalizer.numberOfBands

        for (i in 0 until bandCount) {
            val saved = prefs.getInt(PREFS_KEY_BAND_PREFIX + i, equalizer.getBandLevel(i.toShort()).toInt())
            equalizer.setBandLevel(i.toShort(), saved.toShort())

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val label = TextView(context).apply {
                text = "${equalizer.getCenterFreq(i.toShort()) / 1000} Hz"
                setTextColor(context.getColor(R.color.on_surface))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val seek = SeekBar(context).apply {
                max = maxLevel - minLevel
                progress = saved - minLevel
                setPadding(12, 0, 12, 0)
                progressTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.primary))
                thumbTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.primary))
            }

            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val level = progress + minLevel
                        equalizer.setBandLevel(i.toShort(), level.toShort())
                        prefs.edit().putInt(PREFS_KEY_BAND_PREFIX + i, level).apply()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            row.addView(label)
            row.addView(seek)
            container.addView(row)
        }

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()
        view.findViewById<android.widget.Button>(R.id.btn_equalizer_close).setOnClickListener {
            dialog.dismiss()
        }
        dialog.setOnDismissListener { equalizer.release() }
        dialog.show()
    }

    companion object {
        private const val PREFS_NAME = "player_prefs"
        private const val PREFS_KEY_BAND_PREFIX = "eq_band_"
    }
}
