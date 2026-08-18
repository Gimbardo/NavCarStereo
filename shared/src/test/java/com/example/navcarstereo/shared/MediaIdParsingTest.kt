package com.example.navcarstereo.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaIdParsingTest {

    @Test
    fun `plain song id has no playlist`() {
        assertEquals("song123", parseSongId("song123"))
        assertNull(parsePlaylistId("song123"))
    }

    @Test
    fun `playlist track id extracts song and playlist ids`() {
        val mediaId = "playlist_track:playlistA:song123"

        assertEquals("song123", parseSongId(mediaId))
        assertEquals("playlistA", parsePlaylistId(mediaId))
    }

    @Test
    fun `song id containing colons survives the playlist prefix split`() {
        val mediaId = "playlist_track:playlistA:song:with:colons"

        assertEquals("song:with:colons", parseSongId(mediaId))
        assertEquals("playlistA", parsePlaylistId(mediaId))
    }
}
