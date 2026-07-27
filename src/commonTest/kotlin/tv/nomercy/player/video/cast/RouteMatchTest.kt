// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Which of the routes a phone offers might have a television behind it.
//
// The two exclusions are the whole of it and neither is arbitrary. Extracted
// from the Android waker so they can be asserted without a Chromecast, which is
// the only part of waking a panel a headless test can say anything about.
class RouteMatchTest {

    @Test
    fun anOrdinaryRouteIsWorthTrying() {
        assertTrue(isSelectableCastRoute(isDefault = false, isBluetooth = false))
    }

    @Test
    fun thePhonesOwnSpeakerIsNot() {
        // The default route is this device. Selecting it would cast the film to
        // the phone that is trying to cast it.
        assertFalse(isSelectableCastRoute(isDefault = true, isBluetooth = false))
    }

    @Test
    fun aBluetoothRouteIsNot() {
        // A headset. Moving audio there is not what someone pressing cast asked
        // for, and it is silent about having done it.
        assertFalse(isSelectableCastRoute(isDefault = false, isBluetooth = true))
    }

    @Test
    fun aRouteThatIsBothIsStillNot() {
        assertFalse(isSelectableCastRoute(isDefault = true, isBluetooth = true))
    }

    @Test
    fun aPlatformThatCannotWakeSaysSoRatherThanClaimingSuccess() {
        // A television that never woke and a phone that thinks it did is the
        // worst of the available answers: the viewer is shown a casting state
        // and a dark screen with nothing explaining the gap.
        kotlinx.coroutines.test.runTest {
            assertTrue(defaultCastWaker().wake("dev-a") == WakeOutcome.UNSUPPORTED)
        }
    }

    @Test
    fun aPlatformWithNoBrowserYetOffersAnEmptyListRatherThanFailing() {
        val discovery: DeviceDiscovery = NoDeviceDiscovery()

        discovery.start()

        assertTrue(discovery.devices.value.isEmpty())
        discovery.stop()
    }
}
