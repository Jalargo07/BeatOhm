package com.beatohm.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkModule {

    val client: OkHttpClient by lazy {
        newClient(connectTimeoutSec = 10, readTimeoutSec = 10)
    }

    fun newClient(
        connectTimeoutSec: Long = 10,
        readTimeoutSec: Long = 30,
        writeTimeoutSec: Long = 30,
        followRedirects: Boolean = true
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
        .writeTimeout(writeTimeoutSec, TimeUnit.SECONDS)
        .followRedirects(followRedirects)
        .followSslRedirects(followRedirects)
        .build()
}
