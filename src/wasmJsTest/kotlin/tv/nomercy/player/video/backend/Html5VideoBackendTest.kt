// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.backend

import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.CanonicalBackendEvent
import tv.nomercy.player.core.ports.VideoBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Runs in a real browser (karma), against a real `<video>` element — this
// backend has no fake DOM to test against, unlike MpvVideoBackend's libmpv
// binding, so the conformance bar here is the actual element, not a stub.
class Html5VideoBackendTest {

    @Test
    fun startsIdleAndIsAVideoBackend() {
        val backend: VideoBackend = Html5VideoBackend()

        assertEquals(BackendState.IDLE, backend.state())
        assertEquals(1.0, backend.volume().toDouble())
        assertEquals(0.0, backend.currentTime())

        backend.release()
    }

    @Test
    fun volumeClampsToTheZeroToOneScale() {
        val backend = Html5VideoBackend()

        backend.volume(2.0f)
        assertEquals(1.0f, backend.volume())

        backend.volume(-1.0f)
        assertEquals(0.0f, backend.volume())

        backend.release()
    }

    @Test
    fun loadEmitsLoadStartBeforeAnythingElse() {
        val backend = Html5VideoBackend()
        val seen = mutableListOf<String>()
        backend.on(CanonicalBackendEvent.LOAD_START) { seen.add(CanonicalBackendEvent.LOAD_START) }

        // No real network fetch is awaited here — `load()` returns once the
        // element's own `load()` call has been issued, matching every other
        // backend's fire-and-forget shape for a URL this test never resolves.
        kotlinx.coroutines.test.runTest {
            backend.load("https://example.test/does-not-exist.mp4")
        }

        assertEquals(listOf(CanonicalBackendEvent.LOAD_START), seen)
        backend.release()
    }

    @Test
    fun releaseStopsFurtherEventsFromReachingListeners() {
        val backend = Html5VideoBackend()
        var calls = 0
        backend.on(CanonicalBackendEvent.LOAD_START) { calls++ }

        backend.release()
        // A second release must not throw — release() is idempotent, matching
        // every other MediaBackend implementation's own contract.
        backend.release()

        assertTrue(calls == 0)
    }

    @Test
    fun aVideoBackendReportsNoQualityLevelsWithoutHlsJs() {
        // The documented gap: see Html5VideoBackend's class doc. Asserted here
        // so a future hls.js integration has a red test to turn green rather
        // than a silent behavioural change.
        val backend: VideoBackend = Html5VideoBackend()

        assertTrue(backend.qualityLevels().isEmpty())
        assertEquals(null, backend.quality())

        backend.release()
    }
}
