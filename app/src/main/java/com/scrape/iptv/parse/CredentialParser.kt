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

    /**
     * URLs to try when downloading pasted text, highest priority first.
     * Prefer playlist/API/live URLs so we do not hit the first random `http` link (images, etc.).
     */
    fun candidateFetchUrls(text: String): List<String> {
        val raw = text.trim()
        if (raw.isEmpty()) return emptyList()
        val found = LinkedHashSet<String>()
        firstHttpUrl.findAll(raw).forEach { m ->
            found.add(sanitizeUrl(m.value))
        }
        if (found.isEmpty()) {
            val firstLine = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }
            if (firstLine != null && firstLine.startsWith("http", ignoreCase = true)) {
                found.add(sanitizeUrl(firstLine))
            }
        }
        return found
            .asSequence()
            .filterNot { isBarePanelRootUrl(it) }
            .sortedByDescending { fetchCandidateScore(it) }
            .toList()
    }

    /**
     * True for `http://host:8080` or `http://host:8080/` — there is usually no document at `/`, so fetching it returns 404.
     * These are not downloaded; use [parsePanelRootOnly] to fill the server field only.
     */
    fun isBarePanelRootUrl(url: String): Boolean {
        return normalizeBarePanelBaseUrl(url) != null
    }

    /**
     * If [raw] is a single non-empty line that is only `scheme://host[:port]` (no path, no query, no userinfo),
     * returns that panel base for the "Panel base URL" field. Otherwise null.
     */
    fun parsePanelRootOnly(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val lines = t.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size != 1) return null
        return normalizeBarePanelBaseUrl(lines[0])
    }

    /**
     * Normalizes `http(s)://host[:port]` or `host:port` / `host` with empty path into `http(s)://host[:port]`.
     */
    fun normalizeBarePanelBaseUrl(input: String): String? {
        val s = input.trim()
        if (s.isEmpty()) return null
        val withScheme = if (s.startsWith("http", ignoreCase = true)) s else "http://$s"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.rawUserInfo != null) return null
        if (!uri.rawQuery.isNullOrBlank()) return null
        val path = (uri.path ?: "").trim('/')
        if (path.isNotEmpty()) return null
        val scheme = (uri.scheme ?: "http").lowercase()
        val portPart = if (uri.port > 0) ":${uri.port}" else ""
        return "$scheme://${uri.host}$portPart"
    }

    private fun fetchCandidateScore(u: String): Int {
        val l = u.lowercase()
        var s = 0
        if (l.contains(".m3u") || l.contains(".m3u8")) s += 120
        if (l.contains("get.php")) s += 100
        if (l.contains("player_api.php")) s += 95
        if (l.contains("type=m3u")) s += 45
        if (l.contains("/live/")) s += 70
        if (l.contains("playlist")) s += 40
        if (l.contains("download")) s += 30
        if (l.contains("api")) s += 15
        if (l.endsWith(".php") || l.contains(".php?")) s += 25
        if (l.contains("stalker") || l.contains("portal")) s += 20
        return s
    }

    /** Best URL to fetch first (same ordering as [candidateFetchUrls]). */
    fun findFirstHttpUrl(text: String): String? = candidateFetchUrls(text).firstOrNull()

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
