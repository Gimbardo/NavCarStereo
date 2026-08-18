package com.example.navcarstereo.shared

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.example.navcarstereo.shared.navidrome.CredentialsStore
import com.example.navcarstereo.shared.navidrome.NavAlbum
import com.example.navcarstereo.shared.navidrome.NavPlaylist
import com.example.navcarstereo.shared.navidrome.NavSong
import com.example.navcarstereo.shared.navidrome.NavidromeClient
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Exposes the Navidrome library to Android Auto (and any other MediaBrowser client) as three
 * root tabs: Home (rows of recent/new/top/random albums), Album (full alphabetical list), and
 * Playlist (the user's own playlists). Any album/playlist leads to its tracks (playable, in
 * order); picking a track queues the whole album/playlist from that point, so next/prev/shuffle
 * in the car's transport controls act on a sensible queue instead of a single song.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: NavidromeClient
    private lateinit var session: MediaLibrarySession
    private var lastSearchResults: List<MediaItem> = emptyList()

    override fun onCreate() {
        super.onCreate()
        val config = requireNotNull(CredentialsStore(this).load()) {
            "Navidrome non configurato: apri l'app sul telefono e inserisci i dati del server"
        }
        client = NavidromeClient(config)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = savePlaybackState()
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) savePlaybackState()
            }

            // ponytail: diagnostica temporanea per il bug "buffering si ferma sul cambio traccia
            // automatico" (BETA-ISSUES.md) — rimuovere una volta isolata la causa reale nei log.
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(
                    BUFFER_LOG_TAG,
                    "state=${playbackState.toStateName()} playWhenReady=${player.playWhenReady} " +
                        "position=${player.currentPosition} buffered=${player.bufferedPosition}",
                )
            }
        })
        session = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
        restorePlaybackState(player)
    }

    /** Ricarica l'ultimo album/playlist/brano/posizione ascoltati, in pausa: parte solo quando arriva onPostConnect. */
    private fun restorePlaybackState(player: ExoPlayer) {
        val prefs = playbackPrefs()
        val containerType = prefs.getString(PREF_CONTAINER_TYPE, null) ?: return
        val containerId = prefs.getString(PREF_CONTAINER_ID, null) ?: return
        val trackId = prefs.getString(PREF_TRACK_ID, null) ?: return
        val positionMs = prefs.getLong(PREF_POSITION_MS, 0L)
        serviceScope.launch {
            val tracks = runCatching {
                when (containerType) {
                    CONTAINER_TYPE_PLAYLIST -> client.playlistSongs(containerId).map { it.toMediaItem(containerId) }
                    else -> client.albumSongs(containerId).map { it.toMediaItem() }
                }
            }.getOrNull() ?: return@launch
            val startIndex = tracks.indexOfFirst { it.songId() == trackId }.coerceAtLeast(0)
            withContext(Dispatchers.Main) {
                player.setMediaItems(tracks.map { it.withStreamUri() }, startIndex, positionMs)
                player.prepare()
            }
        }
    }

    private fun savePlaybackState() {
        val item = session.player.currentMediaItem ?: return
        val playlistId = item.playlistId()
        val albumId = item.mediaMetadata.extras?.getString(EXTRA_ALBUM_ID)
        val (containerType, containerId) = when {
            playlistId != null -> CONTAINER_TYPE_PLAYLIST to playlistId
            albumId != null -> CONTAINER_TYPE_ALBUM to albumId
            else -> return
        }
        playbackPrefs().edit()
            .putString(PREF_CONTAINER_TYPE, containerType)
            .putString(PREF_CONTAINER_ID, containerId)
            .putString(PREF_TRACK_ID, item.songId())
            .putLong(PREF_POSITION_MS, session.player.currentPosition)
            .apply()
    }

    private fun playbackPrefs() = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun Int.toStateName(): String = when (this) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($this)"
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onDestroy() {
        session.player.release()
        session.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        /**
         * Non forziamo più play() qui: farlo durante l'handshake di connessione competeva con
         * l'audio focus e lasciava l'auto senza audio finché un altro player non lo "sbloccava".
         * L'utente/l'auto avviano la riproduzione con i comandi transport standard quando serve.
         */
        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            if (controller.packageName == ANDROID_AUTO_PACKAGE &&
                session.connectedControllers.none { it.packageName == ANDROID_AUTO_PACKAGE }
            ) {
                session.player.pause()
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("NavCarStereo")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build(),
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val items = when {
                parentId == ROOT_ID -> listOf(tabItem(HOME_ID, "Home"), tabItem(ALBUMS_ID, "Album"), tabItem(PLAYLISTS_ID, "Playlist"))
                parentId == HOME_ID -> homeRows()
                parentId == ALBUMS_ID -> client.allAlbums(offset = 0, size = ALBUMS_FETCH_SIZE).map { it.toMediaItem() }
                parentId == PLAYLISTS_ID -> client.playlists().map { it.toMediaItem() }
                parentId.startsWith(PLAYLIST_PREFIX) -> {
                    val playlistId = parentId.removePrefix(PLAYLIST_PREFIX)
                    client.playlistSongs(playlistId).map { it.toMediaItem(playlistId) }
                }
                else -> client.albumSongs(parentId).map { it.toMediaItem() }
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = serviceScope.future {
            lastSearchResults = coroutineScope {
                client.search(query)
                    .distinctBy { it.albumId ?: it.id }
                    .map { song ->
                        val albumId = song.albumId
                        if (albumId != null) {
                            async { client.album(albumId).toMediaItem() }
                        } else {
                            async { song.toMediaItem() }
                        }
                    }
                    .map { it.await() }
            }
            session.notifySearchResultChanged(browser, query, lastSearchResults.size, params)
            LibraryResult.ofVoid(params)
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(lastSearchResults), params))

        private suspend fun homeRows(): List<MediaItem> = coroutineScope {
            val recent = async { client.recentAlbums() }
            val newest = async { client.newAlbums() }
            val top = async { client.topAlbums() }
            val random = async { client.randomAlbums() }
            recent.await().map { it.toMediaItem("Ultimi ascoltati") } +
                newest.await().map { it.toMediaItem("Nuove uscite") } +
                top.await().map { it.toMediaItem("Più ascoltati") } +
                random.await().map { it.toMediaItem("Random") }
        }

        private fun tabItem(id: String, title: String): MediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .setExtras(Bundle().apply { putInt(CONTENT_STYLE_BROWSABLE_KEY, CONTENT_STYLE_GRID_ITEM) })
                    .build(),
            )
            .build()

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withStreamUri() }.toMutableList())

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            val requested = mediaItems.singleOrNull()
            val playlistId = requested?.playlistId()
            if (requested != null && playlistId != null) {
                val playlistTracks = client.playlistSongs(playlistId).map { it.toMediaItem(playlistId) }
                val startAt = playlistTracks.indexOfFirst { it.mediaId == requested.mediaId }.coerceAtLeast(0)
                return@future MediaSession.MediaItemsWithStartPosition(
                    playlistTracks.map { it.withStreamUri() },
                    startAt,
                    startPositionMs,
                )
            }
            val albumId = requested?.mediaMetadata?.extras?.getString(EXTRA_ALBUM_ID)
                ?: requested?.let { runCatching { client.song(it.songId()).albumId }.getOrNull() }
            if (requested != null && albumId != null) {
                val albumTracks = client.albumSongs(albumId).map { it.toMediaItem() }
                val startAt = albumTracks.indexOfFirst { it.mediaId == requested.songId() }.coerceAtLeast(0)
                MediaSession.MediaItemsWithStartPosition(
                    albumTracks.map { it.withStreamUri() },
                    startAt,
                    startPositionMs,
                )
            } else {
                MediaSession.MediaItemsWithStartPosition(
                    mediaItems.map { it.withStreamUri() },
                    startIndex,
                    startPositionMs,
                )
            }
        }
    }

    private fun NavAlbum.toMediaItem(groupTitle: String? = null): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(artist)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                .apply { coverArtId?.let { setArtworkUri(android.net.Uri.parse(client.coverArtUrl(it))) } }
                .apply { groupTitle?.let { setExtras(Bundle().apply { putString(CONTENT_STYLE_GROUP_TITLE_KEY, it) }) } }
                .build(),
        )
        .build()

    /**
     * playlistId != null → il brano è mostrato dentro una playlist: il mediaId diventa composito
     * ("playlist_track:$playlistId:$id") così la playlist di provenienza sopravvive al round-trip
     * legacy di Android Auto (onPlayFromMediaId passa solo il mediaId, non gli extras).
     */
    private fun NavSong.toMediaItem(playlistId: String? = null): MediaItem = MediaItem.Builder()
        .setMediaId(if (playlistId != null) "$PLAYLIST_TRACK_MEDIA_ID_PREFIX$playlistId:$id" else id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .apply { coverArtId?.let { setArtworkUri(android.net.Uri.parse(client.coverArtUrl(it))) } }
                .apply {
                    if (playlistId == null) {
                        albumId?.let {
                            setExtras(Bundle().apply { putString(EXTRA_ALBUM_ID, it) })
                        }
                    }
                }
                .build(),
        )
        .build()

    private fun NavPlaylist.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId("$PLAYLIST_PREFIX$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .apply { coverArtId?.let { setArtworkUri(android.net.Uri.parse(client.coverArtUrl(it))) } }
                .build(),
        )
        .build()

    /** Id Subsonic del brano, spogliato dal prefisso "playlist_track:$playlistId:" se presente. */
    private fun MediaItem.songId(): String = parseSongId(mediaId)

    /** Id della playlist di provenienza, se il mediaId è quello composito di un brano in playlist. */
    private fun MediaItem.playlistId(): String? = parsePlaylistId(mediaId)

    private fun MediaItem.withStreamUri(): MediaItem =
        buildUpon().setUri(client.streamUrl(songId())).build()

    private companion object {
        const val ROOT_ID = "root"
        const val HOME_ID = "home"
        const val ALBUMS_ID = "albums"
        const val PLAYLISTS_ID = "playlists"
        const val EXTRA_ALBUM_ID = "albumId"
        const val ALBUMS_FETCH_SIZE = 500 // limite massimo per chiamata di getAlbumList2 in Subsonic

        // Prefisso per distinguere, nel catch-all di onGetChildren, un parentId "playlist" da un
        // albumId grezzo. PLAYLIST_TRACK_MEDIA_ID_PREFIX (per i brani in playlist) è top-level,
        // sotto: serve anche fuori da questa classe (mobile, per il fetch delle lyrics).
        const val PLAYLIST_PREFIX = "playlist:"

        const val PREF_NAME = "playback_state"
        const val PREF_CONTAINER_TYPE = "containerType"
        const val PREF_CONTAINER_ID = "containerId"
        const val PREF_TRACK_ID = "trackId"
        const val PREF_POSITION_MS = "positionMs"
        const val CONTAINER_TYPE_ALBUM = "album"
        const val CONTAINER_TYPE_PLAYLIST = "playlist"

        const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
        const val BUFFER_LOG_TAG = "NavCarStereoBuffer"

        // Stessi valori di androidx.media.utils.MediaConstants — copiati come stringhe/int per non
        // aggiungere una dipendenza solo per due extra del protocollo MediaBrowser standard.
        const val CONTENT_STYLE_BROWSABLE_KEY = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val CONTENT_STYLE_GROUP_TITLE_KEY = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
        const val CONTENT_STYLE_GRID_ITEM = 2
    }
}

/**
 * Prefisso del mediaId composito di un brano mostrato dentro una playlist
 * ("playlist_track:$playlistId:$songId") — vedi [NavSong.toMediaItem] in [PlaybackService].
 * Top-level (non dentro la classe) perché serve anche al modulo mobile, es. per risalire
 * all'id Subsonic del brano corrente e fare il fetch delle lyrics.
 */
const val PLAYLIST_TRACK_MEDIA_ID_PREFIX = "playlist_track:"

/** Id Subsonic del brano, spogliato dal prefisso [PLAYLIST_TRACK_MEDIA_ID_PREFIX] se presente. */
fun parseSongId(mediaId: String): String =
    mediaId.takeIf { it.startsWith(PLAYLIST_TRACK_MEDIA_ID_PREFIX) }
        ?.removePrefix(PLAYLIST_TRACK_MEDIA_ID_PREFIX)
        ?.split(":", limit = 2)
        ?.getOrNull(1)
        ?: mediaId

/** Id della playlist di provenienza, se [mediaId] è quello composito di un brano in playlist. */
fun parsePlaylistId(mediaId: String): String? =
    mediaId.takeIf { it.startsWith(PLAYLIST_TRACK_MEDIA_ID_PREFIX) }
        ?.removePrefix(PLAYLIST_TRACK_MEDIA_ID_PREFIX)
        ?.split(":", limit = 2)
        ?.getOrNull(0)
