// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast.receiver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiverAuthPolicyTest {

    @Test
    fun denyAllRefusesEveryHandshake() {
        val withBearer = ReceiverHandshake("sender-1", "test-token", "127.0.0.1")
        val withoutBearer = ReceiverHandshake("sender-1", null, "127.0.0.1")

        assertFalse(ReceiverAuthPolicy.DenyAll.authorize(withBearer))
        assertFalse(ReceiverAuthPolicy.DenyAll.authorize(withoutBearer))
    }

    @Test
    fun aPolicySeesSenderBearerAndRemoteHost() {
        var seen: ReceiverHandshake? = null
        val policy = ReceiverAuthPolicy { handshake ->
            seen = handshake
            handshake.bearer == "test-token"
        }

        assertTrue(policy.authorize(ReceiverHandshake("sender-2", "test-token", "192.0.2.7")))
        assertEquals(ReceiverHandshake("sender-2", "test-token", "192.0.2.7"), seen)
        assertFalse(policy.authorize(ReceiverHandshake("sender-2", null, "192.0.2.7")))
    }
}
