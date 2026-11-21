// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlinx.coroutines.runBlocking
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.FetchResponse
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val OK = 200
private const val NOT_FOUND = 404
private const val TRACK_CHARS = 72_000_000
private const val FIRST_URL = "https://media.example.test/show/1/subs/eng.full.ass"
private const val SECOND_URL = "https://media.example.test/show/2/subs/eng.full.ass"
private const val MANIFEST_URL = "https://media.example.test/show/1/fonts/fonts.json"
private const val GC_ATTEMPTS = 20
private const val GC_PAUSE_MS = 50L

private const val DIALOGUE =
    "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\\fnTestSans\\pos(960,540)\\blur2}typeset line %06d\n"

// The size of the real tracks comes from the event count, not from any one line.
private fun largeScript(): String {
    val head = "[Script Info]\nPlayResX: 1920\nPlayResY: 1080\n\n[V4+ Styles]\n" +
        "Style: Default,TestSans,48,&H00FFFFFF\n\n[Events]\n" +
        "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
    val builder = StringBuilder(TRACK_CHARS + DIALOGUE.length)
    builder.append(head)
    var index = 0
    while (builder.length < TRACK_CHARS) builder.append(DIALOGUE.format(index++))
    return builder.toString()
}

// A reference still there after every attempt is a copy something is holding.
private fun isCollected(ref: WeakReference<*>): Boolean {
    repeat(GC_ATTEMPTS) {
        System.gc()
        if (ref.get() == null) return true
        Thread.sleep(GC_PAUSE_MS)
    }
    return false
}

// Keeps the size of what it was handed and a weak reference to the text.
private class SinkRenderer : AssRenderer {
    var loadedLength: Int = 0
        private set
    var received: WeakReference<String>? = null
        private set

    override fun addFont(name: String, data: ByteArray): Unit = Unit
    override fun clearFonts(): Unit = Unit

    override fun loadTrack(assContent: String) {
        loadedLength = assContent.length
        received = if (assContent.isEmpty()) null else WeakReference(assContent)
    }

    override fun storageSize(width: Int, height: Int): Unit = Unit
    override fun storageSize(): AssSize? = null
    override fun frameSize(width: Int, height: Int): Unit = Unit
    override fun render(timeMillis: Long): AssFrame? = null
    override fun hasTrack(): Boolean = loadedLength > 0
    override fun release(): Unit = Unit
}

// Takes the track as bytes, the way the native renderers do, and keeps it.
private class ByteSinkRenderer : AssRenderer, ByteTrackRenderer {
    var held: ByteArray? = null
        private set
    var handOvers: MutableList<ByteArray> = mutableListOf()
        private set
    var stringLoads: Int = 0
        private set

    override fun addFont(name: String, data: ByteArray): Unit = Unit
    override fun clearFonts(): Unit = Unit

    override fun loadTrack(assContent: String) {
        if (assContent.isEmpty()) held = null else stringLoads++
    }

    override fun loadTrackBytes(content: ByteArray) {
        held = content
        handOvers += content
    }

    override fun storageSize(width: Int, height: Int): Unit = Unit
    override fun storageSize(): AssSize? = null
    override fun frameSize(width: Int, height: Int): Unit = Unit
    override fun render(timeMillis: Long): AssFrame? = null
    override fun release(): Unit = Unit
}

// A 72 MB track is a 144 MB String on a UTF-16 runtime, and a 384 MB heap holds
// two of those only if nothing keeps the previous one alive.
class LargeTrackMemoryTest {

    private val bodies: MutableMap<String, WeakReference<String>> = mutableMapOf()
    private var firstBodyCollectedAtSecondFetch: Boolean? = null

    private fun player(): ComposedPlayer = ComposedPlayer(
        backend = null,
        fetcher = { url, _ ->
            when (url) {
                FIRST_URL, SECOND_URL -> {
                    if (url == SECOND_URL) firstBodyCollectedAtSecondFetch = isCollected(bodies.getValue(FIRST_URL))
                    val body: String = largeScript()
                    bodies[url] = WeakReference(body)
                    FetchResponse(status = OK, body = body)
                }
                else -> FetchResponse(status = NOT_FOUND)
            }
        },
    )

    @Test
    fun noCopyOfATrackSurvivesItsLoadAndTheNextFetchStartsOnAnEmptyHeap(): Unit = runBlocking {
        val renderer = SinkRenderer()
        val player = player()
        val plugin = SubtitlePlugin(renderer)
        player.setup(PlayerConfig())
        player.addPlugin(plugin)

        // Positive control: the detector must be able to say "still held".
        val held: String = largeScript()
        val heldRef = WeakReference(held)

        assertTrue(plugin.load(FIRST_URL, MANIFEST_URL), "the first track did not load")
        assertTrue(renderer.loadedLength >= TRACK_CHARS, "the renderer was not handed the whole track")
        assertTrue(renderer.hasTrack(), "the renderer holds no track after a successful load")

        assertTrue(isCollected(bodies.getValue(FIRST_URL)), "the downloaded body is still held after the parse")
        assertTrue(isCollected(assertNotNull(renderer.received)), "the text given to libass is still held")

        assertTrue(plugin.load(SECOND_URL, MANIFEST_URL), "the second track did not load")
        assertEquals(true, firstBodyCollectedAtSecondFetch, "the first track was still held at the second fetch")
        assertTrue(isCollected(bodies.getValue(SECOND_URL)), "the second body is still held after the parse")

        assertNotNull(heldRef.get(), "control: a string that is held must read as held")
        assertTrue(held.length >= TRACK_CHARS)
    }

    @Test
    fun aByteRenderersTrackIsOneSharedArrayThatTheNextTrackReleases(): Unit = runBlocking {
        val renderer = ByteSinkRenderer()
        val player = player()
        val plugin = SubtitlePlugin(renderer)
        player.setup(PlayerConfig())
        player.addPlugin(plugin)

        assertTrue(plugin.load(FIRST_URL, MANIFEST_URL))
        var first: ByteArray? = assertNotNull(renderer.held)
        assertTrue(assertNotNull(first).size >= TRACK_CHARS, "control: the renderer was handed the whole track")
        assertEquals(0, renderer.stringLoads, "a byte renderer was handed a String of the whole script")
        assertTrue(isCollected(bodies.getValue(FIRST_URL)), "the downloaded body is still held after the parse")

        plugin.addFontLate("Late.ttf", byteArrayOf(1, 2, 3))
        assertEquals(2, renderer.handOvers.size, "the late font did not reload the track")
        assertTrue(renderer.handOvers.all { it === first }, "the reload made a second copy of the script")

        val firstRef = WeakReference(first)
        first = null
        renderer.handOvers.clear()
        assertTrue(plugin.load(SECOND_URL, MANIFEST_URL))
        assertEquals(true, firstBodyCollectedAtSecondFetch)
        assertTrue(isCollected(firstRef), "the first track's bytes are still held after the second track loaded")
    }
}
