// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// How long a wake gets before it is called failed.
//
// Measured live against a Nokia Streaming Box waking from full sleep, three
// runs: 8.2s, 18.1s, and one over 20s that timed out on a wake that was still
// in progress. Sized off the slowest of those with headroom — the number this
// port carries forward from `PhoneCastSenderImpl`'s own hard-won fix, not the
// smaller figure the plan that named this class was written against.
private const val WAKE_TIMEOUT_MS = 45_000L

// Waking a television panel through a Chromecast.
//
// Ports `PhoneCastSenderImpl.startSessionFor`/`warmRouteDiscovery` behind the
// portable [CastWaker] seam. Wake-only: it selects a route and waits for the
// session to start, and it never calls `RemoteMediaClient.load` — the film
// reaches the set through the set's own `/v1` player, not through Cast.
//
// The receiver application id comes from the host app's own
// `CastOptionsProvider` (`AndroidManifest` meta-data), the same as any Cast
// Sender integration — this library asks `CastContext` for the selector it
// was already configured with rather than taking an id parameter, so a
// library call site never repeats what the app's manifest already states.
public open class ChromecastCastWaker(private val context: Context) : CastWaker {

    private var warmCallback: MediaRouter.Callback? = null

    override suspend fun warmUp() {
        if (warmCallback != null) return
        withContext(Dispatchers.Main) {
            runCatching {
                val selector = CastContext.getSharedInstance(context).mergedSelector ?: return@runCatching
                val callback = object : MediaRouter.Callback() {}
                warmCallback = callback
                MediaRouter.getInstance(context).addCallback(
                    selector,
                    callback,
                    MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN,
                )
            }
        }
    }

    override suspend fun wake(deviceId: String): WakeOutcome = withMulticastLock {
        runCatching {
            val castContext = CastContext.getSharedInstance(context)
            val sessionManager = castContext.sessionManager
            val mediaRouter = MediaRouter.getInstance(context)
            val selector = castContext.mergedSelector ?: return@runCatching WakeOutcome.NO_ROUTE

            // A session pointer left over from a TV panel that went to standby
            // while the session stayed open never fires a fresh CEC One-Touch-
            // Play on reselect. Ending it and letting cast_shell settle before
            // scanning is what makes the wake reliable rather than a coin flip.
            sessionManager.currentCastSession?.let {
                withContext(Dispatchers.Main) {
                    runCatching { sessionManager.endCurrentSession(false) }
                }
                delay(300)
            }

            val targetRoute = withContext(Dispatchers.Main) {
                val callback = object : MediaRouter.Callback() {}
                mediaRouter.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
                try {
                    var found: MediaRouter.RouteInfo? = null
                    val deadline = System.currentTimeMillis() + 6_000L
                    while (System.currentTimeMillis() < deadline && found == null) {
                        found = mediaRouter.routes.firstOrNull { route ->
                            route.matchesSelector(selector) &&
                                isSelectableCastRoute(isDefault = route.isDefault, isBluetooth = route.isBluetooth)
                        }
                        if (found == null) delay(250)
                    }
                    found
                } finally {
                    mediaRouter.removeCallback(callback)
                }
            } ?: return@runCatching WakeOutcome.NO_ROUTE

            val started = awaitSessionStart(sessionManager, routeId = targetRoute.id) {
                mediaRouter.selectRoute(targetRoute)
            }
            if (started == true) WakeOutcome.CAST_FALLBACK else WakeOutcome.NO_ROUTE
        }.getOrDefault(WakeOutcome.NO_ROUTE)
    }

    /**
     * Holds a WifiManager multicast lock for the duration of Cast session
     * negotiation. Without an active lock, some phones' WiFi chips filter
     * incoming multicast/mDNS traffic during aggressive power-save states,
     * which can silently starve the mDNS-SD hostname resolution the Cast v2
     * session handshake depends on mid-negotiation.
     */
    private suspend fun <T> withMulticastLock(block: suspend () -> T): T {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifiManager?.createMulticastLock("NoMercyCastWake")
        try {
            // Guarded: acquire() can throw SecurityException — confirmed live,
            // real device, 2026-08-15 (CHANGE_WIFI_MULTICAST_STATE missing).
            // A denied lock makes mDNS resolution flakier during negotiation,
            // which is what this lock exists to prevent — not a reason to
            // crash a wake attempt that might still succeed without it.
            runCatching { lock?.acquire() }
            return block()
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
    }

    /**
     * Races `onSessionStarted` against polling `currentCastSession.isConnected`
     * for the selected route — `onSessionStarted` has been observed live to
     * simply never fire for a genuinely successful connection, so the wait
     * never trusts a single GMS callback on its own.
     */
    private suspend fun awaitSessionStart(
        sessionManager: SessionManager,
        routeId: String,
        selectRoute: () -> Unit,
    ): Boolean? = withTimeoutOrNull(WAKE_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val mediaRouter = MediaRouter.getInstance(context)
            // Not inside wake()'s outer runCatching — guarded separately.
            val pollJob = CoroutineScope(Dispatchers.Main).launch {
                while (isActive) {
                    val connected = runCatching {
                        mediaRouter.selectedRoute?.id == routeId &&
                            sessionManager.currentCastSession?.isConnected == true
                    }.getOrElse { e ->
                        Log.w("nm-cast-waker", "session poll failed: ${e.message}")
                        if (cont.isActive) cont.resume(false)
                        return@launch
                    }
                    if (connected) {
                        if (cont.isActive) cont.resume(true)
                        return@launch
                    }
                    delay(150L)
                }
            }

            val listener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    sessionManager.removeSessionManagerListener(this, CastSession::class.java)
                    pollJob.cancel()
                    if (cont.isActive) cont.resume(true)
                }

                override fun onSessionStartFailed(session: CastSession, error: Int) {
                    sessionManager.removeSessionManagerListener(this, CastSession::class.java)
                    pollJob.cancel()
                    if (cont.isActive) cont.resume(false)
                }

                override fun onSessionEnded(session: CastSession, error: Int) = Unit
                override fun onSessionEnding(session: CastSession) = Unit
                override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = Unit
                override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
                override fun onSessionStarting(session: CastSession) = Unit
                override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
            }

            sessionManager.addSessionManagerListener(listener, CastSession::class.java)

            // Cast SDK requires SessionManager calls on the main thread, and
            // invokeOnCancellation runs on the cancelling dispatcher — route the
            // listener removal back to the main looper so a timed-out wait does
            // not crash the app with "Must be called from the main thread".
            cont.invokeOnCancellation {
                pollJob.cancel()
                Handler(Looper.getMainLooper()).post {
                    runCatching { sessionManager.removeSessionManagerListener(listener, CastSession::class.java) }
                }
            }

            selectRoute()
        }
    }
}
