// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import com.sun.jna.Pointer
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A script libass rejects leaves the renderer with no track at all.
 *
 * The parse used to happen lazily inside render(). A script that passed the
 * structural gate and was then refused by ass_read_memory kept hasTrack() true,
 * so the layer read render() == null as "nothing changed" and left the previous
 * track's last cue on screen, re-trying the parse on every frame.
 *
 * Libass is replaced by a stand-in on purpose: which scripts the real parser
 * refuses is libass's business and changes with its version, while what this
 * renderer does with a refusal is ours and must not depend on it. This test
 * therefore never skips.
 */
class RejectedScriptBlanksTheRendererTest {

    @Test
    fun aScriptTheParserRefusesLeavesNoTrackAndAnEmptyAnswer() {
        val parser = ScriptedParser(accepts = setOf(GOOD))
        val renderer = NativeAssRenderer(parser.lib(), Pointer(LIBRARY), glyphMax = 1, bitmapCacheMegabytes = 1)
        renderer.frameSize(1920, 1080)

        renderer.loadTrack(GOOD)
        renderer.render(0L)
        assertTrue(renderer.hasTrack(), "a script the parser accepts must be loaded")

        renderer.loadTrack(REFUSED)

        assertFalse(renderer.hasTrack(), "the parser refused the script and the renderer still claims a track")
        assertNull(renderer.render(0L), "a renderer without a track must answer null so the layer blanks")
    }

    @Test
    fun theParseHappensWhenTheTrackIsLoadedNotWhenTheFirstFrameIsDrawn() {
        val parser = ScriptedParser(accepts = setOf(GOOD))
        val renderer = NativeAssRenderer(parser.lib(), Pointer(LIBRARY), glyphMax = 1, bitmapCacheMegabytes = 1)

        renderer.loadTrack(GOOD)

        assertTrue(parser.reads == 1, "the script was not parsed by loadTrack; reads=${parser.reads}")
        renderer.render(0L)
        assertTrue(parser.reads == 1, "drawing a frame parsed the script again; reads=${parser.reads}")
    }

    // The parts of libass this renderer touches, answering "no" to every script
    // it was not told to accept. Pointers are opaque tokens here; nothing reads
    // through them because ass_render_frame answers an empty list.
    private class ScriptedParser(private val accepts: Set<String>) {
        var reads: Int = 0
            private set

        fun lib(): LibAss = Proxy.newProxyInstance(
            LibAss::class.java.classLoader,
            arrayOf(LibAss::class.java),
        ) { _, method, args ->
            when (method.name) {
                "ass_renderer_init" -> Pointer(RENDERER)
                "ass_read_memory" -> {
                    reads++
                    val script = String(args[1] as ByteArray, 0, args[2] as Int)
                    if (script in accepts) Pointer(TRACK) else null
                }
                "ass_render_frame" -> null
                else -> when (method.returnType) {
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Double::class.javaPrimitiveType -> 0.0
                    else -> null
                }
            }
        } as LibAss
    }

    private companion object {
        const val LIBRARY = 1L
        const val RENDERER = 2L
        const val TRACK = 3L

        const val GOOD = "[Script Info]\nPlayResX: 1920\n[Events]\nDialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,,full"
        const val REFUSED = "[Script Info]\nPlayResX: 1280\n[Events]\nDialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,,signs"
    }
}
