package com.scrape.iptv.parse

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Extracts panel base URL, username, and password from a pasted provider link or page body.
 * Supports common Xtream-style patterns only; no crawling beyond the single URL you supply.
 */
object CredentialParser {

    private val firstHttpUrl = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)
    private val liveStyleUrl = Regex("https?://[^\\s\"'<>]+/live/[^\\s\"'<>]+", RegexOption.IGNORE_CASE)

    fun parseFromUserInput(raw: String): CredentialParseResult? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        parseFromPlainText(trimmed)?.let { return it }
        val firstLine = trimmed.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        parseHttpUrl(firstLine)?.let { return it }
        extractFirstUrl(trimmed)?.let { parseHttpUrl(it) }?.let { return it }
        return null
    }

    fun parseFromPlainText(text: String): CredentialParseResult? {
        liveStyleUrl.findAll(text).forEach { m ->
            parseHttpUrl(sanitizeUrl(m.value))?.let { return it }
        }
        firstHttpUrl.findAll(text).forEach { m ->
            val url = sanitizeUrl(m.value)
            parseHttpUrl(url)?.let { return it }
        }
        return null
    }

    private fun sanitizeUrl(s: String): String {
        return s.trimEnd(',', '.', ';', ')', ']', '"', '\'')
    }

    private fun extractFirstUrl(text: String): String? {
        return firstHttpUrl.find(text)?.value?.let { sanitizeUrl(it) }
    }

    /** First http(s) URL in text (any line); used to fetch a page or playlist the user pasted. */
    fun findFirstHttpUrl(text: String): String? = extractFirstUrl(text)

    fun parseHttpUrl(urlString: String): CredentialParseResult? {
        val normalized = if (urlString.startsWith("http", ignoreCase = true)) {
            urlString
        } else {
            "http://$urlString"
        }
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val scheme = (uri.scheme ?: "http").lowercase()
        val host = uri.host ?: return null
        val portPart = if (uri.port > 0) ":${uri.port}" else ""
        val base = "$scheme://$host$portPart"

        uri.rawUserInfo?.let { info ->
            val idx = info.indexOf(':')
            if (idx > 0 && idx < info.length - 1) {
                val u = urlDecode(info.substring(0, idx))
                val p = urlDecode(info.substring(idx + 1))
                if (u.isNotEmpty() && p.isNotEmpty()) {
                    return CredentialParseResult(base, u, p, "from user:pass@host")
                }
            }
        }

        parseQueryCredentials(uri)?.let { (u, p) ->
            if (u.isNotEmpty() && p.isNotEmpty()) {
                return CredentialParseResult(base, u, p, "from query string")
            }
        }

        parseLivePath(uri, scheme, host, portPart)?.let { return it }

        parseTwoSegmentPortalPath(uri, scheme, host, portPart)?.let { return it }

        return null
    }

    private fun parseQueryCredentials(uri: URI): Pair<String, String>? {
        val raw = uri.rawQuery ?: return null
        val params = splitQuery(raw)
        val u = params["username"] ?: params["user"] ?: params["u"] ?: return null
        val p = params["password"] ?: params["pass"] ?: params["pwd"] ?: params["p"] ?: return null
        return u to p
    }

    private fun splitQuery(rawQuery: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (part in rawQuery.split('&')) {
            if (part.isEmpty()) continue
            val eq = part.indexOf('=')
            val key = urlDecode(if (eq >= 0) part.substring(0, eq) else part).lowercase()
            val value = urlDecode(if (eq >= 0) part.substring(eq + 1) else "")
            out[key] = value
        }
        return out
    }

    private fun urlDecode(s: String): String {
        return runCatching {
            URLDecoder.decode(s, StandardCharsets.UTF_8.name())
        }.getOrDefault(s)
    }

    private fun parseLivePath(uri: URI, scheme: String, host: String, portPart: String): CredentialParseResult? {
        val path = uri.path ?: return null
        val parts = path.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        val i = parts.indexOf("live")
        if (i < 0 || i + 2 >= parts.size) return null
        val user = parts[i + 1]
        val pass = parts[i + 2]
        if (user.isEmpty() || pass.isEmpty()) return null
        val base = "$scheme://$host$portPart"
        return CredentialParseResult(base, user, pass, "from /live/… path")
    }

    private val reservedFirstSegments = setOf(
        "live", "movie", "series", "timeshift", "get.php", "player_api.php",
        "c", "p", "stream", "api", "epg",
    )

    private fun parseTwoSegmentPortalPath(
        uri: URI,
        scheme: String,
        host: String,
        portPart: String,
    ): CredentialParseResult? {
        if (uri.rawQuery?.isNotEmpty() == true) return null
        val path = uri.path ?: return null
        val parts = path.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != 2) return null
        val a = parts[0]
        val b = parts[1]
        if (a.lowercase() in reservedFirstSegments) return null
        if (a.contains('.') && a.substringAfterLast('.').length <= 4) return null
        val base = "$scheme://$host$portPart"
        return CredentialParseResult(base, a, b, "from /username/password path")
    }
}
