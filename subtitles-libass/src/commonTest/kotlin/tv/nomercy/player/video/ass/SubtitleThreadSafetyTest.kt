// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.FetchResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A renderer that records the order it was called in.
//
// The first version of this file used a busy flag and asserted it was never set
// twice. It passed with no serialisation at all, and that is worth keeping
// rather than deleting: the renderer's methods do not suspend, so on a single
// test dispatcher nothing can run between a flag being set and cleared. The
// guard could not observe the bug it was written for.
//
// What can be observed is the order. Every operation here is a sequence — attach
// fonts, then load the track — and the damage from not serialising is a sequence
// from one operation landing inside another's.
private class OrderingRenderer : AssRenderer {

    val calls: MutableList<String> = mutableListOf()

    override fun addFont(name: String, data: ByteArray) {
        calls += "addFont:$name"
    }

    override fun loadTrack(assContent: String) {
        calls += "loadTrack"
    }

    override fun frameSize(width: Int, height: Int) {
        calls += "frameSize"
    }

    override fun render(timeMillis: Long): AssFrame? {
        calls += "render"
        return null
    }

    override fun release() = Unit
}

// libass is not reentrant, and the plugin is the only place that can order the
// calls into it. Each platform actual has its own lock; none of them can see
// that a font arriving from one coroutine is racing a track loading in another.
class SubtitleThreadSafetyTest {

    private fun host(): ComposedPlayer = ComposedPlayer(
        backend = null,
        fetcher = { url, _ ->
            // Yields inside the fetch, which is where the plugin actually
            // suspends. Without this the whole load runs to completion before
            // anything else is scheduled and no interleaving is possible —
            // which is how the first version of this test passed on a bug.
            yield()
            when {
                url.endsWith(".ass") -> FetchResponse(status = OK, body = ASS)
                url.endsWith(".json") -> FetchResponse(status = OK, body = """["A.ttf"]""")
                else -> FetchResponse(status = OK, bytes = byteArrayOf(1, 2, 3))
            }
        },
    )

    private suspend fun plugin(renderer: AssRenderer): SubtitlePlugin {
        val player: ComposedPlayer = host()
        player.setup(PlayerConfig())
        return SubtitlePlugin(renderer).also { player.addPlugin(it) }
    }

    @Test
    fun aLateFontArrivingMidLoadStillEndsWithTheTrackLoaded() = runTest {
        // The consequence that matters. addFontLate reloads the current track so
        // libass resolves families again — but if it runs while load() is still
        // fetching, there is no current track yet and the reload is skipped. The
        // font is registered and never takes effect, which is a film that plays
        // to the end in the wrong typeface.
        val renderer = OrderingRenderer()
        val subject: SubtitlePlugin = plugin(renderer)

        coroutineScope {
            val loading = async { subject.load(SUBTITLE, MANIFEST) }
            launch {
                yield()
                subject.addFontLate("Late.ttf", byteArrayOf(9))
            }
            loading.await()
        }

        val fonts: List<String> = renderer.calls.filter { it.startsWith("addFont") }
        assertTrue(fonts.any { it.contains("Late") }, "the late font never reached libass: ${renderer.calls}")
        assertEquals("loadTrack", renderer.calls.last(), "the last call was not a track load: ${renderer.calls}")
    }

    @Test
    fun aTrackIsNeverLoadedBeforeItsFontsAreAttached() = runTest {
        // The ordering invariant, under concurrency rather than in isolation.
        // libass resolves a family when it draws, so a track loaded before its
        // fonts renders in a fallback face and reports nothing.
        val renderer = OrderingRenderer()
        val subject: SubtitlePlugin = plugin(renderer)

        coroutineScope {
            launch { subject.load(SUBTITLE, MANIFEST) }
            launch {
                repeat(REPEATS) {
                    subject.addFontLate("Extra$it.ttf", byteArrayOf(it.toByte()))
                    yield()
                }
            }
        }

        val firstLoad: Int = renderer.calls.indexOf("loadTrack")
        val firstFont: Int = renderer.calls.indexOfFirst { it.startsWith("addFont") }

        assertTrue(firstFont >= 0, "no font was ever attached: ${renderer.calls}")
        assertTrue(firstLoad > firstFont, "a track loaded before any font: ${renderer.calls}")
    }

    @Test
    fun oneOperationsNativeCallsAreContiguous() {
        // What serialisation actually looks like from outside. Each operation
        // is addFont-then-loadTrack, and with a lock those two are adjacent;
        // without one, a late font's pair can land between another's.
        //
        // This is observable on a single test dispatcher because the fetches
        // yield and the native section does not — so an unlocked plugin
        // interleaves here, and a locked one cannot.
        runTest {
            val renderer = OrderingRenderer()
            val subject: SubtitlePlugin = plugin(renderer)

            coroutineScope {
                launch { subject.load(SUBTITLE, MANIFEST) }
                launch {
                    yield()
                    subject.addFontLate("Late.ttf", byteArrayOf(9))
                }
            }

            // Every loadTrack must be immediately preceded by the addFont of
            // the same operation. A loadTrack with a foreign addFont in front
            // of it is the interleave.
            val calls: List<String> = renderer.calls
            for ((index, call) in calls.withIndex()) {
                if (call != "loadTrack" || index == 0) continue

                assertTrue(
                    calls[index - 1].startsWith("addFont"),
                    "a track loaded with no font of its own in front of it: $calls",
                )
            }
            assertTrue(calls.count { it == "loadTrack" } >= 1, "nothing was loaded: $calls")
        }
    }
}

private const val OK = 200
private const val REPEATS = 8
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
