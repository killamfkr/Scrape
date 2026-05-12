package com.scrape.iptv.parse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val MAX_BODY_CHARS = 600_000

/**
 * Fetches the single URL the user entered (e.g. M3U or a small HTML page) and returns the body as text.
 */
object CredentialSourceFetcher {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun fetchAsString(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "XtreamIptvClient/1.0")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} when fetching source URL")
            }
            val body = resp.body?.string().orEmpty()
            if (body.length > MAX_BODY_CHARS) {
                throw IOException("Downloaded page is too large to parse safely.")
            }
            body
        }
    }
}
