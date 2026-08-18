package com.example.navcarstereo.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class NextRepeatModeTest {

    @Test
    fun `cycles off to all to one and back to off`() {
        assertEquals(Player.REPEAT_MODE_ALL, nextRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, nextRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, nextRepeatMode(Player.REPEAT_MODE_ONE))
    }
}
