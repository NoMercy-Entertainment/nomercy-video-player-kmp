// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

// Structural gate before any native ass_read_memory call — not a parser,
// just rejects content with no recognisable ASS header before it reaches
// a native call that Kotlin/Native can't always guard with runCatching.
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

// Same bound the header field readers already scan by; densest measured track's [Events] is line 40.
private const val ASS_HEADER_SCAN_LINES = 200
