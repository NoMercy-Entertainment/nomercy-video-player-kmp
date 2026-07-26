// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.FetchResponse
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val OK = 200
private const val NOT_FOUND = 404
private const val FRAME_WIDTH = 1920
private const val FRAME_HEIGHT = 1080
private const val SUBTITLE_URL = "https://media.example.test/show/1/subs/en.ass"
private const val MANIFEST_URL = "https://media.example.test/show/1/fonts/fonts.json"
private const val FONT_URL = "https://media.example.test/show/1/fonts/Skeleton.ttf"

// The whole pipeline, end to end, with only the network stood in for.
//
// The other gates each prove one link: the parsers read a manifest, the plugin
// fetches in the right order, the renderer rasterizes. This is the one that
// proves they were built to fit each other — a real player hosts a real plugin
// which fetches a real subtitle, reads the fonts it names, attaches real font
// bytes to a real libass, and draws visible pixels.
//
// It is the shape a consumer will actually assemble, which is why every piece is
// the production one. The fetcher is the exception, and only because a test that
// reached the internet would be a test that fails when the internet does.
class FullPipelineGateTest {

    // Any real font on the machine. Which one does not matter — what matters is
    // that real bytes go through the manifest, the fetch and into libass, since
    // a pipeline that quietly dropped them still renders in a fallback face.
    private fun systemFont(): File? = listOf(
        "/System/Library/Fonts/Supplemental",
        "/usr/share/fonts/truetype/dejavu",
        "/usr/share/fonts/truetype",
        "C:/Windows/Fonts",
    ).asSequence()
        .map(::File)
        .filter { it.isDirectory }
        .flatMap { it.walkTopDown().maxDepth(2) }
        .firstOrNull { it.extension.equals("ttf", ignoreCase = true) && it.length() > 0 }

    @Test
    fun aPlayerAPluginAndLibassDrawOneCueBetweenThem() = runTest {
        val reason: String? = AssRenderers.whyUnavailable()
        if (reason != null) {
            println("skipped: $reason")
            return@runTest
        }
        val font: File = systemFont() ?: run {
            println("skipped: no system font to feed the pipeline")
            return@runTest
        }

        val renderer: AssRenderer = assertNotNull(AssRenderers.create(AssPlatformContext()))
        val responses: Map<String, FetchResponse> = mapOf(
            SUBTITLE_URL to FetchResponse(status = OK, body = skeletonAss()),
            MANIFEST_URL to FetchResponse(status = OK, body = """["Skeleton.ttf"]"""),
            FONT_URL to FetchResponse(status = OK, bytes = font.readBytes()),
        )
        val player = ComposedPlayer(
            backend = null,
            fetcher = { url, _ -> responses[url] ?: FetchResponse(status = NOT_FOUND) },
        )
        player.setup(PlayerConfig())

        val plugin = SubtitlePlugin(renderer)
        player.addPlugin(plugin)

        try {
            val loaded: Boolean = plugin.load(SUBTITLE_URL, MANIFEST_URL)
            renderer.frameSize(FRAME_WIDTH, FRAME_HEIGHT)

            assertTrue(loaded, "the plugin could not load a subtitle it was handed")
            assertTrue(
                plugin.loadedFonts.contains(SKELETON_FONT),
                "the font the cue names never made it through: ${plugin.loadedFonts}",
            )

            val frame: AssFrame = assertNotNull(
                renderer.render(INSIDE_CUE_MILLIS),
                "the pipeline assembled and then drew nothing",
            )
            assertTrue(
                frame.images.any { image -> image.pixels.any { it.toInt() != 0 } },
                "the cue laid out but never rasterized: every image was transparent",
            )
        } finally {
            player.dispose()
        }
    }
}
