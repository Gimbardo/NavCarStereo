package com.example.navcarstereo.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumLetterTest {

    @Test
    fun `groups by first alphanumeric char, accents stripped, rest in hash`() {
        assertEquals("T", albumLetter("“The Spaghetti Incident?”"))
        assertEquals("U", albumLetter("¡Uno! (96kHz/24bit)"))
        assertEquals("E", albumLetter("Èrgo"))
        assertEquals("M", albumLetter("moral panic"))
        assertEquals("#", albumLetter("2nd Law"))
        assertEquals("#", albumLetter("…"))
    }
}
