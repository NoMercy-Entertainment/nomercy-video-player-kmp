// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.device.FormFactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// The trailer player, against what the app's TrailerMobileUiPlugin actually
// passes.
//
// There are two players in the NoMercy client and this library had one. The
// second is not a smaller copy of the first: it is the same chrome answering
// three questions differently, and the questions are the test.
class VideoUiKindTest {

    private val trailer: ChromeButtons = ChromeButtons.forKind(VideoUiKind.Trailer)

    @Test
    fun aTrailerOffersSubtitlesAndNoOtherMenu() {
        assertTrue(trailer.subtitles)

        // `onEpisodesClick = { }`, `onQualityClick = { }`, `onAudioClick = { }`
        // in TrailerMobileUiPlugin — wired to nothing, because a trailer is one
        // file with one audio track. Absent here rather than wired to nothing.
        assertFalse(trailer.playlist)
        assertFalse(trailer.quality)
        assertFalse(trailer.audio)
        assertFalse(trailer.settings)
    }

    @Test
    fun aTrailerHasNowhereToGoNextAndNoChaptersToJump() {
        assertFalse(trailer.previousNext)
        assertFalse(trailer.chapters)
    }

    // `showCast = false` on the trailer's top bar. Casting a two-minute trailer
    // to a television is not a thing anybody wants, and offering it is a button
    // that opens a device picker for nothing.
    @Test
    fun aTrailerDoesNotOfferToCast() {
        assertFalse(trailer.cast)
    }

    // Somebody deciding whether to watch a trailer is asking exactly how long it
    // is, so the clock stays even though most of the row goes.
    @Test
    fun aTrailerStillSaysHowLongItIs() {
        assertTrue(trailer.time)
        assertTrue(trailer.playPause)
        assertTrue(trailer.fullscreen)
    }

    @Test
    // This used to assert the full player was the bare constructor, which was a
    // guard on the wrong thing: it proved adding the trailer kind changed
    // nothing, and what it locked in was a bar of seven controls with no menu on
    // it at all. He looked at that and said it was not his player, and he was
    // right — his site passes eight more buttons and the drop-in was giving
    // nobody any of them.
    //
    // So the guard stays and its subject moves. The full player is his site's
    // set, and the menus are named one by one because those four are what a
    // viewer means by the player.
    fun theFullPlayerIsTheOneHisSiteDraws() {
        val full: ChromeButtons = ChromeButtons.forKind(VideoUiKind.Full)

        assertEquals(ChromeButtons.nomercyWeb(), full)

        assertTrue(full.quality)
        assertTrue(full.subtitles)
        assertTrue(full.audio)
        assertTrue(full.playlist)
        assertTrue(full.chapters)
        assertTrue(full.pictureInPicture)
    }

    // And the library's own defaults are still the web library's, because that
    // is what responsive.ts says and a consumer building one by hand is asking
    // for that. The two being different is the whole point.
    @Test
    fun theBareDefaultsAreStillTheWebLibrarysOwn() {
        assertFalse(ChromeButtons().quality)
        assertFalse(ChromeButtons().subtitles)
        assertNotEquals(ChromeButtons(), ChromeButtons.forKind(VideoUiKind.Full))
    }

    // A trailer sits inside a page that already showed the poster. A pre-screen
    // over it would be the same picture twice with a second button to press,
    // which is why TrailerMobileUiPlugin.showPreScreen is a no-op.
    @Test
    fun aTrailerHasNoPreScreenEvenWhenAsked() {
        val plugin = VideoUiPlugin(
            VideoUiOptions(formFactor = FormFactor.Phone, kind = VideoUiKind.Trailer),
        )

        plugin.showPreScreen()

        assertFalse(plugin.isPreScreenVisible())
    }

    @Test
    fun theFullPlayerStillShowsOne() {
        val plugin = VideoUiPlugin(VideoUiOptions(formFactor = FormFactor.Phone))

        plugin.showPreScreen()

        assertTrue(plugin.isPreScreenVisible())
    }
}
