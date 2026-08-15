// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

// A bare-minimum structural gate in front of every native libass call this
// module makes, checked before content ever reaches ass_read_memory.
//
// Not a parser — libass's own parser is far more capable than anything worth
// duplicating here. This exists only to catch content with no recognisable
// ASS structure at all (a bad download, a truncated sync, a hand-edited file
// missing its own header) before it reaches a native call with no exception
// boundary. On the JVM/Android binding that call is additionally wrapped in
// runCatching for whatever JNA can still throw; on Kotlin/Native a trap
// inside libass itself generally cannot be caught, so this check is that
// binding's only line of defence, not a redundant one.
internal fun looksLikeAssScript(content: String): Boolean {
    if (content.isBlank()) return false
    var sawScriptInfo = false
    var sawEvents = false
    for (line in content.lineSequence().take(ASS_HEADER_SCAN_LINES)) {
        val trimmed = line.trim()
        if (trimmed.equals("[Script Info]", ignoreCase = true)) sawScriptInfo = true
        if (trimmed.equals("[Events]", ignoreCase = true)) sawEvents = true
        if (sawScriptInfo && sawEvents) return true
    }
    return false
}

// Same bound the header field readers in each renderer already scan by —
// every ASS header ends well inside this; [Events] on the densest track
// measured here is line 40.
private const val ASS_HEADER_SCAN_LINES = 200
