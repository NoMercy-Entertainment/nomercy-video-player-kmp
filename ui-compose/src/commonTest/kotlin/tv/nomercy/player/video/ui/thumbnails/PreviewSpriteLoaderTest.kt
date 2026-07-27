// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.thumbnails

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.cues.SpriteCue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Getting a sprite sheet from the server to something a chrome can scrub.
//
// The fetchers are the caller's on purpose: both files sit behind the same
// bearer token as the media itself, and a loader holding its own HTTP client
// would be a second place for auth to be got wrong. What is tested here is that
// this one path handles the failures — because a preview is the part of a player
// that is allowed to be missing, and it must go missing quietly.
class PreviewSpriteLoaderTest {

    @Test
    fun aSheetAndItsVttBecomeSomethingScrubbable() = runTest {
        val sprite: PreviewSprite? = loadPreviewSprite(
            spriteUrl = SHEET_URL,
            vttUrl = VTT_URL,
            fetch = SpriteFetchers(bytes = { SHEET_BYTES }, text = { REAL_SPRITE_VTT }),
            scope = this,
            openTiles = fakeTiles,
        )

        assertNotNull(sprite)
        assertEquals(4, sprite.frames.size)
        assertEquals(320, sprite.frameWidthPx)
        assertEquals(178, sprite.frameHeightPx)
        assertEquals(640, sprite.cueAt(25.0)?.x)
    }

    @Test
    fun bothFilesAreFetchedAtOnceRatherThanOneAfterTheOther() = runTest {
        // Two round trips to a self-hosted server over a home connection is a
        // visible wait before the first preview appears. Each fetcher announces
        // itself and then waits for the other, so a loader that ran them in
        // sequence — in either order — never finishes.
        val sheetStarted = CompletableDeferred<Unit>()
        val vttStarted = CompletableDeferred<Unit>()

        val sprite: PreviewSprite? = loadPreviewSprite(
            spriteUrl = SHEET_URL,
            vttUrl = VTT_URL,
            fetch = SpriteFetchers(
                bytes = {
                    sheetStarted.complete(Unit)
                    vttStarted.await()
                    SHEET_BYTES
                },
                text = {
                    vttStarted.complete(Unit)
                    sheetStarted.await()
                    REAL_SPRITE_VTT
                },
            ),
            scope = this,
            openTiles = fakeTiles,
        )

        assertNotNull(sprite)
    }

    @Test
    fun aSheetThatDoesNotArriveMeansNoPreview() = runTest {
        assertNull(
            loadPreviewSprite(
                spriteUrl = SHEET_URL,
                vttUrl = VTT_URL,
                fetch = SpriteFetchers(bytes = { null }, text = { REAL_SPRITE_VTT }),
                scope = this,
                openTiles = fakeTiles,
            ),
        )
    }

    @Test
    fun aVttThatDoesNotArriveMeansNoPreview() = runTest {
        // The sheet on its own is a picture with no idea where anything is in it.
        assertNull(
            loadPreviewSprite(
                spriteUrl = SHEET_URL,
                vttUrl = VTT_URL,
                fetch = SpriteFetchers(bytes = { SHEET_BYTES }, text = { null }),
                scope = this,
                openTiles = fakeTiles,
            ),
        )
    }

    @Test
    fun aVttWithNoCuesMeansNoPreview() = runTest {
        // A sheet generated for an item too short to have frames, or a VTT that
        // arrived truncated. Either way there is no frame to show, and handing
        // back a sprite that answers null to every time is a chrome drawing an
        // empty box under the scrubber for the whole title.
        assertNull(
            loadPreviewSprite(
                spriteUrl = SHEET_URL,
                vttUrl = VTT_URL,
                fetch = SpriteFetchers(bytes = { SHEET_BYTES }, text = { "WEBVTT" }),
                scope = this,
                openTiles = fakeTiles,
            ),
        )
    }

    @Test
    fun bytesThatAreNotAnImageMeanNoPreview() = runTest {
        // An HTML error page served with a 200, which is what a misconfigured
        // reverse proxy in front of a self-hosted server hands back.
        assertNull(
            loadPreviewSprite(
                spriteUrl = SHEET_URL,
                vttUrl = VTT_URL,
                fetch = SpriteFetchers(bytes = { SHEET_BYTES }, text = { REAL_SPRITE_VTT }),
                scope = this,
                openTiles = { _, _, _ -> null },
            ),
        )
    }

    @Test
    fun theCallersFetchersAreTheOnesUsed() = runTest {
        // Not a detail. Both files are behind the same bearer token as the media,
        // and the moment this reaches for its own client the preview breaks on
        // every authenticated server while working on the developer's.
        val asked: MutableList<String> = mutableListOf()

        loadPreviewSprite(
            spriteUrl = SHEET_URL,
            vttUrl = VTT_URL,
            fetch = SpriteFetchers(
                bytes = { url ->
                    asked.add(url)
                    SHEET_BYTES
                },
                text = { url ->
                    asked.add(url)
                    REAL_SPRITE_VTT
                },
            ),
            scope = this,
            openTiles = fakeTiles,
        )

        assertEquals(setOf(SHEET_URL, VTT_URL), asked.toSet())
    }
}

private val fakeTiles: (ByteArray, List<SpriteCue>, CoroutineScope) -> SpriteTileSource =
    { _, _, _ ->
        object : SpriteTileSource {
            override fun frame(index: Int): ImageBitmap? = null
            override fun release() = Unit
        }
    }

private const val SHEET_URL = "https://server.test/sprite.webp"
private const val VTT_URL = "https://server.test/sprite.vtt"
private val SHEET_BYTES = ByteArray(8) { it.toByte() }

private val REAL_SPRITE_VTT = """
WEBVTT

00:00:00.000 --> 00:00:10.000
sprite.webp#xywh=0,0,320,178

00:00:10.000 --> 00:00:20.000
sprite.webp#xywh=320,0,320,178

00:00:20.000 --> 00:00:30.000
sprite.webp#xywh=640,0,320,178

00:00:30.000 --> 00:00:40.000
sprite.webp#xywh=960,0,320,178
""".trimIndent()
