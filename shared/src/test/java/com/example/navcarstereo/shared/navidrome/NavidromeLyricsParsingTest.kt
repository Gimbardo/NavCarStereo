package com.example.navcarstereo.shared.navidrome

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NavidromeLyricsParsingTest {

    @Test
    fun `synced lyrics keep line timestamps`() {
        val json = JSONObject(
            """{"synced": true, "line": [{"start": 1000, "value": "prima riga"}, {"start": 2000, "value": "seconda riga"}]}""",
        )

        val lyrics = json.toLyrics()

        assertEquals(
            listOf(NavLyricLine(1000L, "prima riga"), NavLyricLine(2000L, "seconda riga")),
            lyrics.lines,
        )
    }

    @Test
    fun `unsynced lyrics have null timestamps`() {
        val json = JSONObject("""{"synced": false, "line": [{"value": "riga singola"}]}""")

        val lyrics = json.toLyrics()

        assertEquals(listOf(NavLyricLine(null, "riga singola")), lyrics.lines)
    }

    @Test
    fun `missing line array yields empty lyrics`() {
        val json = JSONObject("""{"synced": true}""")

        val lyrics = json.toLyrics()

        assertEquals(emptyList<NavLyricLine>(), lyrics.lines)
    }
}
