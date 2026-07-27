// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.thumbnails

// How the sheet and its layout are read, supplied by whoever is doing the
// reading.
//
// Both files sit behind the same bearer token as the media itself, so a loader
// with its own HTTP client would be a second place for auth to be got wrong —
// and one that works against a developer's open server while every
// authenticated install shows no preview at all. The web player carries the same
// pair for the same reason.
public class SpriteFetchers(
    public val bytes: suspend (String) -> ByteArray?,
    public val text: suspend (String) -> String?,
)
