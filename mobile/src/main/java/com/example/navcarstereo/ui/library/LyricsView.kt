package com.example.navcarstereo.ui.library

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.navcarstereo.shared.navidrome.NavLyrics

/**
 * Lyrics non sincronizzate (nessun [com.example.navcarstereo.shared.navidrome.NavLyricLine.startMs])
 * → lista statica scrollabile a mano, senza auto-scroll né riga evidenziata.
 */
@Composable
fun LyricsView(lyrics: NavLyrics, positionMs: Long, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val synced = lyrics.lines.any { it.startMs != null }
    val currentLine = if (synced) {
        lyrics.lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= positionMs }.coerceAtLeast(0)
    } else {
        null
    }

    LaunchedEffect(currentLine) {
        currentLine?.let { listState.animateScrollToItem(it) }
    }

    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(lyrics.lines) { index, line ->
            Text(
                text = line.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (index == currentLine) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}
