// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.touch

import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.core.plugin.PluginOptionField
import tv.nomercy.player.video.input.PlayerCommands

public data class TouchZoneOptions(
    /** How far a double tap seeks. The web's ten seconds. */
    val seekSeconds: Double = TOUCH_SEEK_SECONDS,
    /** How long after a tap a second one still counts as a double. */
    val doubleTapWindowMs: Long = DOUBLE_TAP_THRESHOLD_MS,
)

/**
 * Turns taps on the picture into transport.
 *
 * The same `touch-zones` id the web plugin has, so a consumer moving code
 * across writes the same line:
 *
 *     player.addPlugin(TouchZonesPlugin(commands))
 *
 * [TouchZoneGrid] holds the rules — which third of the picture was hit, whether
 * a second tap arrived in time — and this holds the wiring to the transport.
 * A chrome reports where a finger landed; this decides what it meant.
 *
 * The gesture rules live in [singleTapAction] and [doubleTapAction] and are the
 * surprising half: a single tap means the same thing everywhere EXCEPT the
 * centre, and every seek needs two taps. Seeking on a single tap at the edges
 * is the obvious design and it makes a player that jumps whenever somebody
 * touches it to see the controls.
 */
public open class TouchZonesPlugin(
    private val commands: PlayerCommands,
    opts: TouchZoneOptions = TouchZoneOptions(),
    /**
     * Whether the picture carries volume zones.
     *
     * Added on a touch device rather than removed on a small one: a mouse has
     * a wheel and a finger has nothing.
     */
    private val volumeZones: Boolean = true,
) : Plugin<TouchZoneOptions>() {

    public companion object Manifest : PluginManifest {
        override val id: String = "touch-zones"

        // Two, matching the web plugin this mirrors.
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    // Held rather than fixed: an option a host can edit is one the plugin has
    // to read again afterwards.
    private var opts: TouchZoneOptions = opts

    override val options: TouchZoneOptions get() = opts

    override fun optionFields(): List<PluginOptionField> = listOf(
        PluginOptionField.Number(
            key = "seekSeconds",
            label = "Double-tap seek (seconds)",
            value = opts.seekSeconds,
            min = MIN_TOUCH_SEEK,
            max = MAX_TOUCH_SEEK,
            apply = { chosen -> opts = opts.copy(seekSeconds = chosen) },
        ),
        PluginOptionField.Number(
            key = "doubleTapWindowMs",
            label = "Double-tap window (ms)",
            value = opts.doubleTapWindowMs.toDouble(),
            min = MIN_DOUBLE_TAP_MS,
            max = MAX_DOUBLE_TAP_MS,
            step = TOUCH_STEP_MS,
            apply = { chosen -> opts = opts.copy(doubleTapWindowMs = chosen.toLong()) },
        ),
    )


    private var lastTapAtMs: Long = 0
    private var lastZone: TouchZone? = null

    // Once a double tap opens a run, every further tap in the same zone adds
    // another step to it, the way every other player behaves. The run closes
    // when the taps stop or the viewer moves to a different zone.
    private var runZone: TouchZone? = null

    /**
     * A tap landed at [x], [y] across the picture, at [atMs].
     *
     * Fractions of the picture rather than pixels, so a chrome does not have to
     * tell the plugin how big it is and the rule is the same on a phone and a
     * tablet.
     *
     * Returns what it did, so a chrome can show the matching feedback overlay
     * without working the rule out a second time and disagreeing with it.
     */
    public fun tap(x: Float, y: Float, atMs: Long): TouchAction {
        val zone: TouchZone = zoneAt(x, y, volumeZones)
        val withinWindow: Boolean = atMs - lastTapAtMs <= opts.doubleTapWindowMs
        val sameZone: Boolean = zone == lastZone

        // A run that has gone quiet, or that the viewer has left, is over.
        if (!sameZone || !withinWindow) runZone = null

        val inRun: Boolean = runZone == zone
        val opensRun: Boolean = sameZone && withinWindow

        lastZone = zone
        lastTapAtMs = atMs

        val action: TouchAction = if (inRun || opensRun) {
            runZone = zone
            doubleTapAction(zone)
        } else {
            singleTapAction(zone)
        }

        perform(action)
        return action
    }

    private fun perform(action: TouchAction) {
        when (action) {
            TouchAction.SEEK_BACK -> commands.transport.seekBy(-opts.seekSeconds.toFloat())
            TouchAction.SEEK_FORWARD -> commands.transport.seekBy(opts.seekSeconds.toFloat())
            TouchAction.TOGGLE_PLAYBACK -> commands.transport.togglePlay()
            TouchAction.VOLUME_UP -> commands.volume.volumeUp()
            TouchAction.VOLUME_DOWN -> commands.volume.volumeDown()

            // The chrome owns its own visibility and its own fullscreen; this
            // reports what the gesture meant and does not reach into either.
            TouchAction.TOGGLE_CONTROLS, TouchAction.TOGGLE_FULLSCREEN -> Unit
        }
    }
}

// The range an editor offers. A one-second double tap is a nudge and thirty is
// a scene; below 150ms two deliberate taps stop registering as two.
private const val MIN_TOUCH_SEEK: Double = 1.0
private const val MAX_TOUCH_SEEK: Double = 30.0
private const val MIN_DOUBLE_TAP_MS: Double = 150.0
private const val MAX_DOUBLE_TAP_MS: Double = 1_000.0
private const val TOUCH_STEP_MS: Double = 25.0
