package com.example.navcarstereo.ui.library

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.navcarstereo.R
import com.example.navcarstereo.player.PlaybackUiState
import com.example.navcarstereo.shared.navidrome.CredentialsStore
import com.example.navcarstereo.shared.navidrome.NavLyrics
import com.example.navcarstereo.shared.navidrome.NavidromeClient
import com.example.navcarstereo.shared.parseSongId

private const val UI_PREFS_NAME = "ui_prefs"
private const val KEY_SHOW_LYRICS = "show_lyrics"

/** Stessi controlli del "now playing" di Android Auto (play/pausa, prev/next, shuffle, repeat), solo in un layout mobile. */
@Composable
fun NowPlayingSheet(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.nowPlaying
    var scrubPositionMs by remember(item?.mediaId) { mutableStateOf<Float?>(null) }

    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE) }
    var showLyrics by remember { mutableStateOf(uiPrefs.getBoolean(KEY_SHOW_LYRICS, false)) }
    val lyricsCache = remember { mutableMapOf<String, NavLyrics?>() }
    var lyrics by remember { mutableStateOf<NavLyrics?>(null) }

    LaunchedEffect(item?.mediaId) {
        val songId = item?.mediaId?.let(::parseSongId)
        lyrics = when {
            songId == null -> null
            lyricsCache.containsKey(songId) -> lyricsCache[songId]
            else -> {
                val config = CredentialsStore(context).load()
                val fetched = config?.let { runCatching { NavidromeClient(it).getLyricsBySongId(songId) }.getOrNull() }
                lyricsCache[songId] = fetched
                fetched
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = {
                showLyrics = !showLyrics
                uiPrefs.edit().putBoolean(KEY_SHOW_LYRICS, showLyrics).apply()
            }) {
                Icon(
                    Icons.Filled.Lyrics,
                    contentDescription = stringResource(R.string.cd_lyrics),
                    tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (showLyrics && lyrics != null) {
            LyricsView(
                lyrics = requireNotNull(lyrics),
                positionMs = state.positionMs,
                modifier = Modifier.fillMaxWidth().size(280.dp),
            )
        } else {
            AsyncImage(
                model = item?.mediaMetadata?.artworkUri,
                contentDescription = null,
                modifier = Modifier.size(280.dp),
            )
        }
        Text(
            text = item?.mediaMetadata?.title?.toString() ?: "",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp),
        )
        item?.mediaMetadata?.artist?.let {
            Text(text = it.toString(), style = MaterialTheme.typography.bodyLarge)
        }

        val durationMs = state.durationMs.coerceAtLeast(1L)
        LinearProgressIndicator(
            progress = { (state.bufferedPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(2.dp),
        )
        Slider(
            value = scrubPositionMs ?: state.positionMs.toFloat(),
            onValueChange = { scrubPositionMs = it },
            onValueChangeFinished = {
                scrubPositionMs?.let { onSeekTo(it.toLong()) }
                scrubPositionMs = null
            },
            valueRange = 0f..durationMs.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val activeTint = MaterialTheme.colorScheme.primary
            val inactiveTint = MaterialTheme.colorScheme.onSurface
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = stringResource(R.string.cd_shuffle),
                    tint = if (state.shuffleEnabled) activeTint else inactiveTint,
                )
            }
            IconButton(onClick = onSkipPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.cd_previous))
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                )
            }
            IconButton(onClick = onSkipNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.cd_next))
            }
            IconButton(onClick = onCycleRepeatMode) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = stringResource(R.string.cd_repeat),
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) inactiveTint else activeTint,
                )
            }
        }
    }
}
