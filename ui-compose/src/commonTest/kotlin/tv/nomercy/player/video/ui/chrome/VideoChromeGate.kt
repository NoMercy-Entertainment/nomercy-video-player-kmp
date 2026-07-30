// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.ui.chrome.menus.SETTINGS_MENU_TAG
import tv.nomercy.player.video.ui.tv.PROGRESS_TAG
import tv.nomercy.player.video.ui.tv.TvChromeStrings
import kotlin.test.Test
import kotlin.test.assertEquals

// The whole touch chrome, over a real player.
//
// Nothing here asserts that a composable rendered on its own. Every case drives
// the assembly the way a viewer drives it and then asks the engine what
// happened, because the widgets already have their own tests and what is
// untested until now is the wiring between them: one controller, one read model,
// one set of commands, and a form factor deciding which surface gets built.
//
// Abstract and subclassed per host, the same shape as PlayerControlsGate. The
// Android host runs under Robolectric and needs its runner named on the class,
// which cannot be done from common code.
@OptIn(ExperimentalTestApi::class)
abstract class VideoChromeGate {

    private val strings = TvChromeStrings()

    private fun player(): NMVideoPlayer = NMVideoPlayer(RecordingVideoBackend())

    private fun ComposeUiTest.mount(
        player: NMVideoPlayer,
        formFactor: FormFactor = FormFactor.Phone,
        buttons: ChromeButtons = ChromeButtons(),
    ) {
        setContent {
            // Set up inside the composition, which is where an app sets it up.
            // The item matters: the chrome reads what is playing, and a player
            // with an empty queue would leave every content assertion measuring
            // a placeholder.
            LaunchedEffect(player) {
                player.setup(PlayerConfig())
                player.queue(listOf(ChromeTestItem()))
                player.load(ChromeTestItem())
            }

            // A stated width, not the host's.
            //
            // The bar drops controls that do not fit, so what is on screen
            // depends on how wide the window is — and the window is whatever
            // each host happens to give a test. This mounted bare and passed on
            // Compose Desktop while failing on Robolectric, whose default
            // device is narrower: the subtitles button is ranked sixteenth and
            // did not fit there, so a case about opening its menu could not
            // find the button. Nothing about that test is about width.
            //
            // Plain width, and the HOST is what carries the number. A
            // requiredWidth ignores the constraints it is handed, which sounds
            // like the fix and puts the bar outside the window instead — the
            // nodes then exist and are neither displayed nor clickable. So the
            // Android host declares a wide device through a Robolectric
            // qualifier, and this box only has to not shrink it.
            //
            // ChromeWidthGate is where the width rule is graded, deliberately.
            Box(modifier = Modifier.width(MOUNT_WIDTH.dp).height(MOUNT_HEIGHT.dp)) {
                VideoChrome(player, formFactor, buttons = buttons, strings = strings)
            }
        }
        waitForIdle()

        // The autohide is four seconds, and a test clock left to run advances
        // through it while waiting for a recomposition — so the controls an
        // assertion is about would be gone before it looked. Stopped here and
        // stepped deliberately below.
        mainClock.autoAdvance = false
    }

    // Long enough for a fade to finish and for a click's coroutine to reach the
    // engine, and nowhere near the four seconds that would hide the controls.
    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
    }

    // For the assertions the autohide cannot touch, because what they are about
    // is drawn outside it: a message and a menu stay up whether or not the
    // controls have gone.
    //
    // They need the clock running, and that is a finding rather than a
    // preference. On the Android host the frame that carries a recomposition
    // comes from the test clock, so a stopped one leaves a snapshot write
    // invisible for the rest of the test — these two passed on the desktop host
    // and failed here for exactly that reason.
    private fun ComposeUiTest.settleWithTheClockRunning() {
        mainClock.autoAdvance = true
        waitForIdle()
    }

    @Test
    fun aPausedFilmKeepsItsControlsOnScreen() = runComposeUiTest {
        // Rule four of the autohide, at the assembly rather than in the state
        // machine's own test: a paused film with nothing over it is a still
        // image, and there is no way to tell it apart from a player that died.
        mount(player())

        onNodeWithTag(TOUCH_CHROME_TAG).assertExists()
        onNodeWithTag(TRANSPORT_BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun pressingPlayStartsTheEngine() = runComposeUiTest {
        val player = player()
        mount(player)

        onNodeWithTag(PLAY_PAUSE_TAG).performClick()
        settle()

        assertEquals(PlayState.PLAYING, player.state().playState)
    }

    @Test
    fun andTheEngineDrivesTheGlyphBack() = runComposeUiTest {
        // The half that a remembered flag would fake. The button has to follow
        // what the engine is doing, not what it was last asked to do.
        val player = player()
        mount(player)

        onNodeWithTag(PLAY_PAUSE_TAG).performClick()
        settle()

        onNodeWithContentDescription(strings.pause).assertExists()
    }

    @Test
    fun theScrubberIsThereToDragAlong() = runComposeUiTest {
        mount(player())

        onNodeWithTag(SCRUBBER_TAG).assertExists()
    }

    @Test
    fun andItIsTheChromesOwnBarThatIsDrawn() = runComposeUiTest {
        // Two ChapterProgressBar composables exist and the scrubber drew the
        // television's, so every fix to the segmented drawing landed on a copy this
        // never renders. Nothing could say so: only the tv copy had a tag, which
        // left every check grading the file the fix was applied to rather than the
        // thing on screen.
        mount(player())

        onNodeWithTag(CHAPTER_BAR_TAG).assertExists()
        onNodeWithTag(PROGRESS_TAG).assertDoesNotExist()
    }

    @Test
    fun aDragLetGoOffTheStripStillSeeks() = runComposeUiTest {
        // `finalizeScrub` is bound on `document`, not on the bar. A viewer who
        // drags along the strip, wanders off it and lets go there has still chosen
        // a place, and dropping the seek would leave the film where it was with a
        // bubble showing where they meant to go.
        val backend = RecordingVideoBackend()
        mount(NMVideoPlayer(backend))

        onNodeWithTag(SCRUBBER_TAG).performTouchInput {
            down(centerLeft)
            moveTo(center)
            moveTo(Offset(center.x, height * OFF_THE_STRIP))
            up()
        }
        settle()

        assertEquals(
            listOf(MIDPOINT_SECONDS),
            backend.seeks,
            "a drag released off the strip did not commit at the position it was let go",
        )
    }

    @Test
    fun aPressOnTheBarSeeksToWhereItLanded() = runComposeUiTest {
        // A click on `.slider-bar` is a mousedown and a mouseup with no travel in
        // between, and `finalizeScrub` seeks to where the pointer was released.
        // detectDragGestures never fires for that, so pressing the bar here did
        // nothing at all — the one gesture every viewer tries first.
        val backend = RecordingVideoBackend()
        mount(NMVideoPlayer(backend))

        onNodeWithTag(SCRUBBER_TAG).performTouchInput {
            down(center)
            up()
        }
        settle()

        assertEquals(listOf(MIDPOINT_SECONDS), backend.seeks, "a press on the bar seeked nowhere")
    }

    @Test
    fun theTopBarNamesWhatIsPlaying() = runComposeUiTest {
        mount(player())

        onNodeWithText(ChromeTestItem().title).assertExists()
    }

    @Test
    fun aTouchBuildGetsTheTapZones() = runComposeUiTest {
        mount(player(), FormFactor.Phone)

        onNodeWithTag(ZONE_CENTRE).assertExists()
    }

    @Test
    fun aDesktopBuildDoesNot() = runComposeUiTest {
        // A pointer has a button for everything the zones offer, and three
        // invisible columns over the picture would swallow clicks meant for
        // whatever the host drew underneath.
        mount(player(), FormFactor.Desktop)

        onNodeWithTag(ZONE_CENTRE).assertDoesNotExist()
    }

    @Test
    fun openingTheSubtitleMenuPutsAListOnScreen() = runComposeUiTest {
        mount(player(), buttons = ChromeButtons(subtitles = true))

        onNodeWithContentDescription(strings.subtitles).performClick()
        settleWithTheClockRunning()

        onNodeWithTag(SETTINGS_MENU_TAG).assertExists()
    }

    @Test
    fun aMessageFromThePlayerReachesTheViewer() = runComposeUiTest {
        // The key handler says "2x" through the player rather than through the
        // chrome, so this is the only path a speed change has to the screen.
        val player = player()
        mount(player)

        player.message(SPEED_MESSAGE)
        settleWithTheClockRunning()

        onNodeWithText(SPEED_MESSAGE).assertExists()
    }

    @Test
    fun andGoesAwayWhenThePlayerClearsIt() = runComposeUiTest {
        val player = player()
        mount(player)

        player.message(SPEED_MESSAGE)
        settleWithTheClockRunning()
        player.clearMessage()
        settleWithTheClockRunning()

        onNodeWithText(SPEED_MESSAGE).assertDoesNotExist()
    }
}

private const val SETTLE_MS = 500L
private const val SPEED_MESSAGE = "2.0x"

// Halfway along a strip that spans a 120-second fixture, which is what the middle
// of the bar means. Written as the answer rather than the arithmetic so a seek
// landing anywhere else names a number rather than a formula.
private const val MIDPOINT_SECONDS = 60.0

// Far enough below the strip that the pointer is nowhere near it when it comes up.
private const val OFF_THE_STRIP = 4f

// Wide enough that every control a case asks for is on the bar, so a case about
// a menu is about that menu.
private const val MOUNT_WIDTH = 1280
private const val MOUNT_HEIGHT = 720
