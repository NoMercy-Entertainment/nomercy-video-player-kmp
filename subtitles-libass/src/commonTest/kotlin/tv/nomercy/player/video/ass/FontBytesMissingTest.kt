// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.PlayerErrorEvent
import tv.nomercy.player.core.ports.FetchResponse
import tv.nomercy.player.core.player.PlayerConfig

// A fetcher that answers with text and no bytes, which is what the reference
// implementation did and what a consumer copying it will do.
//
// Every other test in this file hands the plugin bytes, so the path where a
// font arrives unreadable was never exercised: the track loaded, load() returned
// true, loadedFonts came back empty and the cue drew in a fallback face. Nothing
// in the suite disagreed, and nothing on screen did either.
class FontBytesMissingTest {

    @Test
    fun aFontWithNoBytesIsReportedRatherThanSkipped() {
        runTest {
            val errors: MutableList<String> = mutableListOf()
            val player = ComposedPlayer(
                backend = null,
                fetcher = { url, _ ->
                    when (url) {
                        SUBTITLE -> FetchResponse(status = OK, body = RAIL_ASS)
                        MANIFEST -> FetchResponse(status = OK, body = RAIL_FONT_MANIFEST)
                        // The font itself: a status of 200 and a body, and no
                        // bytes at all. This is the shape that shipped.
                        else -> FetchResponse(status = OK, body = "not the font")
                    }
                },
            )
            player.setup(PlayerConfig())
            player.on(CoreEvents.Warning) { error: PlayerErrorEvent -> errors += error.code }
            player.on(CoreEvents.PluginWarning) { error: PlayerErrorEvent -> errors += error.code }

            val plugin = SubtitlePlugin(RecordingRenderer(), null)
            player.addPlugin(plugin)

            assertTrue(plugin.load(SUBTITLE, MANIFEST), "the track itself should still load")
            assertEquals(emptyList<String>(), plugin.loadedFonts, "no face can be registered without bytes")
            assertTrue(
                errors.any { code -> code == "plugin:subtitle/font-unreadable" },
                "an unreadable font was dropped without a word; errors were $errors",
            )
        }
    }

    private companion object {
        const val OK = 200
        const val SUBTITLE = "https://example.test/rail.ass"
        const val MANIFEST = "https://example.test/fonts.json"
    }
}
