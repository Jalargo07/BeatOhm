package com.musicdownloader.network

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val TAG = "NetworkModule"

    val client: OkHttpClient by lazy {
        try {
            val bootstrapClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val dnsOverHttps = DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1")
                )
                .includeIPv6(false)
                .build()

            Log.e(TAG, "DoH configurado correctamente con Cloudflare")
            bootstrapClient.newBuilder()
                .dns(dnsOverHttps)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "DoH fallo, usando DNS del sistema: ${e.message}")
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }
}
