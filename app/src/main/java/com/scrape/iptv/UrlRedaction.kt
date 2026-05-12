package com.scrape.iptv

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Masks username/password in typical Xtream-style stream URLs for safe display.
 * Copy / open actions should still use the original URL from [StreamUrlBuilder].
 */
object UrlRedaction {

    private const val LIVE = "/live/"

    /**
     * Replaces `/live/<user>/<pass>/` with `/live/***/***/` when segments match [username] and [password].
     * Also strips `username:password@` from the authority if present.
     */
    fun redactLiveCredentials(url: String, username: String, password: String): String {
        if (username.isBlank() || password.isBlank()) return url
        var out = redactLivePath(url, username, password)
        val userInfo = "$username:$password@"
        if (out.contains(userInfo)) {
            out = out.replace(userInfo, "***:***@")
        }
        out = redactQueryValue(out, "username", username)
        out = redactQueryValue(out, "password", password)
        out = redactQueryValue(out, "user", username)
        out = redactQueryValue(out, "pass", password)
        return out
    }

    private fun redactLivePath(url: String, username: String, password: String): String {
        val lower = url.lowercase()
        val idx = lower.indexOf(LIVE)
        if (idx < 0) return url
        val start = idx + LIVE.length
        val tail = url.substring(start)
        val slashAfterUser = tail.indexOf('/')
        if (slashAfterUser < 0) return url
        val u = tail.substring(0, slashAfterUser)
        val afterUser = tail.substring(slashAfterUser + 1)
        val slashAfterPass = afterUser.indexOf('/')
        if (slashAfterPass < 0) return url
        val p = afterUser.substring(0, slashAfterPass)
        val rest = afterUser.substring(slashAfterPass)
        return if (u == username && p == password) {
            url.substring(0, start) + "***/***/" + rest
        } else {
            url
        }
    }

    private fun redactQueryValue(url: String, param: String, rawValue: String): String {
        if (rawValue.isEmpty()) return url
        val needle = "$param=$rawValue"
        val enc = try {
            URLEncoder.encode(rawValue, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            rawValue
        }
        var out = url
        out = out.replace(needle, "$param=***", ignoreCase = true)
        if (enc != rawValue) {
            out = out.replace("$param=$enc", "$param=***", ignoreCase = true)
        }
        return out
    }
}
