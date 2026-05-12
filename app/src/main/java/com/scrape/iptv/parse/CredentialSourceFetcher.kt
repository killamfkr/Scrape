package com.scrape.iptv.parse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val MAX_BODY_CHARS = 600_000

private val USER_AGENTS = listOf(
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36",
    "VLC/3.0.20 (Linux) LibVLC/3.0.20",
    "Lavf/60.16.100",
    "XtreamIptvClient/1.0",
)

private val RETRY_HTTP_CODES = setOf(401, 403, 404)

/**
 * Fetches the single URL the user entered (e.g. M3U or a small HTML page) and returns the body as text.
 */
object CredentialSourceFetcher {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun fetchAsString(url: String): String = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) throw IOException("Empty URL.")
        var lastError: IOException? = null
        for ((index, ua) in USER_AGENTS.withIndex()) {
            try {
                return@withContext executeFetch(trimmed, ua)
            } catch (e: IOException) {
                lastError = e
                if (index < USER_AGENTS.lastIndex && isRetryableWithDifferentAgent(e)) {
                    continue
                }
                throw e
            }
        }
        throw lastError ?: IOException("Request failed.")
    }

    private fun isRetryableWithDifferentAgent(e: IOException): Boolean {
        val m = Regex("""HTTP\s+(401|403|404)\b""").find(e.message ?: return false) ?: return false
        return m.groupValues[1].toIntOrNull() in RETRY_HTTP_CODES
    }

    private fun executeFetch(url: String, userAgent: String): String {
        val req = buildRequest(url, userAgent)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} for:\n$url")
            }
            val body = resp.body?.string().orEmpty()
            if (body.length > MAX_BODY_CHARS) {
                throw IOException("Downloaded page is too large to parse safely.")
            }
            return body
        }
    }

    private fun buildRequest(url: String, userAgent: String): Request {
        val b = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")

        val httpUrl: HttpUrl? = url.toHttpUrlOrNull()
        if (httpUrl != null) {
            val origin = buildString {
                append(httpUrl.scheme, "://", httpUrl.host)
                val def = HttpUrl.defaultPort(httpUrl.scheme)
                if (httpUrl.port != def) append(':', httpUrl.port)
            }
            b.header("Referer", "$origin/")
        }
        return b.build()
    }
}
