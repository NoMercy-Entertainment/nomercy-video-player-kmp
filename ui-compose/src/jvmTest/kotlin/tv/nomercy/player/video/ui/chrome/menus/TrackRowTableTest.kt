// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import tv.nomercy.player.core.ports.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The rows the subtitle pane will draw, printed for both locales.
 *
 * The codes are the ones the Tears of Steel pane actually listed when it was
 * wrong: it showed Brazilian, Chinese, Croatian, Czech, Danish, Dutch, English
 * and French under a header reading `Ondertiteling`.
 */
class TrackRowTableTest {

    private val codes: List<String> =
        listOf("bra", "zho", "hrv", "cze", "dan", "nld", "eng", "fre")

    @Test
    fun everyRowIsNamedInBothLocales() {
        val rows: List<Triple<String, String, String>> = codes.mapIndexed { index, code ->
            val track = SubtitleTrack(id = "$index", language = code, label = "")
            Triple(code, subtitleLabel(track, index, "nl"), subtitleLabel(track, index, "en"))
        }

        println("code   nl                     en")
        for ((code, dutch, english) in rows) {
            println(code.padEnd(6) + " " + dutch.padEnd(22) + " " + english)
        }

        // The proof, not the print: no row may fall back to its raw code, and
        // the two locales must actually differ. A table where both columns match
        // is a table where the locale was ignored, which is the defect restated.
        for ((code, dutch, english) in rows) {
            assertTrue(!dutch.equals(code, ignoreCase = true), "$code fell back to its code in Dutch")
            assertTrue(!english.equals(code, ignoreCase = true), "$code fell back to its code in English")
        }

        val differing: Int = rows.count { (_, dutch, english) -> dutch != english }
        assertTrue(differing >= 5, "only $differing of ${rows.size} rows differ between nl and en")
    }
}
