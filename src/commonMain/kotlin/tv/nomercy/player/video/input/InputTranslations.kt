// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

// The key handler's and the touch zones' strings.
//
// Written rather than generated, which is the opposite of every other table
// here and is the point. scripts/generate-player-translations.py refuses a
// folder where every locale carries the canonical English, because that is
// what a plugin whose i18n folder was copied and never translated looks like.
// These three are that on purpose: they are number formats with no words in
// them, and the web ships all 78 locales carrying the identical value. A
// generated table would be 78 copies of "{speed}x".
//
// If a word ever appears in one of these, it belongs in the generator with the
// rest and this file should go.
public object InputTranslations {

    public const val SPEED: String = "plugin.key-handler.speed"
    public const val SEEK_BACK: String = "plugin.touch-zones.seek.back"
    public const val SEEK_FORWARD: String = "plugin.touch-zones.seek.forward"

    // Verbatim from the web's en.ts, placeholders included: a host substitutes
    // {speed} and {seconds} the same way it does for every other key, so a
    // chrome does not need to know these are special.
    public val ALL: Map<String, String> = mapOf(
        SPEED to "{speed}x",
        SEEK_BACK to "-{seconds}s",
        SEEK_FORWARD to "+{seconds}s",
    )
}
