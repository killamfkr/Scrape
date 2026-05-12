package com.scrape.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrape.iptv.api.LiveCategory
import com.scrape.iptv.api.LiveStream
import com.scrape.iptv.api.XtreamApiFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IptvUiState(
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
)

class IptvViewModel : ViewModel() {
    private val _state = MutableStateFlow(IptvUiState())
    val state: StateFlow<IptvUiState> = _state.asStateFlow()

    fun setServerUrl(value: String) = _state.update { it.copy(serverUrl = value, errorMessage = null) }
    fun setUsername(value: String) = _state.update { it.copy(username = value, errorMessage = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, errorMessage = null) }
    fun setStreamExtension(value: String) = _state.update { it.copy(streamExtension = value, errorMessage = null) }

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
