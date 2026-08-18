package com.example.navcarstereo.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.example.navcarstereo.shared.PlaybackService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val nowPlaying: MediaItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/**
 * Client MediaBrowser di [PlaybackService]: nessuna chiamata Subsonic qui, solo un ponte tra il
 * [Player] già gestito dal servizio (condiviso con Android Auto) e lo stato osservabile da Compose.
 */
@UnstableApi
class PlayerController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var browser: MediaBrowser? = null
    private val browserReady = CompletableDeferred<MediaBrowser>()

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state

    suspend fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java.name))
        val b = MediaBrowser.Builder(context, token).buildAsync().await().also {
            it.addListener(playerListener)
        }
        browser = b
        browserReady.complete(b)
        pushState()
        scope.launch {
            while (isActive) {
                if (browser?.isPlaying == true) pushState()
                delay(500)
            }
        }
    }

    fun release() {
        browser?.release()
        browser = null
    }

    suspend fun children(parentId: String): List<MediaItem> =
        browserReady.await().getChildren(parentId, 0, Int.MAX_VALUE, null).await().value.orEmpty()

    suspend fun search(query: String): List<MediaItem> {
        val b = browserReady.await()
        b.search(query, null).await()
        return b.getSearchResult(query, 0, Int.MAX_VALUE, null).await().value.orEmpty()
    }

    /** Passa il MediaItem così com'è al servizio: è lui a espandere album/playlist in coda. */
    fun play(item: MediaItem) {
        browser?.apply {
            setMediaItem(item)
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        browser?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipNext() {
        browser?.seekToNext()
    }

    fun skipPrevious() {
        browser?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        browser?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        browser?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
        pushState()
    }

    fun cycleRepeatMode() {
        browser?.let { it.repeatMode = nextRepeatMode(it.repeatMode) }
        pushState()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = pushState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = pushState()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = pushState()
        override fun onRepeatModeChanged(repeatMode: Int) = pushState()
        override fun onPlaybackStateChanged(playbackState: Int) = pushState()
    }

    private fun pushState() {
        browser?.let {
            _state.value = PlaybackUiState(
                nowPlaying = it.currentMediaItem,
                isPlaying = it.isPlaying,
                positionMs = it.currentPosition.coerceAtLeast(0),
                durationMs = it.duration.coerceAtLeast(0),
                bufferedPositionMs = it.bufferedPosition.coerceAtLeast(0),
                shuffleEnabled = it.shuffleModeEnabled,
                repeatMode = it.repeatMode,
            )
        }
    }
}

/** OFF -> ALL -> ONE -> OFF, l'ordine standard dei player musicali. */
fun nextRepeatMode(current: Int): Int = when (current) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}
