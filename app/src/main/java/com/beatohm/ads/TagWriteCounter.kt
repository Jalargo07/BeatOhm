package com.beatohm.ads

import android.content.Context
import android.content.SharedPreferences

object TagWriteCounter {
    private const val PREFS_NAME = "tag_write_counter"
    private const val KEY_COUNT = "songs_written_count"
    const val MAX_FREE_WRITES = 100

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getCount(): Int = prefs.getInt(KEY_COUNT, 0)

    fun increment(): Int {
        val newCount = getCount() + 1
        prefs.edit().putInt(KEY_COUNT, newCount).apply()
        return newCount
    }

    fun reset() {
        prefs.edit().putInt(KEY_COUNT, 0).apply()
    }

    fun hasReachedLimit(): Boolean = getCount() >= MAX_FREE_WRITES
}
