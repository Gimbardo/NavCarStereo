package com.example.navcarstereo.ui.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.navcarstereo.player.PlaybackUiState
import org.junit.Rule
import org.junit.Test

/** Non tocca lyrics/rete: nessun brano corrente -> niente fetch, si testano solo i controlli. */
class NowPlayingSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: PlaybackUiState = PlaybackUiState(),
        onTogglePlayPause: () -> Unit = {},
        onSkipPrevious: () -> Unit = {},
        onSkipNext: () -> Unit = {},
        onToggleShuffle: () -> Unit = {},
        onCycleRepeatMode: () -> Unit = {},
    ) {
        composeRule.setContent {
            NowPlayingSheet(
                state = state,
                onTogglePlayPause = onTogglePlayPause,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeatMode = onCycleRepeatMode,
                onSeekTo = {},
            )
        }
    }

    @Test
    fun tappingPlayPauseInvokesCallback() {
        var invoked = false
        setContent(onTogglePlayPause = { invoked = true })

        composeRule.onNodeWithContentDescription("Play").performClick()

        assert(invoked)
    }

    @Test
    fun tappingSkipNextAndPreviousInvokeCallbacks() {
        var nextInvoked = false
        var previousInvoked = false
        setContent(onSkipNext = { nextInvoked = true }, onSkipPrevious = { previousInvoked = true })

        composeRule.onNodeWithContentDescription("Successivo").performClick()
        composeRule.onNodeWithContentDescription("Precedente").performClick()

        assert(nextInvoked)
        assert(previousInvoked)
    }

    @Test
    fun tappingShuffleAndRepeatInvokeCallbacks() {
        var shuffleInvoked = false
        var repeatInvoked = false
        setContent(onToggleShuffle = { shuffleInvoked = true }, onCycleRepeatMode = { repeatInvoked = true })

        composeRule.onNodeWithContentDescription("Shuffle").performClick()
        composeRule.onNodeWithContentDescription("Repeat").performClick()

        assert(shuffleInvoked)
        assert(repeatInvoked)
    }

    @Test
    fun lyricsToggleSwapsAlbumArtWithoutTouchingPlaybackControls() {
        setContent()

        composeRule.onNodeWithContentDescription("Testi").performClick()

        // Nessun brano in riproduzione -> niente lyrics da mostrare, ma i controlli restano visibili.
        composeRule.onNodeWithContentDescription("Play").assertExists()
        composeRule.onNodeWithContentDescription("Successivo").assertExists()
    }
}
