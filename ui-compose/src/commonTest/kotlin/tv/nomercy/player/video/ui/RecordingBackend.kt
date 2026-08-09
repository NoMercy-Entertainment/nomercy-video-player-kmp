// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import tv.nomercy.player.core.ports.BackendEvents
import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.MediaBackend

// An engine that reports like a real one.
//
// The player under the view is the real ComposedPlayer with the real backend
// bridge; only the decoder is stood in for. That matters: the button reads
// stateFlow, and stateFlow is only written when an engine announces something,
// so a fake that stayed silent would leave the button forever on Play and the
// gate would pass by measuring nothing.
open class RecordingBackend : MediaBackend {

    private val listeners: MutableMap<String, MutableList<(Any?) -> Unit>> = mutableMapOf()
    private var playing: Boolean = false
    private var loaded: Boolean = false

    override suspend fun load(url: String, opts: LoadOptions) {
        loaded = true
        emit(BackendEvents.LOAD_START)
        emit(BackendEvents.LOADED_METADATA)
        emit(BackendEvents.CAN_PLAY)
    }

    override suspend fun play() {
        playing = true
        emit(BackendEvents.PLAY)
        emit(BackendEvents.PLAYING)
    }

    override fun pause() {
        playing = false
        emit(BackendEvents.PAUSE)
    }

    override fun stop() {
        playing = false
        emit(BackendEvents.PAUSE)
    }

    override fun release() {
        stop()
        listeners.clear()
    }

    override fun currentTime(): Double = 0.0
    override fun currentTime(seconds: Double): Unit = Unit
    override fun duration(): Double = DURATION_SECONDS
    override fun volume(): Float = 1f
    override fun volume(value: Float): Unit = Unit
    override fun mute(): Unit = Unit
    override fun unmute(): Unit = Unit
    override fun buffered(): Double = DURATION_SECONDS
    override fun playbackRate(): Double = 1.0
    override fun playbackRate(rate: Double): Unit = Unit

    override fun state(): BackendState = when {
        playing -> BackendState.PLAYING
        loaded -> BackendState.PAUSED
        else -> BackendState.IDLE
    }

    override fun on(event: String, fn: (Any?) -> Unit) {
        listeners.getOrPut(event) { mutableListOf() }.add(fn)
    }

    override fun off(event: String, fn: (Any?) -> Unit) {
        listeners[event]?.remove(fn)
    }

    private fun emit(event: String) {
        listeners[event].orEmpty().toList().forEach { it(null) }
    }

    private companion object {
        const val DURATION_SECONDS = 120.0
    }
}
