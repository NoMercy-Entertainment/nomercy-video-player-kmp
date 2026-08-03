// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tv.nomercy.player.core.events.BeforeEvent
import tv.nomercy.player.core.events.CastTarget
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.EventKey
import tv.nomercy.player.core.events.SeekPosition
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.ActionOptions
import tv.nomercy.player.core.player.ActionSource
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.core.ports.CastSender

// While casting, this phone is a remote control and not a second renderer.
//
// That is the whole plugin, and it is structural rather than a rule somebody
// remembers. Every local transport action is intercepted before it happens and
// prevented, then sent to the television instead — so the local engine never
// advances, never decodes, and never plays a second copy of the film into the
// room. A test that only checked "the command reached the set" would pass with
// both playing at once, which is why the acceptance checks the local backend
// stayed still.
//
// The one exception is an action the television itself caused. A pause arriving
// from the set's own stream must not be sent back to it, or the two ends spend
// the evening telling each other to pause.
public open class VideoCastPlugin(
    private val controller: RemoteVideoController,
    private val scope: CoroutineScope,
    private val waker: CastWaker = UnsupportedCastWaker(),
) : Plugin<Unit>(), CastSender {

    public companion object Manifest : PluginManifest {
        // "cast-sender", the web's id, not "video-cast".
        //
        // A renamed plugin id is worse than a missing one. Missing fails at the
        // call site; renamed means a consumer carrying working web code gets
        // getPlugin("cast-sender") returning nothing from a library that does
        // have casting, and nothing in the build says why.
        override val id: String = "cast-sender"
        override val version: String = "1.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    // What the television is doing, for the local chrome to draw.
    //
    // The same flow the controller holds rather than a copy of it: two sources
    // of truth for one session is how a phone's scrubber and a set's disagree.
    public open val castState: StateFlow<RemotePlayerState?> get() = controller.state

    public open val status: StateFlow<RemoteStatus> get() = controller.status

    public open val casting: Boolean get() = controller.status.value == RemoteStatus.CONNECTED

    override fun use() {
        on(CoreEvents.BeforePlay) { event -> route(event) { controller.play() } }
        on(CoreEvents.BeforePause) { event -> route(event) { controller.pause() } }
        on(CoreEvents.BeforeStop) { event -> route(event) { controller.stop() } }
        on(CoreEvents.BeforeNext) { event -> route(event) { controller.next() } }
        on(CoreEvents.BeforePrevious) { event -> route(event) { controller.previous() } }

        on(CoreEvents.BeforeSeek) { event ->
            if (shouldRoute(event.data.source)) {
                event.preventDefault()
                val seconds: Double = event.data.time
                scope.launch { controller.seek(seconds) }
            }
        }

        // Volume and mute are forwarded rather than intercepted, which is the
        // opposite of the transport above and deliberate. There is no second
        // renderer to silence — nothing is decoding here — and the slider a
        // viewer is dragging is bound to the local state, so preventing the
        // local change would freeze the control they are holding while the
        // television obeyed it.
        on(CoreEvents.Volume) { change ->
            if (casting) scope.launch { controller.presentation.setVolumePercent(change.level) }
        }

        on(CoreEvents.Mute) { change ->
            if (casting) scope.launch { controller.presentation.setMuted(change.muted) }
        }

        // Picking an item, which is the half of queue movement next() and
        // previous() do not cover.
        //
        // The television owns the queue, so this moves ITS cursor rather than
        // sending it a url: choosing episode five in the app and having the set
        // stay on episode one is what happened without it.
        on(CoreEvents.Item) { change ->
            if (casting) scope.launch { controller.setPlaylistActive(change.index) }
        }
    }

    // ── CastSender ───────────────────────────────────────────────────────────

    // Hand this item to the television at the position the viewer is at.
    //
    // The player has already paused locally by the time this runs — that order
    // is core's and it is what stops two devices playing the same thing a second
    // apart. What is left here is the launch, and its answer is the set's:
    // false when it refused, so a chrome does not draw a casting state over a
    // television that never started.
    override suspend fun transfer(target: CastTarget, item: PlaylistItem?, position: Double): Boolean {
        val url: String = item?.url ?: return false

        if (waker.wake(target.id) == WakeOutcome.NO_ROUTE) return false

        controller.connect()
        return controller.launch(url, positionSeconds = position)
    }

    // Take it back, and say where the set had got to.
    //
    // Read before disconnecting, because the state flow is what the SSE stream
    // was filling and disconnecting stops it. Null when the set never said,
    // which core treats as "do not seek" rather than seeking to zero — a
    // reclaim that restarted the film would be worse than one that resumed
    // where this device left off.
    override suspend fun reclaim(): Double? {
        val positionMs: Long? = controller.state.value?.positionMs

        controller.disconnect()

        return positionMs?.takeIf { it > 0 }?.let { it / MILLIS_PER_SECOND.toDouble() }
    }

    // Put an item on a television and start following it.
    //
    // The wake comes first and its outcome is answered rather than assumed: a
    // set with no route is one a viewer has to be told about, and launching at
    // it would leave a phone in a casting state with a dark screen in the room.
    public open suspend fun startCast(device: RemoteDevice, url: String): WakeOutcome {
        val outcome: WakeOutcome = waker.wake(device.id)
        if (outcome == WakeOutcome.NO_ROUTE) return outcome

        controller.connect()
        // The url was accepted here and thrown away, so a viewer who started a
        // cast got a connected session showing whatever the set had been on.
        controller.launch(url)
        return outcome
    }

    public open suspend fun stopCast() {
        controller.stop()
        controller.disconnect()
    }

    override fun dispose() {
        controller.disconnect()
    }

    private fun route(event: BeforeEvent<ActionOptions>, command: suspend () -> Unit) {
        if (!shouldRoute(event.data.source)) return

        event.preventDefault()
        scope.launch { command() }
    }

    // Not while local, and not for something the television just told us about.
    //
    // The second half is the loop guard. A pause frame from the set updates the
    // local player, which raises its own before-pause, which without this would
    // be sent straight back to the set that sent it.
    private fun shouldRoute(source: String?): Boolean =
        casting && source != ActionSource.REMOTE
}

private const val MILLIS_PER_SECOND = 1_000
