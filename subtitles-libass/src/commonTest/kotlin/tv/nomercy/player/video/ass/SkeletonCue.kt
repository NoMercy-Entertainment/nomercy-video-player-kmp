// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

// One cue, in the shape a real .ass file has.
//
// The sections, the Format line before the Style line, an explicit PlayResX/Y so
// the renderer has an authored resolution to scale from, and a style naming a
// font that is not the default — because a cue that renders in the fallback
// typeface looks identical to a cue that rendered correctly, and the whole point
// of this slice is to know the difference.
internal fun skeletonAss(fontName: String = SKELETON_FONT): String = SKELETON_TEMPLATE.replace(FONT_SLOT, fontName)

private const val FONT_SLOT = "@FONT@"

// The name nothing on any device answers to, so a cue asking for it can only be
// satisfied by a font that was attached.
internal const val SKELETON_FONT: String = "Skeleton Sans"

private const val SKELETON_TEMPLATE: String = """[Script Info]
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, Bold, Alignment, MarginL, MarginR, MarginV
Style: Skeleton,@FONT@,72,&H00FFFFFF,0,2,10,10,40

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:05.00,Skeleton,,0,0,0,,Hello from libass
"""

// Inside the cue, and outside it. A gate that only ever asked at one timestamp
// would pass on a renderer that drew the cue permanently.
internal const val INSIDE_CUE_MILLIS: Long = 2_000L
internal const val BEFORE_CUE_MILLIS: Long = 200L
