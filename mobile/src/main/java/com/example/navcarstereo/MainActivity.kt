package com.example.navcarstereo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.navcarstereo.ui.theme.NavCarStereoTheme
import com.example.navcarstereo.shared.navidrome.CredentialsStore
import com.example.navcarstereo.shared.navidrome.NavidromeClient
import com.example.navcarstereo.shared.navidrome.NavidromeConfig
import com.example.navcarstereo.ui.library.LibraryScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = CredentialsStore(this)
        setContent {
            NavCarStereoTheme {
                var showSetup by remember { mutableStateOf(store.load() == null) }
                if (showSetup) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        SetupScreen(
                            store = store,
                            modifier = Modifier.padding(innerPadding),
                            onConfigured = { showSetup = false },
                        )
                    }
                } else {
                    LibraryScreen(onOpenSetup = { showSetup = true })
                }
            }
        }
    }
}

private sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Checking : ConnectionStatus
    data object Success : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

@Composable
private fun SetupScreen(store: CredentialsStore, modifier: Modifier = Modifier, onConfigured: () -> Unit = {}) {
    var servers by remember { mutableStateOf(store.listServers()) }
    var active by remember { mutableStateOf(store.load()) }
    var showAddForm by remember { mutableStateOf(servers.isEmpty()) }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Idle) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        servers = store.listServers()
        active = store.load()
    }

    fun connect(config: NavidromeConfig, onSuccess: () -> Unit) {
        status = ConnectionStatus.Checking
        scope.launch {
            status = try {
                NavidromeClient(config).ping()
                onSuccess()
                refresh()
                ConnectionStatus.Success
            } catch (e: Exception) {
                ConnectionStatus.Error(e.message ?: "Connessione fallita")
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Server Navidrome", modifier = Modifier.weight(1f))
            if (active != null) {
                TextButton(onClick = onConfigured) { Text("Chiudi") }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 8.dp)) {
            items(servers) { config ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { connect(config) { store.save(config); onConfigured() } },
                    ) {
                        Text(text = "${config.serverUrl} — ${config.username}")
                        if (config.serverUrl == active?.serverUrl && config.username == active?.username) {
                            Text(text = "Attivo")
                        }
                    }
                    TextButton(onClick = {
                        store.removeServer(config)
                        refresh()
                    }) {
                        Text("Rimuovi")
                    }
                }
                HorizontalDivider()
            }
        }

        if (showAddForm) {
            TextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("URL server (es. https://navidrome.miodominio.it)") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            TextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Utente") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {
                    val config = NavidromeConfig(serverUrl, username, password)
                    connect(config) {
                        store.saveServer(config)
                        store.save(config)
                        serverUrl = ""
                        username = ""
                        password = ""
                        showAddForm = false
                        onConfigured()
                    }
                }) {
                    Text("Connetti e salva")
                }
                OutlinedButton(onClick = { showAddForm = false }) {
                    Text("Annulla")
                }
            }
        } else {
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = { showAddForm = true },
            ) {
                Text("Aggiungi server")
            }
        }

        when (val current = status) {
            is ConnectionStatus.Idle -> Unit
            is ConnectionStatus.Checking -> CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            is ConnectionStatus.Success -> Text(
                text = "Connesso. Apri Android Auto per vedere la libreria.",
                modifier = Modifier.padding(top = 16.dp),
            )
            is ConnectionStatus.Error -> Text(
                text = "Errore: ${current.message}",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
