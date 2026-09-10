// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.player.PlayerPhase
import tv.nomercy.player.video.ui.tv.TvChromeStrings
import tv.nomercy.player.video.ui.tv.tvChromeStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// The message channel's rules, and the strings it needs to state them.
//
// The chrome listened to two of wireFeedback's nine events, so it said nothing
// while an item loaded, nothing while a stream stalled, and nothing when a decode
// failed. Two of the three words it needed had no field at all, and the third had
// a field and no mapping — so `loading` read "Loading" in all seventy-nine locales
// that already carry the translation.
class ChromeMessageTest {

    @Test
    fun theThreeMessageWordsAreReadFromTheLocaleTable() {
        val dutch: TvChromeStrings = tvChromeStrings("nl")
        val english: TvChromeStrings = tvChromeStrings("en")

        // Each has to differ from the English, which is what proves the key is
        // being read rather than the data class default standing in.
        assertNotEquals(english.loading, dutch.loading, "loading is not read from the table")
        assertNotEquals(english.buffering, dutch.buffering, "buffering is not read from the table")
        assertNotEquals(english.error, dutch.error, "error is not read from the table")
    }

    @Test
    fun loadingAndBufferingAreDifferentSentences() {
        // One is the item arriving, the other is it running out mid-play. A
        // chrome that says the same thing for both leaves a viewer on a slow
        // connection unable to tell which is happening.
        val strings: TvChromeStrings = tvChromeStrings("en")

        assertNotEquals(strings.loading, strings.buffering)
    }

    @Test
    fun theEnglishTextIsTheWebs() {
        val strings: TvChromeStrings = tvChromeStrings("en")

        assertEquals("Loading…", strings.loading)
        assertEquals("Buffering…", strings.buffering)
        assertTrue(strings.error.startsWith("Something went wrong"), "error was ${strings.error}")
    }

    // The distinction the web keeps as `messageIsFeedback`. `playing` and `time`
    // end a buffering notice because those events mean the buffering ended; they
    // must not cut off a sentence the host asked for.
    // Reported on a phone: "if I pause and then seek I get to the fake
    // buffering state". A seek raises Waiting, and Playing — the only thing
    // that cleared the notice — never comes, because a paused player is not
    // going to start.
    @Test
    fun aPausedPlayerIsNotBuffering() {
        assertEquals(false, waitIsWorthAnnouncing(PlayerPhase.PAUSED))
    }

    // The notice still has to appear for every wait that IS one. Named rather
    // than counted: a predicate that answered false everywhere would satisfy
    // the test above on its own.
    @Test
    fun everyOtherPhaseStillAnnouncesTheWait() {
        val silent: List<PlayerPhase> = PlayerPhase.entries.filterNot { waitIsWorthAnnouncing(it) }

        assertEquals(listOf(PlayerPhase.PAUSED), silent)
    }

    @Test
    fun aHostMessageAndAFeedbackMessageAreNotTheSameKind() {
        val buffering = ChromeMessage("Buffering…", ChromeMessage.Kind.Feedback)
        val asked = ChromeMessage("Saved to your list", ChromeMessage.Kind.Host)

        assertNotEquals(buffering.kind, asked.kind)
    }
}
