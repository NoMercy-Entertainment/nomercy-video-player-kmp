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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// NoMercy televisions on the local network, as Android sees them.
//
// Two stages, because the platform makes them two: a browse turns up names, and
// each name has to be resolved separately before it has an address. A picker
// showing unresolved names would list televisions it cannot connect to.
public open class NsdDeviceDiscovery(context: Context) : DeviceDiscovery {

    private val manager: NsdManager? =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val found = MutableStateFlow<List<RemoteDevice>>(emptyList())

    override val devices: StateFlow<List<RemoteDevice>> = found.asStateFlow()

    private var listener: NsdManager.DiscoveryListener? = null

    override fun start() {
        if (listener != null) return

        val active = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                resolve(service)
            }

            // Matched on the service name rather than on an address, because a
            // set that has gone away cannot be resolved to tell us which one it
            // was — the name is all that survives the disappearance.
            override fun onServiceLost(service: NsdServiceInfo) {
                found.value = found.value.filterNot { it.serviceName == service.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            // Stopped rather than retried. A failing browse retried in place is
            // a radio kept awake for a list that is not going to arrive.
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stop()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        listener = active
        manager?.discoverServices(
            DeviceDiscovery.SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            active,
        )
    }

    override fun stop() {
        val active: NsdManager.DiscoveryListener = listener ?: return
        listener = null
        runCatching { manager?.stopServiceDiscovery(active) }
        found.value = emptyList()
    }

    private fun resolve(service: NsdServiceInfo) {
        @Suppress("DEPRECATION")
        manager?.resolveService(
            service,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) = Unit

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    add(resolved.toRemoteDevice())
                }
            },
        )
    }

    // Replaced rather than appended when the same set resolves twice, which it
    // does whenever a television changes address on a network that renumbers.
    private fun add(device: RemoteDevice) {
        found.value = found.value.filterNot { it.serviceName == device.serviceName } + device
    }
}

private fun NsdServiceInfo.toRemoteDevice(): RemoteDevice = RemoteDevice(
    id = serviceName,
    serviceName = serviceName,
    host = host?.hostAddress.orEmpty(),
    port = port,
    loginState = attribute("loginState"),
    fingerprint = attribute("fp"),
)

// Bytes on the wire, and absent on a set that predates the record. An empty
// string is the honest reading of both.
private fun NsdServiceInfo.attribute(name: String): String =
    attributes[name]?.toString(Charsets.UTF_8).orEmpty()
