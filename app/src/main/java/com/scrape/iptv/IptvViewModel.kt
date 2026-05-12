package com.scrape.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrape.iptv.api.LiveCategory
import com.scrape.iptv.api.LiveStream
import com.scrape.iptv.api.XtreamApiFactory
import com.scrape.iptv.parse.CredentialParser
import com.scrape.iptv.parse.CredentialParseResult
import com.scrape.iptv.parse.CredentialSourceFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IptvUiState(
    val sourceUrl: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val streamExtension: String = "ts",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val accountSummary: String? = null,
    val categories: List<LiveCategory> = emptyList(),
    val streams: List<LiveStream> = emptyList(),
    val selectedCategory: LiveCategory? = null,
    val connectedBaseUrl: String? = null,
    val lastParseHint: String? = null,
)

class IptvViewModel : ViewModel() {
    private val _state = MutableStateFlow(IptvUiState())
    val state: StateFlow<IptvUiState> = _state.asStateFlow()

    fun setSourceUrl(value: String) = _state.update { it.copy(sourceUrl = value, errorMessage = null) }

    fun setServerUrl(value: String) = _state.update { it.copy(serverUrl = value, errorMessage = null) }
    fun setUsername(value: String) = _state.update { it.copy(username = value, errorMessage = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, errorMessage = null) }
    fun setStreamExtension(value: String) = _state.update { it.copy(streamExtension = value, errorMessage = null) }

    fun fillCredentialsFromSourceUrl() {
        val raw = _state.value.sourceUrl.trim()
        if (raw.isEmpty()) {
            _state.update {
                it.copy(errorMessage = "Paste your source URL or playlist text first.")
            }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    lastParseHint = null,
                )
            }
            val parsed = CredentialParser.parseFromUserInput(raw)
            if (parsed != null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        serverUrl = parsed.serverBaseUrl,
                        username = parsed.username,
                        password = parsed.password,
                        lastParseHint = buildString {
                            append("Filled fields")
                            parsed.hint?.let { h -> append(" ($h)") }
                            append(".")
                        },
                        errorMessage = null,
                    )
                }
                return@launch
            }

            val panelRoot = CredentialParser.parsePanelRootOnly(raw)
            if (panelRoot != null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        serverUrl = panelRoot,
                        lastParseHint = "Panel address only (no playlist at the root URL). Enter username and password, or paste an M3U / portal link that includes them.",
                        errorMessage = null,
                    )
                }
                return@launch
            }

            val fetched = runCatching { fetchThenParse(raw) }.getOrElse { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: e.toString(),
                    )
                }
                return@launch
            }
            if (fetched == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Could not find a username and password in that URL or page.",
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    serverUrl = fetched.serverBaseUrl,
                    username = fetched.username,
                    password = fetched.password,
                    lastParseHint = buildString {
                        append("Filled fields")
                        fetched.hint?.let { h -> append(" ($h)") }
                        append(".")
                    },
                    errorMessage = null,
                )
            }
        }
    }

    private suspend fun fetchThenParse(raw: String): CredentialParseResult? {
        val candidates = CredentialParser.candidateFetchUrls(raw)
        if (candidates.isEmpty()) return null
        val downloadErrors = mutableListOf<String>()
        var downloadedOk = false
        for (url in candidates) {
            val body = runCatching { CredentialSourceFetcher.fetchAsString(url) }
                .onFailure { e ->
                    downloadErrors.add("${e.message ?: e.toString()}")
                }
                .getOrNull() ?: continue
            downloadedOk = true
            CredentialParser.parseFromPlainText(body)?.let { return it }
            CredentialParser.parseFromUserInput(body)?.let { return it }
        }
        if (!downloadedOk && downloadErrors.isNotEmpty()) {
            throw IllegalStateException(
                buildString {
                    append("Could not download from ")
                    append(candidates.size)
                    append(" URL(s). ")
                    append(downloadErrors.last())
                    append(" If the link works in a browser, copy the page or M3U text here instead of the page URL.")
                },
            )
        }
        return null
    }

    fun connectAndLoadCategories() {
        val server = _state.value.serverUrl.trim()
        val user = _state.value.username.trim()
        val pass = _state.value.password
        if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            _state.update { it.copy(errorMessage = "Server URL, username, and password are required.") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    categories = emptyList(),
                    streams = emptyList(),
                    selectedCategory = null,
                    accountSummary = null,
                    lastParseHint = null,
                )
            }
            runCatching {
                val api = XtreamApiFactory.create(server, debugLogging = BuildConfig.DEBUG)
                val auth = api.authenticate(user, pass)
                val ui = auth.userInfo
                if (ui?.auth != 1) {
                    throw IllegalStateException(ui?.message ?: "Authentication failed (auth != 1).")
                }
                val categories = api.getLiveCategories(user, pass)
                val summary = buildString {
                    append("Status: ${ui.status ?: "—"}")
                    ui.expDate?.let { append("\nExpires (unix): $it") }
                    auth.serverInfo?.let { s ->
                        append("\nServer: ${s.url}:${s.port} (${s.serverProtocol ?: "http"})")
                    }
                }
                Triple(server, summary, categories)
            }.onSuccess { (base, summary, categories) ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        connectedBaseUrl = base,
                        accountSummary = summary,
                        categories = categories.sortedBy { c -> c.categoryName.orEmpty() },
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun selectCategory(category: LiveCategory) {
        val base = _state.value.connectedBaseUrl ?: return
        val user = _state.value.username.trim()
        val pass = _state.value.password
        val catId = category.categoryId ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    selectedCategory = category,
                    streams = emptyList(),
                )
            }
            runCatching {
                val api = XtreamApiFactory.create(base, debugLogging = BuildConfig.DEBUG)
                api.getLiveStreams(user, pass, categoryId = catId)
            }.onSuccess { list ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        streams = list.sortedBy { s -> s.name.orEmpty() },
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun streamPlayUrl(stream: LiveStream): String? {
        val base = _state.value.connectedBaseUrl ?: return null
        val user = _state.value.username.trim()
        val pass = _state.value.password
        val id = stream.streamId ?: return null
        return StreamUrlBuilder.liveStreamUrl(base, user, pass, id, _state.value.streamExtension)
    }
}
