// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Finding a television and waking it.
//
// Both are platform work with nothing testable in them, so what is pinned here
// is the shape three implementations have to share and the two decisions that
// are the library's rather than the platform's: what a viewer is shown, and what
// a caller does with each outcome.
class PlatformSeamTest {

    private fun device(
        serviceName: String = "Living Room-04217",
        loginState: String = "",
    ) = RemoteDevice(
        id = "dev-a",
        serviceName = serviceName,
        host = "192.168.1.40",
        port = 7626,
        loginState = loginState,
    )

    @Test
    fun aViewerSeesTheRoomRatherThanTheServiceName() {
        // The digits make the name unique on the network, which is the set's
        // problem. Showing them to someone choosing between rooms is showing
        // plumbing.
        assertEquals("Living Room", device().displayName)
    }

    @Test
    fun aNameWithoutTheSuffixIsLeftAlone() {
        assertEquals("Kitchen", device(serviceName = "Kitchen").displayName)
    }

    @Test
    fun digitsThatArePartOfTheNameSurvive() {
        // Only the five-digit suffix the television appends is removed. A room
        // someone genuinely called "Studio 54" keeps its number.
        assertEquals("Studio 54", device(serviceName = "Studio 54").displayName)
    }

    @Test
    fun aSetNobodyHasSignedIntoIsStillListedAndSaidToNeedIt() {
        // Hiding it would leave a viewer with no way to set it up, and casting
        // to it silently would fail with nothing explaining why.
        val fresh: RemoteDevice = device(loginState = "unauthenticated")

        assertTrue(fresh.needsSignIn)
        assertFalse(device(loginState = "authenticated").needsSignIn)
    }

    @Test
    fun everyImplementationBrowsesForTheSameService() {
        // A house with a phone and a laptop in it has to see the same list on
        // both, which only holds if all three implementations agree on this.
        assertEquals("_nomercy._tcp", DeviceDiscovery.SERVICE_TYPE)
    }

    @Test
    fun aBrowseIsStartedAndStoppedRatherThanLeftRunning() {
        // A browse left running is a radio kept awake, which on a phone is
        // measurable battery spent on a list nobody is looking at.
        val discovery = FakeDeviceDiscovery()

        discovery.start()
        assertTrue(discovery.started)

        discovery.stop()
        assertTrue(discovery.stopped)
    }

    @Test
    fun setsAppearingOnTheNetworkReachWhoeverIsWatching() {
        val discovery = FakeDeviceDiscovery()

        discovery.emit(listOf(device()))

        assertEquals(listOf("Living Room"), discovery.devices.value.map { it.displayName })
    }

    @Test
    fun aPlatformWithNoPanelToWakeSaysSoRatherThanFailing() {
        // Desktop and Apple reach the television directly. Reporting a failure
        // would make a caller tell a viewer something is wrong when nothing is.
        runTest {
            val waker: CastWaker = UnsupportedCastWaker()

            waker.warmUp()

            assertEquals(WakeOutcome.UNSUPPORTED, waker.wake("dev-a"))
        }
    }

    @Test
    fun eachWakeOutcomeIsDistinctBecauseCallersActOnThemDifferently() {
        // An already-awake set can be cast to now; a wake merely sent needs a
        // moment; no route at all is the one a viewer has to be told about. A
        // boolean would collapse the three.
        runTest {
            assertEquals(WakeOutcome.AWOKE, FakeCastWaker(WakeOutcome.AWOKE).wake("dev-a"))
            assertEquals(WakeOutcome.NO_ROUTE, FakeCastWaker(WakeOutcome.NO_ROUTE).wake("dev-a"))
        }
    }

    @Test
    fun theSetThatWasAskedForIsTheSetThatIsWoken() {
        runTest {
            val waker = FakeCastWaker()

            waker.wake("dev-a")
            waker.wake("dev-b")

            assertEquals(listOf("dev-a", "dev-b"), waker.woke)
        }
    }
}
