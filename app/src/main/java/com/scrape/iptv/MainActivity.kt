package com.scrape.iptv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrape.iptv.api.LiveStream
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                IptvApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IptvApp(vm: IptvViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Xtream Codes IPTV") },
                actions = {
                    IconButton(
                        onClick = { vm.connectAndLoadCategories() },
                        enabled = !state.isLoading,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload account")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Your link or playlist",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = state.sourceUrl,
                onValueChange = vm::setSourceUrl,
                label = { Text("Source URL or pasted M3U") },
                placeholder = { Text("Paste here when you have it — can be left empty") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 6,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.fillCredentialsFromSourceUrl() },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Fill server, username & password from source")
            }
            state.lastParseHint?.let { hint ->
                Spacer(Modifier.height(6.dp))
                Text(hint, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Panel login",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = vm::setServerUrl,
                label = { Text("Panel base URL") },
                placeholder = { Text("http://host:8080") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.username,
                onValueChange = vm::setUsername,
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = vm::setPassword,
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.streamExtension,
                onValueChange = vm::setStreamExtension,
                label = { Text("Stream file extension") },
                placeholder = { Text("ts or m3u8") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { vm.connectAndLoadCategories() },
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Connect & load categories")
                }
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.width(36.dp).height(36.dp))
                }
            }
            state.accountSummary?.let { summary ->
                Spacer(Modifier.height(12.dp))
                Text(summary, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text("Live categories", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp, max = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(
                    state.categories,
                    key = { i, c -> "${c.categoryId}_$i" },
                ) { _, cat ->
                    FilterChip(
                        selected = cat.categoryId == state.selectedCategory?.categoryId,
                        onClick = { vm.selectCategory(cat) },
                        label = { Text(cat.categoryName ?: "—") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.selectedCategory != null) {
                    "Streams — ${state.selectedCategory?.categoryName ?: ""}"
                } else {
                    "Pick a category"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    state.streams,
                    key = { idx, s -> "${s.streamId}_$idx" },
                ) { _, stream ->
                    StreamRow(
                        stream = stream,
                        url = vm.streamPlayUrl(stream),
                        onCopy = { url ->
                            clipboard.setText(AnnotatedString(url))
                            scope.launch { snackbar.showSnackbar("Copied stream URL") }
                        },
                        onOpenExternal = { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    scope.launch {
                                        snackbar.showSnackbar("No app can open this URL")
                                    }
                                }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamRow(
    stream: LiveStream,
    url: String?,
    onCopy: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(stream.name ?: "(no name)", style = MaterialTheme.typography.titleSmall)
            url?.let { u ->
                Text(
                    u,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 4,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { onCopy(u) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL")
                    }
                    IconButton(onClick = { onOpenExternal(u) }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open in player")
                    }
                }
            } ?: Text("Missing stream id", style = MaterialTheme.typography.bodySmall)
        }
    }
}
