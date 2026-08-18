package com.example.navcarstereo.shared.navidrome

data class NavidromeConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
)

data class NavAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val coverArtId: String?,
    val songCount: Int,
)

data class NavSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String?,
    val track: Int?,
    val coverArtId: String?,
    val durationSeconds: Int?,
)

data class NavPlaylist(
    val id: String,
    val name: String,
    val songCount: Int,
    val coverArtId: String?,
)

data class NavLyricLine(
    /** null se le lyrics non sono sincronizzate: la riga va mostrata senza auto-scroll. */
    val startMs: Long?,
    val text: String,
)

data class NavLyrics(val lines: List<NavLyricLine>)
