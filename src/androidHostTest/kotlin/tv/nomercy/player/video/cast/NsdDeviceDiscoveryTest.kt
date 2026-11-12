// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Looper
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [NsdDeviceDiscoveryTest.FakeNsdManager::class])
class NsdDeviceDiscoveryTest {

    @BeforeTest
    fun reset() {
        FakeNsdManager.reset()
    }

    @Test
    fun simultaneousAnnouncementsBothReachPicker() {
        assertNotNull(RuntimeEnvironment.getApplication().getSystemService(Context.NSD_SERVICE))
        val discovery = NsdDeviceDiscovery(RuntimeEnvironment.getApplication())
        discovery.start()

        FakeNsdManager.announce(service("Living Room", "192.0.2.10"))
        FakeNsdManager.announce(service("Bedroom", "192.0.2.11"))
        FakeNsdManager.completeActive()
        FakeNsdManager.completeIfActive()

        assertEquals(setOf("Living Room", "Bedroom"), discovery.devices.value.map { it.serviceName }.toSet())
        discovery.stop()
    }

    @Test
    fun oneFailedResolveIsRetried() {
        val discovery = NsdDeviceDiscovery(RuntimeEnvironment.getApplication())
        discovery.start()

        FakeNsdManager.announce(service("Kitchen", "192.0.2.12"))
        FakeNsdManager.failActive()
        assertEquals(1, FakeNsdManager.resolveCalls)
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        FakeNsdManager.completeIfActive()

        assertEquals(listOf("Kitchen"), discovery.devices.value.map { it.serviceName })
        assertEquals(2, FakeNsdManager.resolveCalls)
        discovery.stop()
    }

    @Test
    fun repeatedFailuresStopAfterThreeAttempts() {
        val discovery = NsdDeviceDiscovery(RuntimeEnvironment.getApplication())
        discovery.start()

        FakeNsdManager.announce(service("Kitchen", "192.0.2.12"))
        repeat(3) {
            FakeNsdManager.failActive()
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        }

        assertEquals(3, FakeNsdManager.resolveCalls)
        assertEquals(emptyList(), discovery.devices.value)
        discovery.stop()
    }

    @Test
    fun nextTelevisionResolvesWhileFailedOneWaitsForRetry() {
        val discovery = NsdDeviceDiscovery(RuntimeEnvironment.getApplication())
        discovery.start()

        FakeNsdManager.announce(service("Living Room", "192.0.2.10"))
        FakeNsdManager.announce(service("Bedroom", "192.0.2.11"))
        FakeNsdManager.failActive()
        FakeNsdManager.completeActive()
        assertEquals(listOf("Bedroom"), discovery.devices.value.map { it.serviceName })

        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        FakeNsdManager.completeActive()
        assertEquals(setOf("Living Room", "Bedroom"), discovery.devices.value.map { it.serviceName }.toSet())
        discovery.stop()
    }

    @Test
    fun lostTelevisionDoesNotReappearFromPendingResolve() {
        val discovery = NsdDeviceDiscovery(RuntimeEnvironment.getApplication())
        discovery.start()

        val livingRoom = service("Living Room", "192.0.2.10")
        val bedroom = service("Bedroom", "192.0.2.11")
        FakeNsdManager.announce(livingRoom)
        FakeNsdManager.announce(bedroom)
        FakeNsdManager.lose(bedroom)
        FakeNsdManager.completeActive()
        assertEquals(listOf("Living Room"), discovery.devices.value.map { it.serviceName })

        FakeNsdManager.lose(livingRoom)
        assertEquals(emptyList(), discovery.devices.value)
        discovery.stop()
    }

    private fun service(name: String, address: String): NsdServiceInfo = NsdServiceInfo().apply {
        serviceName = name
        host = InetAddress.getByName(address)
        port = 8096
    }

    @Implements(NsdManager::class)
    class FakeNsdManager {
        @Implementation
        fun discoverServices(serviceType: String, protocolType: Int, listener: NsdManager.DiscoveryListener) {
            discoveryListener = listener
        }

        @Implementation
        fun stopServiceDiscovery(listener: NsdManager.DiscoveryListener) {
            discoveryListener = null
        }

        @Implementation
        fun resolveService(service: NsdServiceInfo, listener: NsdManager.ResolveListener) {
            resolveCalls++
            if (active != null) {
                listener.onResolveFailed(service, NsdManager.FAILURE_ALREADY_ACTIVE)
            } else {
                active = service to listener
            }
        }

        companion object {
            var discoveryListener: NsdManager.DiscoveryListener? = null
            var active: Pair<NsdServiceInfo, NsdManager.ResolveListener>? = null
            var resolveCalls: Int = 0

            fun reset() {
                discoveryListener = null
                active = null
                resolveCalls = 0
            }

            fun announce(service: NsdServiceInfo) {
                assertNotNull(discoveryListener).onServiceFound(service)
            }

            fun lose(service: NsdServiceInfo) {
                assertNotNull(discoveryListener).onServiceLost(service)
            }

            fun completeActive() {
                val (service, listener) = assertNotNull(active)
                active = null
                listener.onServiceResolved(service)
            }

            fun completeIfActive() {
                if (active != null) completeActive()
            }

            fun failActive() {
                val (service, listener) = assertNotNull(active)
                active = null
                listener.onResolveFailed(service, NsdManager.FAILURE_INTERNAL_ERROR)
            }
        }
    }
}
