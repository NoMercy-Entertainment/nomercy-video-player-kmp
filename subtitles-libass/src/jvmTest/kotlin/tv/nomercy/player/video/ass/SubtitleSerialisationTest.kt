// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.FetchResponse
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

// A renderer that notices being entered twice at once, on real threads.
//
// This lives in jvmTest and not in commonTest, and that placement is the whole
// finding. Two attempts at this in commonTest both passed with the lock
// deliberately removed: `runTest` drives a single dispatcher, so a block that
// does not suspend cannot be interleaved whether or not a mutex guards it. The
// gate could not observe the bug it existed for, twice, in two different shapes.
//
// Real threads can. The counter is incremented on entry and decremented on
// exit; anything above one at any moment is two callers inside libass, which in
// native code is a crash rather than a wrong pixel.
private class ConcurrencyGuardRenderer : AssRenderer {

    private val inside = AtomicInteger(0)

    @Volatile
    var overlaps: Int = 0
        private set

    private fun <T> guarded(body: () -> T): T {
        if (inside.incrementAndGet() > 1) overlaps++
        try {
            // Long enough that an unserialised caller genuinely lands inside
            // this window. Without it the section is too short to collide and
            // the test goes quiet again.
            Thread.sleep(1)
            return body()
        } finally {
            inside.decrementAndGet()
        }
    }

    override fun addFont(name: String, data: ByteArray): Unit = guarded { }

    override fun clearFonts(): Unit = Unit

    override fun loadTrack(assContent: String): Unit = guarded { }

    override fun frameSize(width: Int, height: Int): Unit = guarded { }

    override fun render(timeMillis: Long): AssFrame? = guarded { null }

    override fun release() = Unit
}

// libass is not reentrant. The plugin is the only place that can order a font
// arriving on one thread against a track loading on another.
class SubtitleSerialisationTest {

    @Test
    fun nativeCallsNeverOverlapUnderRealConcurrency() = runBlocking {
        val renderer = ConcurrencyGuardRenderer()
        val player = ComposedPlayer(
            backend = null,
            fetcher = { url, _ ->
                when {
                    url.endsWith(".ass") -> FetchResponse(status = OK, body = ASS)
                    url.endsWith(".json") -> FetchResponse(status = OK, body = """["A.ttf"]""")
                    else -> FetchResponse(status = OK, bytes = byteArrayOf(1, 2, 3))
                }
            },
        )
        player.setup(PlayerConfig())
        val plugin = SubtitlePlugin(renderer)
        player.addPlugin(plugin)

        // Dispatchers.Default, so these genuinely run on different threads.
        // This is what commonTest cannot do and why that version was silent.
        withContext(Dispatchers.Default) {
            val work = buildList {
                add(async { plugin.load(SUBTITLE, MANIFEST) })
                repeat(CONCURRENT) { index ->
                    add(async { plugin.addFontLate("Font$index.ttf", byteArrayOf(index.toByte())) })
                }
            }
            work.awaitAll()
        }

        assertEquals(0, renderer.overlaps, "two callers were inside libass at once ${renderer.overlaps} times")
    }
}

private const val OK = 200
private const val CONCURRENT = 24
private const val SUBTITLE = "https://media.example.test/ep/rail.ass"
private const val MANIFEST = "https://media.example.test/ep/fonts.json"
private val ASS = """
    [Script Info]
    Title: rail

    [V4+ Styles]
    Format: Name, Fontname
    Style: Default,Skeleton Sans

    [Events]
    Format: Layer, Start, End, Style, Text
    Dialogue: 0,0:00:01.00,0:00:03.00,Default,hello
""".trimIndent()
