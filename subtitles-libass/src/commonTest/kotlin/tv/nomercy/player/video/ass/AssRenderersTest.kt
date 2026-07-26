// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// What a caller learns before it tries to draw anything.
//
// A platform without a renderer has to say so in a sentence, because the caller
// that asks is deciding between styled subtitles and plain text — and the
// developer who reads the answer is the one wondering why the subtitles are
// blank.
class AssRenderersTest {

    @Test
    fun anUnavailableRendererExplainsItselfRatherThanReturningNothing() {
        val reason: String? = AssRenderers.whyUnavailable()

        if (reason == null) return
        assertTrue(reason.contains("libass"), "the reason does not mention libass: $reason")
        assertTrue(reason.length > SHORT_REASON, "the reason is too short to act on: $reason")
    }

    @Test
    fun availabilityAndTheReasonAgree() {
        // Two ways of asking the same question, and a caller will use whichever
        // reads better at the call site. They must never disagree.
        assertEquals(AssRenderers.whyUnavailable() == null, AssRenderers.isAvailable())
    }

    private companion object {
        const val SHORT_REASON = 20
    }
}
