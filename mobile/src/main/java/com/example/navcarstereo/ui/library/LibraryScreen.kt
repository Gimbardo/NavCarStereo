package com.example.navcarstereo.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.example.navcarstereo.download.downloadAlbum
import com.example.navcarstereo.download.downloadTrack
import com.example.navcarstereo.player.PlayerController
import com.example.navcarstereo.shared.navidrome.CredentialsStore
import kotlinx.coroutines.delay

private sealed interface Tab {
    data object Home : Tab
    data object Albums : Tab
    data object Playlists : Tab
}

private sealed interface Destination {
    data class TabRoot(val tab: Tab) : Destination
    data class Detail(val item: MediaItem) : Destination
    data class SearchResults(val query: String) : Destination
}

private const val HOME_ID = "home"
private const val ALBUMS_ID = "albums"
private const val PLAYLISTS_ID = "playlists"

private fun Tab.rootId() = when (this) {
    Tab.Home -> HOME_ID
    Tab.Albums -> ALBUMS_ID
    Tab.Playlists -> PLAYLISTS_ID
}

private fun Tab.title() = when (this) {
    Tab.Home -> "Home"
    Tab.Albums -> "Album"
    Tab.Playlists -> "Playlist"
}

private fun Tab.icon() = when (this) {
    Tab.Home -> Icons.Filled.Home
    Tab.Albums -> Icons.Filled.Album
    Tab.Playlists -> Icons.AutoMirrored.Filled.QueueMusic
}

/** Schermata libreria mobile: stessi controlli di Android Auto (tab, ricerca, riproduzione), stile Spotify. */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun LibraryScreen(onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember { PlayerController(context) }
    val config = remember { CredentialsStore(context).load() }
    LaunchedEffect(Unit) { controller.connect() }
    DisposableEffect(Unit) { onDispose { controller.release() } }

    var pendingDownload by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    val playback by controller.state.collectAsState()

    var selectedTab by remember { mutableStateOf<Tab>(Tab.Home) }
    var destination by remember { mutableStateOf<Destination>(Destination.TabRoot(Tab.Home)) }
    var searching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showNowPlaying by remember { mutableStateOf(false) }
    val nowPlayingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchQuery, searching) {
        if (!searching) return@LaunchedEffect
        delay(300)
        destination = Destination.SearchResults(searchQuery)
    }

    LaunchedEffect(searching) {
        if (searching) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(destination) {
        loading = true
        items = when (val dest = destination) {
            is Destination.TabRoot -> controller.children(dest.tab.rootId())
            is Destination.Detail -> controller.children(dest.item.mediaId)
            is Destination.SearchResults -> if (dest.query.isBlank()) emptyList() else controller.search(dest.query)
        }
        loading = false
    }

    fun openTab(tab: Tab) {
        searching = false
        selectedTab = tab
        destination = Destination.TabRoot(tab)
    }

    fun onItemClick(item: MediaItem) {
        if (item.mediaMetadata.isBrowsable == true) {
            destination = Destination.Detail(item)
        } else {
            controller.play(item)
            showNowPlaying = true
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            placeholder = { Text("Cerca album, artisti, brani") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Svuota")
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                        )
                    } else {
                        val title = when (val dest = destination) {
                            is Destination.TabRoot -> dest.tab.title()
                            is Destination.Detail -> dest.item.mediaMetadata.title?.toString() ?: ""
                            is Destination.SearchResults -> "Ricerca"
                        }
                        Text(title)
                    }
                },
                navigationIcon = {
                    if (destination !is Destination.TabRoot) {
                        IconButton(onClick = { openTab(selectedTab) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; openTab(selectedTab) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Chiudi ricerca")
                        }
                    } else {
                        IconButton(onClick = { searching = true; searchQuery = "" }) {
                            Icon(Icons.Filled.Search, contentDescription = "Cerca")
                        }
                        IconButton(onClick = onOpenSetup) {
                            Icon(Icons.Filled.Settings, contentDescription = "Impostazioni")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                playback.nowPlaying?.let { nowPlaying ->
                    MiniPlayerBar(
                        item = nowPlaying,
                        isPlaying = playback.isPlaying,
                        onClick = { showNowPlaying = true },
                        onTogglePlayPause = { controller.togglePlayPause() },
                        onSkipNext = { controller.skipNext() },
                    )
                    HorizontalDivider()
                }
                NavigationBar {
                    listOf(Tab.Home, Tab.Albums, Tab.Playlists).forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab && destination is Destination.TabRoot,
                            onClick = { openTab(tab) },
                            label = { Text(tab.title()) },
                            icon = { Icon(tab.icon(), contentDescription = null) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                when (val dest = destination) {
                    is Destination.Detail -> AlbumDetailView(
                        dest.item,
                        items,
                        onItemClick = ::onItemClick,
                        onDownloadAlbum = {
                            val title = dest.item.mediaMetadata.title?.toString() ?: "l'album"
                            pendingDownload = "Scaricare tutti i brani di \"$title\"?" to {
                                config?.let { downloadAlbum(context, it, items) }
                            }
                        },
                        onDownloadTrack = { track ->
                            val title = track.mediaMetadata.title?.toString() ?: "il brano"
                            pendingDownload = "Scaricare \"$title\"?" to {
                                config?.let { downloadTrack(context, it, track) }
                            }
                        },
                    )
                    is Destination.TabRoot -> if (selectedTab == Tab.Home) {
                        LibraryHomeList(items, onItemClick = ::onItemClick)
                    } else {
                        LibraryFlatList(items, onItemClick = ::onItemClick)
                    }
                    is Destination.SearchResults -> LibraryFlatList(items, onItemClick = ::onItemClick)
                }
            }
        }
    }

    if (showNowPlaying) {
        ModalBottomSheet(
            onDismissRequest = { showNowPlaying = false },
            sheetState = nowPlayingSheetState,
        ) {
            NowPlayingSheet(
                state = playback,
                onTogglePlayPause = { controller.togglePlayPause() },
                onSkipPrevious = { controller.skipPrevious() },
                onSkipNext = { controller.skipNext() },
                onToggleShuffle = { controller.toggleShuffle() },
                onCycleRepeatMode = { controller.cycleRepeatMode() },
                onSeekTo = { controller.seekTo(it) },
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }

    pendingDownload?.let { (message, confirm) ->
        AlertDialog(
            onDismissRequest = { pendingDownload = null },
            title = { Text("Download") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { confirm(); pendingDownload = null }) { Text("Scarica") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownload = null }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun MiniPlayerBar(
    item: MediaItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = item.mediaMetadata.artworkUri,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(text = item.mediaMetadata.title?.toString() ?: "", maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            item.mediaMetadata.artist?.let { Text(text = it.toString(), maxLines = 1, style = MaterialTheme.typography.bodySmall) }
        }
        IconButton(onClick = onTogglePlayPause) {
            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pausa" else "Play")
        }
        IconButton(onClick = onSkipNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Successivo")
        }
    }
}
