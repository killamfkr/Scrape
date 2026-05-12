package com.scrape.iptv

/**
 * Builds HLS / TS URLs for live streams (Xtream Codes style path layout).
 */
object StreamUrlBuilder {
    fun liveStreamUrl(
        panelBaseUrl: String,
        username: String,
        password: String,
        streamId: String,
        extension: String,
    ): String {
        val base = panelBaseUrl.trim().trimEnd('/')
        val ext = extension.trim().removePrefix(".")
        return "$base/live/$username/$password/$streamId.$ext"
    }
}
