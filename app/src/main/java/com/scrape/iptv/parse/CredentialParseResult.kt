package com.scrape.iptv.parse

data class CredentialParseResult(
    val serverBaseUrl: String,
    val username: String,
    val password: String,
    val hint: String? = null,
)
