package com.beatohm

import android.app.Application
import com.beatohm.ads.TagWriteCounter
import com.beatohm.ads.InMobiManager

class BeatOhmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TagWriteCounter.init(this)
        InMobiManager.initialize(this)
    }
}
