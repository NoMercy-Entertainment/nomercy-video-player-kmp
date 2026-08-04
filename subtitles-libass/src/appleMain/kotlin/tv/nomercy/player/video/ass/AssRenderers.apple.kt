// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import tv.nomercy.player.video.subtitles.AssRenderer
import kotlinx.cinterop.ExperimentalForeignApi
import libass.ass_library_init

public actual class AssPlatformContext

// libass is linked in on Apple, so the only way it is unavailable is a library
// that refuses to initialise — which happens when it is out of memory and
// almost never otherwise.
@OptIn(ExperimentalForeignApi::class)
public actual object AssRenderers {

    public actual fun create(context: AssPlatformContext): AssRenderer? {
        val library = ass_library_init() ?: return null
        return AppleAssRenderer(library)
    }

    public actual fun whyUnavailable(): String? = null
}
