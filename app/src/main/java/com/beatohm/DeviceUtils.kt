package com.beatohm

import android.app.ActivityManager
import android.content.Context

object DeviceUtils {
    /**
     * Returns optimal thread count based on device RAM.
     * - ≤3GB RAM → 2 threads (eMMC 5.1, low-end)
     * - 4-6GB RAM → 3 threads (UFS 2.x, mid-range)
     * - >6GB RAM → 4 threads (UFS 3.1+, flagship)
     */
    fun getOptimalThreadCount(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamGB = memoryInfo.totalMem / (1024 * 1024 * 1024)
        return when {
            totalRamGB <= 3 -> 2
            totalRamGB <= 6 -> 3
            else -> 4
        }
    }
}
