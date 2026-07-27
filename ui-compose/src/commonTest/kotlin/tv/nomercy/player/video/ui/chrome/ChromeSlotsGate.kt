// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

// Replacing a piece of the chrome without leaving it.
//
// The claim is guidance rather than walls, and the two halves of it are here: a
// host that supplies nothing gets the shipped control, and a host that supplies
// one gets theirs INSTEAD — still handed the same commands, so it drives the
// engine through the same seam and cannot drift into reading the player itself.
@OptIn(ExperimentalTestApi::class)
abstract class ChromeSlotsGate {

    private fun ComposeUiTest.mount(slots: ChromeSlots, provided: ChromeSlots? = null): NMVideoPlayer {
        val player = NMVideoPlayer(RecordingVideoBackend())

        setContent {
            LaunchedEffect(player) {
                player.setup(PlayerConfig())
                player.queue(listOf(ChromeTestItem()))
                player.load(ChromeTestItem())
            }

            CompositionLocalProvider(LocalChromeSlots provides (provided ?: ChromeSlots())) {
                if (provided == null) {
                    VideoChrome(player, FormFactor.Phone, slots = slots)
                } else {
                    VideoChrome(player, FormFactor.Phone)
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false

        return player
    }

    private fun ownTransport(): ChromeSlots = ChromeSlots(
        transport = { state, commands ->
            Box(
                modifier = Modifier
                    .size(BUTTON_SIZE)
                    .testTag(OWN_TRANSPORT)
                    .clickable { commands.setPlaying(!state.playing) },
            )
        },
    )

    @Test
    fun withNoSlotTheShippedTransportIsThere() = runComposeUiTest {
        mount(ChromeSlots())

        onNodeWithTag(TRANSPORT_BAR_TAG).assertExists()
    }

    @Test
    fun aSuppliedOneReplacesItRatherThanJoiningIt() = runComposeUiTest {
        // Instead of, not beside. Two transport rows is what a slot that merely
        // added would give, and the shipped one underneath would still be taking
        // presses meant for the host's.
        mount(ownTransport())

        onNodeWithTag(OWN_TRANSPORT).assertExists()
        onNodeWithTag(TRANSPORT_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun andStillDrivesTheEngine() = runComposeUiTest {
        val player = mount(ownTransport())

        onNodeWithTag(OWN_TRANSPORT).performClick()
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()

        assertEquals(PlayState.PLAYING, player.state().playState)
    }

    @Test
    fun theSlotsCanBeFurnishedFromAboveTheCallSite() = runComposeUiTest {
        // The thing wanting to override is usually a layer or two above the
        // thing mounting the player, and threading a parameter down is where one
        // call site quietly keeps the default.
        mount(ChromeSlots(), provided = ownTransport())

        onNodeWithTag(OWN_TRANSPORT).assertExists()
    }

    @Test
    fun anOverlaySlotIsAddedRatherThanSubstituted() = runComposeUiTest {
        // The other half of the rule. A skip-intro button is the host's feature,
        // and a slot that replaced the chrome to draw one would make a host
        // choose between their feature and the controls.
        mount(ChromeSlots(overlays = { _, _ -> Box(Modifier.size(BUTTON_SIZE).testTag(OWN_OVERLAY)) }))

        onNodeWithTag(OWN_OVERLAY).assertExists()
        onNodeWithTag(TRANSPORT_BAR_TAG).assertExists()
    }
}

private const val OWN_TRANSPORT = "host-transport"
private const val OWN_OVERLAY = "host-overlay"
private const val SETTLE_MS = 500L
private val BUTTON_SIZE = 48.dp
