package com.beatohm.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkModule {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
