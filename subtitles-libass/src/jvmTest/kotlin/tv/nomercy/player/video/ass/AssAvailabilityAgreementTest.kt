// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import tv.nomercy.player.video.subtitles.AssRenderer
import kotlin.test.Test
import kotlin.test.assertTrue

// The two answers about libass have to be the same answer.
//
// `create()` returning null and `whyUnavailable()` returning null at the same
// time is a machine reporting itself healthy while handing back no renderer,
// and there is nothing left for a caller to say to whoever is looking at blank
// subtitles. It reached CI as eight red gates whose only message was "no
// renderer on a platform that reports itself available": the library loaded and
// `ass_library_init` declined, which `whyUnavailable` never asked about.
//
// This runs on every desktop host, with and without libass, because it asserts
// agreement rather than availability — the version that skipped on a host with
// no library would have been silent on exactly the host that had the problem.
class AssAvailabilityAgreementTest {

    @Test
    fun aRefusalToCreateComesWithAReason() {
        val renderer: AssRenderer? = AssRenderers.create(AssPlatformContext())
        try {
            val reason: String? = AssRenderers.whyUnavailable()

            if (renderer == null) {
                assertTrue(
                    reason != null && reason.isNotBlank(),
                    "no renderer and no reason: a caller has nothing to tell the viewer",
                )
            } else {
                assertTrue(
                    reason == null,
                    "a renderer was built while the library called itself unavailable: $reason",
                )
            }
        } finally {
            renderer?.release()
        }
    }
}
