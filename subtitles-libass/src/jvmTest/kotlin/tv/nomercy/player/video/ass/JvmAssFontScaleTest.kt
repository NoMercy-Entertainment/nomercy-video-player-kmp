// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import tv.nomercy.player.video.subtitles.AssImage
import tv.nomercy.player.video.subtitles.AssRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The viewer's text size, through the real library.
//
// Dialogue follows it and a positioned sign does not: scaling a sign moves it
// off the thing in the picture it was drawn to sit on, which is why the
// override is selective rather than a plain font scale.
class JvmAssFontScaleTest {

    @Test
    fun dialogueFollowsTheScaleAndAPositionedSignKeepsItsAuthoredSize() {
        val renderer: AssRenderer = LibassRequirement.rendererOrSkip() ?: return
        try {
            renderer.frameSize(FRAME_WIDTH, FRAME_HEIGHT)
            renderer.loadTrack(SCRIPT)

            val authored: Extents = extentsAt(renderer)
            renderer.fontScale(DOUBLE)
            val doubled: Extents = extentsAt(renderer)

            assertTrue(authored.dialogue > 0 && authored.sign > 0, "the script drew nothing: $authored")
            assertTrue(
                doubled.dialogue >= authored.dialogue * MIN_GROWTH,
                "dialogue stayed at its authored size: $authored then $doubled",
            )
            assertEquals(authored.sign, doubled.sign, "the positioned sign was scaled with the dialogue")
        } finally {
            renderer.release()
        }
    }

    private fun extentsAt(renderer: AssRenderer): Extents {
        val images: List<AssImage> = renderer.render(INSIDE_MILLIS)?.images.orEmpty()
        return Extents(
            dialogue = widthOf(images.filter { it.y >= SIGN_ZONE_BOTTOM }),
            sign = widthOf(images.filter { it.y < SIGN_ZONE_BOTTOM }),
        )
    }

    private fun widthOf(images: List<AssImage>): Int =
        if (images.isEmpty()) 0 else images.maxOf { it.x + it.width } - images.minOf { it.x }

    private data class Extents(val dialogue: Int, val sign: Int)
}

private const val FRAME_WIDTH = 1920

private const val FRAME_HEIGHT = 1080

private const val SIGN_ZONE_BOTTOM = 540

private const val INSIDE_MILLIS = 2_000L

private const val DOUBLE = 2.0

// Doubling the size grows a line by close to double; the margin keeps hinting
// from failing it.
private const val MIN_GROWTH = 1.5

private const val SCRIPT = """[Script Info]
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, Bold, Alignment, MarginL, MarginR, MarginV
Style: Default,sans-serif,60,&H00FFFFFF,0,2,10,10,40

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:05.00,Default,,0,0,0,,Hello from libass
Dialogue: 0,0:00:01.00,0:00:05.00,Default,,0,0,0,,{\an7\pos(100,100)}SIGN
"""
