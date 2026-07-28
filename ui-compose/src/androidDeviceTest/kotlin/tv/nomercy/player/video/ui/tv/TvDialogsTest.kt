// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.pressKey
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tv.nomercy.player.video.tv.TvChromeCallbacks
import tv.nomercy.player.video.tv.TvChromeContent
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.tv.TvDialog
import tv.nomercy.player.video.tv.TvEpisode
import tv.nomercy.player.video.tv.TvTrack

// The pre-screen and the lists, on a television.
//
// The thing worth checking on a device is focus. A list that opens with nothing
// focused is a list a remote cannot move through, and that cannot be seen from
// reading the code.
class TvDialogsTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recording : TvChromeCallbacks {
        val calls: MutableList<String> = mutableListOf()
        override fun play() { calls += "play" }
        override fun pause() = Unit
        override fun togglePlay() = Unit
        override fun seek(seconds: Float) = Unit
        override fun overrideTime(seconds: Float?) = Unit
        override fun restart() { calls += "restart" }
        override fun next() = Unit
        override fun exitPlayer() = Unit
    }

    private val strings = TvChromeStrings()
    private val callbacks = Recording()
    private var opened: TvDialog? = null
    private var chosen: String? = null

    private val episodes = listOf(
        TvEpisode(id = "e1", title = "The First One"),
        TvEpisode(id = "e2", title = WATCHING, isCurrent = true),
        TvEpisode(id = "e3", title = "The Third One"),
    )

    private fun preScreen(content: TvChromeContent) {
        compose.setContent {
            TvPreScreen(
                content = content,
                callbacks = callbacks,
                strings = strings,
                onOpen = { opened = it },
            )
        }
    }

    @Test
    fun resumeTakesFocusSoOneMorePressPutsThePictureBack() {
        // Somebody who pressed back by accident wants exactly this, and it
        // should be one press away rather than a hunt.
        preScreen(TvChromeContent(item = TvChromeItem(title = FILM)))

        compose.onNodeWithContentDescription(strings.resume).assertIsFocused()
    }

    @Test
    fun pressingItResumes() {
        preScreen(TvChromeContent(item = TvChromeItem(title = FILM)))

        compose.onNodeWithContentDescription(strings.resume).performKeyInput { pressKey(Key.DirectionCenter) }

        assertEquals(listOf("play"), callbacks.calls)
    }

    @Test
    fun aFilmIsNotOfferedAnEpisodeList() {
        // A single entry opening onto itself is a row that does nothing, and on
        // a remote every row costs a press to get past.
        preScreen(TvChromeContent(item = TvChromeItem(title = FILM), episodes = episodes.take(1)))

        compose.onNodeWithContentDescription(strings.episodes).assertDoesNotExist()
    }

    @Test
    fun aSeriesIs() {
        preScreen(TvChromeContent(item = TvChromeItem(title = "A", show = FILM), episodes = episodes))

        compose.onNodeWithContentDescription(strings.episodes).assertIsDisplayed()
    }

    @Test
    fun openingTheListAsksTheChromeRatherThanDrawingIt() {
        preScreen(TvChromeContent(item = TvChromeItem(show = FILM), episodes = episodes))

        // Focused first, because Compose delivers a key event to whatever holds
        // focus and this row does not: resume does, which is the point of the
        // case above. A remote gets here by pressing down twice.
        compose.onNodeWithContentDescription(strings.episodes).requestFocus()
        compose.onNodeWithContentDescription(strings.episodes).performKeyInput { pressKey(Key.DirectionCenter) }

        assertEquals(TvDialog.Episodes, opened)
    }

    @Test
    fun subtitlesAreOfferedEvenWithNoneLoaded() {
        // The list is where searching online lives, and a film with no subtitles
        // is exactly when somebody wants that.
        preScreen(TvChromeContent(item = TvChromeItem(title = FILM)))

        compose.onNodeWithContentDescription(strings.subtitles).assertIsDisplayed()
    }

    @Test
    fun theEpisodeListOpensOnTheOneBeingWatched() {
        // In a long season the interesting entry is the one they are on, and
        // scrolling to it from the top is the whole interaction.
        compose.setContent {
            TvEpisodesDialog(episodes = episodes, onSelect = { chosen = it }, title = strings.episodes)
        }

        compose.onNodeWithContentDescription(WATCHING).assertIsFocused()
    }

    @Test
    fun choosingAnEpisodeNamesTheOneChosen() {
        compose.setContent {
            TvEpisodesDialog(episodes = episodes, onSelect = { chosen = it }, title = strings.episodes)
        }

        compose.onNodeWithContentDescription(WATCHING).performKeyInput { pressKey(Key.DirectionCenter) }

        assertEquals("e2", chosen)
    }

    @Test
    fun theCurrentTrackIsMarkedAsSuchRatherThanOnlyColoured() {
        // Colour alone is the one distinction a viewer with no colour vision
        // cannot make, and this is the row telling them what they are hearing.
        val tracks = listOf(
            TvTrack(id = "en", label = ENGLISH, isCurrent = true),
            TvTrack(id = "nl", label = "Nederlands"),
        )
        compose.setContent {
            TvTrackDialog(tracks = tracks, onSelect = { chosen = it }, title = strings.language, tag = AUDIO_TAG)
        }

        val node = compose.onNodeWithContentDescription(ENGLISH).fetchSemanticsNode()

        assertTrue(node.config[SemanticsProperties.Selected])
    }

    @Test
    fun aTrackListStillRendersWhenNothingIsSelectedYet() {
        // Every track off is an ordinary state for subtitles, and a list that
        // only worked with a current entry would be empty exactly then.
        val tracks = listOf(TvTrack(id = "en", label = ENGLISH), TvTrack(id = "nl", label = "Nederlands"))
        compose.setContent {
            TvTrackDialog(tracks = tracks, onSelect = { chosen = it }, title = strings.subtitles, tag = SUBTITLE_TAG)
        }

        compose.onNodeWithTag(SUBTITLE_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription(ENGLISH).assertIsDisplayed()
    }
}

private const val WATCHING = "The Second One"
private const val ENGLISH = "English"
private const val FILM = "Rail Wars"
