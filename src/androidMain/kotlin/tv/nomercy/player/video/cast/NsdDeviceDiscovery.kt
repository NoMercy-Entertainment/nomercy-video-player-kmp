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
import android.os.Handler
import android.os.Looper
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
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private val pending = ArrayDeque<ResolveRequest>()
    private val retries = mutableMapOf<ResolveRequest, Runnable>()
    private var resolving: ResolveRequest? = null

    private class ResolveRequest(
        val scan: NsdManager.DiscoveryListener,
        val service: NsdServiceInfo,
        var attempts: Int = 0,
        var cancelled: Boolean = false,
    )

    override fun start() {
        if (listener != null) return

        val active = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                synchronized(lock) {
                    if (listener !== this) return
                    pending.addLast(ResolveRequest(this, service))
                }
                resolveNext()
            }

            // Matched on the service name rather than on an address, because a
            // set that has gone away cannot be resolved to tell us which one it
            // was — the name is all that survives the disappearance.
            override fun onServiceLost(service: NsdServiceInfo) {
                synchronized(lock) {
                    if (listener !== this) return
                    val name = service.serviceName
                    found.value = found.value.filterNot { it.serviceName == name }
                    pending.removeAll { it.service.serviceName == name }
                    resolving?.takeIf { it.service.serviceName == name }?.cancelled = true
                    val retryIterator = retries.iterator()
                    while (retryIterator.hasNext()) {
                        val (request, retry) = retryIterator.next()
                        if (request.service.serviceName == name) {
                            handler.removeCallbacks(retry)
                            retryIterator.remove()
                        }
                    }
                }
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
        val active: NsdManager.DiscoveryListener = synchronized(lock) {
            val current = listener ?: return
            listener = null
            pending.clear()
            resolving = null
            retries.values.forEach(handler::removeCallbacks)
            retries.clear()
            found.value = emptyList()
            current
        }
        runCatching { manager?.stopServiceDiscovery(active) }
    }

    private fun resolveNext() {
        val request = synchronized(lock) {
            if (resolving != null || manager == null) return
            if (pending.isEmpty()) return
            pending.removeFirst().also {
                resolving = it
                it.attempts++
            }
        }
        if (listener !== request.scan) return
        @Suppress("DEPRECATION")
        manager?.resolveService(request.service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                resolved(request, null)
            }

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                resolved(request, resolved)
            }
        })
    }

    private fun resolved(request: ResolveRequest, service: NsdServiceInfo?) {
        synchronized(lock) {
            if (resolving !== request) return
            resolving = null
            if (listener === request.scan && !request.cancelled) {
                if (service != null) {
                    add(service.toRemoteDevice())
                } else if (request.attempts < MAX_RESOLVE_ATTEMPTS) {
                    scheduleRetry(request)
                }
            }
        }
        resolveNext()
    }

    private fun scheduleRetry(request: ResolveRequest) {
        val retry = Runnable {
            val queued = synchronized(lock) {
                retries.remove(request)
                if (listener === request.scan && !request.cancelled) {
                    pending.addLast(request)
                    true
                } else {
                    false
                }
            }
            if (queued) resolveNext()
        }
        retries[request] = retry
        handler.postDelayed(retry, RESOLVE_RETRY_DELAY_MS)
    }

    // Replaced rather than appended when the same set resolves twice, which it
    // does whenever a television changes address on a network that renumbers.
    private fun add(device: RemoteDevice) {
        found.value = found.value.filterNot { it.serviceName == device.serviceName } + device
    }

    private companion object {
        const val MAX_RESOLVE_ATTEMPTS = 3
        const val RESOLVE_RETRY_DELAY_MS = 200L
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
