// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.PlayerErrorEvent
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.FetchResponse
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Switching from one styled track to another never leaves the first one drawing.
 *
 * Seen on the living-room TV: the menu ticked "English (Signs)" while a dialogue
 * line that exists only in the Full track was on screen. The Signs file never
 * replaced the Full one in libass, and the plugin still reported the new url as
 * the loaded one.
 *
 * Three ways to get there, one test each: the new file could not be fetched, the
 * old track stayed in the renderer for as long as the new one took to arrive,
 * and a slow earlier request finished after a later one and replaced it.
 */
class SubtitleSwitchBlanksTheOldTrackTest {

    @Test
    fun aSwitchToATrackThatCannotBeFetchedLeavesNothingDrawn() = runTest {
        val warnings: MutableList<String> = mutableListOf()
        val player = play { url ->
            if (url == FULL) FetchResponse(status = OK, body = FULL_BODY) else FetchResponse(status = MISSING, body = "")
        }
        player.on(CoreEvents.Warning) { event: PlayerErrorEvent -> warnings += event.code }
        player.on(CoreEvents.PluginWarning) { event: PlayerErrorEvent -> warnings += event.code }
        val renderer = HeldTrackRenderer()
        val plugin = SubtitlePlugin(renderer).also { player.addPlugin(it) }

        assertTrue(plugin.load(FULL, null))
        assertEquals(FULL_BODY, renderer.held)

        assertFalse(plugin.load(SIGNS, null), "the signs file does not exist")

        assertFalse(renderer.hasTrack(), "the full track is still in the renderer: ${renderer.held}")
        assertNull(plugin.subtitle(), "the plugin reports a url nothing is drawing")
        assertTrue(warnings.any { it == "plugin:octopus/load-failed" }, "the failure was not reported; got $warnings")
    }

    @Test
    fun theOldTrackIsGoneBeforeTheNewOneHasArrived() = runTest {
        val signsGate = CompletableDeferred<Unit>()
        val player = play { url ->
            if (url == SIGNS) signsGate.await()
            FetchResponse(status = OK, body = if (url == FULL) FULL_BODY else SIGNS_BODY)
        }
        val renderer = HeldTrackRenderer()
        val plugin = SubtitlePlugin(renderer).also { player.addPlugin(it) }
        plugin.load(FULL, null)

        val switching = launch { plugin.load(SIGNS, null) }
        runCurrent()

        assertFalse(renderer.hasTrack(), "the full track kept drawing while the signs file downloaded: ${renderer.held}")

        signsGate.complete(Unit)
        switching.join()
        assertEquals(SIGNS_BODY, renderer.held)
    }

    @Test
    fun aSlowEarlierRequestCannotReplaceALaterOne() = runTest {
        val fullGate = CompletableDeferred<Unit>()
        val player = play { url ->
            if (url == FULL) fullGate.await()
            FetchResponse(status = OK, body = if (url == FULL) FULL_BODY else SIGNS_BODY)
        }
        val renderer = HeldTrackRenderer()
        val plugin = SubtitlePlugin(renderer).also { player.addPlugin(it) }

        val slow = launch { plugin.load(FULL, null) }
        runCurrent()
        assertTrue(plugin.load(SIGNS, null), "the last request must install")

        fullGate.complete(Unit)
        slow.join()

        assertEquals(SIGNS_BODY, renderer.held, "the earlier, slower request finished last and won")
        assertFalse(renderer.installed.contains(FULL_BODY), "the dropped request was installed at some point: ${renderer.installed}")
        assertEquals(SIGNS, plugin.subtitle())
    }

    private suspend fun play(answer: suspend (String) -> FetchResponse): ComposedPlayer =
        ComposedPlayer(backend = null, fetcher = { url, _ -> answer(url) }).also { it.setup(PlayerConfig()) }

    // A renderer that is only a record of what it was last told to draw. The
    // plugin's job is deciding what is in the renderer, and that is all this
    // observes.
    private class HeldTrackRenderer : AssRenderer {
        var held: String = ""
            private set
        val installed: MutableList<String> = mutableListOf()

        override fun addFont(name: String, data: ByteArray): Unit = Unit

        override fun clearFonts(): Unit = Unit

        override fun loadTrack(assContent: String) {
            held = assContent
            if (assContent.isNotEmpty()) installed += assContent
        }

        override fun hasTrack(): Boolean = held.isNotBlank()

        override fun frameSize(width: Int, height: Int): Unit = Unit

        override fun storageSize(width: Int, height: Int): Unit = Unit

        override fun storageSize(): AssSize? = null

        override fun render(timeMillis: Long): AssFrame? = null

        override fun release(): Unit = Unit
    }

    private companion object {
        const val OK = 200
        const val MISSING = 404
        const val FULL = "https://media.example.test/show/1/eng.full.ass"
        const val SIGNS = "https://media.example.test/show/1/eng.sign.ass"
        const val FULL_BODY = "[Script Info]\n[Events]\nDialogue: full"
        const val SIGNS_BODY = "[Script Info]\n[Events]\nDialogue: signs"
    }
}
