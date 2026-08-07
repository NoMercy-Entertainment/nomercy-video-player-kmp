// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.FetchResponse

// What a script asks for, against what the plugin registered.
//
// The rest of the plugin's tests hand it four bytes that are not a font, so the
// name parser falls back to the filename and one name is all there ever was to
// register. That is exactly the case this defect hid behind: with a REAL font,
// the file reports a full name and a family that differ, a style line names one
// of the two, and registering only the winner drew No Game No Life's dialogue
// in a fallback face wide enough to run off the frame.
class FontNameRegistrationTest {

    private fun systemFont(name: String): ByteArray? = listOf(
        "C:/Windows/Fonts/$name",
        "/usr/share/fonts/truetype/$name",
        "/System/Library/Fonts/Supplemental/$name",
    ).map(::File).firstOrNull { it.isFile }?.readBytes()

    @Test
    fun aStyleNamingTheFamilyFindsAFontWhoseFullNameDiffers() = runTest {
        // Segoe UI Bold reports full name "Segoe UI Bold" and family "Segoe
        // UI", the same shape as Fontin Sans reporting "FontinSans-Bold" and
        // "Fontin Sans Rg". Not Arial: AssFontNames skips that name on purpose,
        // so a style asking for it fetches nothing and this would pass on an
        // empty pipeline.
        val bytes: ByteArray = systemFont(BOLD_FONT_FILE) ?: run {
            println("segoeuib.ttf not installed — skipping the real-font registration gate")
            return@runTest
        }

        val renderer = RecordingRenderer()
        val player = ComposedPlayer(
            backend = null,
            fetcher = { url, _ ->
                when (url) {
                    SUBTITLE_URL -> FetchResponse(status = OK, body = skeletonAss(FAMILY))
                    MANIFEST_URL -> FetchResponse(status = OK, body = """["segoeuib.ttf"]""")
                    FONT_URL -> FetchResponse(status = OK, bytes = bytes)
                    else -> FetchResponse(status = NOT_FOUND)
                }
            },
        )
        val plugin = SubtitlePlugin(renderer)
        player.setup(PlayerConfig())
        player.addPlugin(plugin)

        plugin.load(SUBTITLE_URL, MANIFEST_URL)

        // Read from the call log rather than the map: the map is what libass
        // holds NOW, and a reconcile arriving after the load would empty it
        // without saying that the registration never happened.
        assertTrue(
            renderer.calls.contains("addFont:$FAMILY"),
            "the style asked for $FAMILY and the calls were ${renderer.calls}",
        )
        assertTrue(
            renderer.calls.contains("addFont:$FULL_NAME"),
            "the full name was lost: ${renderer.calls}",
        )
    }
}

private const val OK = 200
private const val NOT_FOUND = 404
private const val SUBTITLE_URL = "https://media.example.test/show/1/subs/en.ass"
private const val MANIFEST_URL = "https://media.example.test/show/1/fonts/fonts.json"
private const val FONT_URL = "https://media.example.test/show/1/fonts/segoeuib.ttf"
private const val BOLD_FONT_FILE = "segoeuib.ttf"
private const val FAMILY = "Segoe UI"
private const val FULL_NAME = "Segoe UI Bold"
