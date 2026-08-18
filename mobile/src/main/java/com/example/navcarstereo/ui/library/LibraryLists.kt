package com.example.navcarstereo.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage

/**
 * Chiave usata da PlaybackService per raggruppare le righe della tab Home in sezioni
 * (stesso protocollo MediaBrowser standard, non logica di dominio: ridichiarata qui invece
 * di dipendere da `shared` solo per una costante).
 */
private const val GROUP_TITLE_KEY = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"

@Composable
fun MediaItemRow(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.mediaMetadata.artworkUri,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = item.mediaMetadata.title?.toString() ?: item.mediaId,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            item.mediaMetadata.artist?.let {
                Text(text = it.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Lista piatta: usata da Album, Playlist, dettaglio album/playlist e risultati di ricerca. */
@Composable
fun LibraryFlatList(items: List<MediaItem>, onItemClick: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(items, key = { it.mediaId }) { item ->
            MediaItemRow(item, onClick = { onItemClick(item) })
            HorizontalDivider()
        }
    }
}

/** Home: gli item arrivano già raggruppati in blocchi contigui per sezione (extra [GROUP_TITLE_KEY]). */
@Composable
fun LibraryHomeList(items: List<MediaItem>, onItemClick: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    val sections = remember(items) {
        val result = mutableListOf<Pair<String, MutableList<MediaItem>>>()
        items.forEach { item ->
            val groupTitle = item.mediaMetadata.extras?.getString(GROUP_TITLE_KEY) ?: ""
            if (result.lastOrNull()?.first != groupTitle) result.add(groupTitle to mutableListOf())
            result.last().second.add(item)
        }
        result
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        sections.forEach { (title, sectionItems) ->
            item(key = "header:$title") {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            item(key = "row:$title") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(sectionItems, key = { it.mediaId }) { album ->
                        AlbumCard(album, onClick = { onItemClick(album) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(120.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = item.mediaMetadata.artworkUri,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text(
            text = item.mediaMetadata.title?.toString() ?: item.mediaId,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        item.mediaMetadata.artist?.let {
            Text(text = it.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Dettaglio album/playlist: art fetchata una sola volta in testa, poi tracce senza cover per riga. */
@Composable
fun AlbumDetailView(
    container: MediaItem,
    tracks: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    onDownloadAlbum: () -> Unit,
    onDownloadTrack: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item(key = "header") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = container.mediaMetadata.artworkUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(0.5f).aspectRatio(1f),
                )
                Text(
                    text = container.mediaMetadata.title?.toString() ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                container.mediaMetadata.artist?.let {
                    Text(text = it.toString(), style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onDownloadAlbum) {
                    Icon(Icons.Filled.Download, contentDescription = "Scarica album")
                }
            }
            HorizontalDivider()
        }
        items(tracks, key = { it.mediaId }) { track ->
            TrackRow(track, onClick = { onItemClick(track) }, onDownloadClick = { onDownloadTrack(track) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun TrackRow(item: MediaItem, onClick: () -> Unit, onDownloadClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = item.mediaMetadata.title?.toString() ?: item.mediaId,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            item.mediaMetadata.artist?.let {
                Text(text = it.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = onDownloadClick) {
            Icon(Icons.Filled.Download, contentDescription = "Scarica brano")
        }
    }
}
