// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.preferences

// Which preferences come back, one switch each.
//
// Separate switches rather than one, because the reasons to turn them off are
// not the same reason. A kiosk wants the volume it was configured with and the
// viewer's subtitle language; a trailer reel wants neither. Saving is never
// switched off: what is written costs nothing to keep, and a consumer who turns
// restoring back on should find their viewer's choices still there.
public data class VideoPreferencesOptions(
    val restoreSubtitle: Boolean = true,
    val restoreAudio: Boolean = true,
    val restoreQuality: Boolean = true,
    val restoreVolume: Boolean = true,
    val restoreSubtitleStyle: Boolean = true,
)
