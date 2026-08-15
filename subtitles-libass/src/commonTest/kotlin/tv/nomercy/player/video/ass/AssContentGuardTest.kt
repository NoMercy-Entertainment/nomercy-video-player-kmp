// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The one gate standing between whatever a queue hands loadTrack() and a
// native call with no exception boundary — see AssContentGuard's own doc.
class AssContentGuardTest {

    @Test
    fun aRealAssScriptPasses() {
        val content = """
            [Script Info]
            Title: Example
            PlayResX: 1920
            PlayResY: 1080

            [V4+ Styles]
            Format: Name, Fontname, Fontsize

            [Events]
            Format: Layer, Start, End, Text
            Dialogue: 0,0:00:00.00,0:00:02.00,Hello
        """.trimIndent()

        assertTrue(looksLikeAssScript(content))
    }

    @Test
    fun emptyContentFails() {
        assertFalse(looksLikeAssScript(""))
    }

    @Test
    fun blankContentFails() {
        assertFalse(looksLikeAssScript("   \n  \n  "))
    }

    @Test
    fun aTruncatedDownloadWithNoEventsSectionFails() {
        val content = """
            [Script Info]
            Title: Example
        """.trimIndent()

        assertFalse(looksLikeAssScript(content))
    }

    @Test
    fun unrelatedTextWithNoAssStructureFails() {
        assertFalse(looksLikeAssScript("This is not a subtitle file, just some prose."))
    }

    @Test
    fun sectionMarkersFarPastTheHeaderScanWindowStillFail() {
        val padding = (1..300).joinToString("\n") { "; padding line $it" }
        val content = "$padding\n[Script Info]\n[Events]\n"

        assertFalse(looksLikeAssScript(content))
    }
}
