// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package tv.nomercy.player.video.backend

// The DOM surface Html5VideoBackend drives, modelled as external JsAny
// interfaces rather than pulled in from a DOM binding library — Kotlin/Wasm
// JS interop has no `dynamic`, and this repo's convention (CafExternal.kt,
// WebReceiverClientCapabilities.kt) is a hand-written external declaration
// per member actually used, not a whole-lib.dom surface nothing here needs.

internal external interface JsTimeRanges : JsAny {
    val length: Int
}

@JsFun("(ranges, i) => ranges.start(i)")
internal external fun jsTimeRangeStart(ranges: JsTimeRanges, index: Int): Double

@JsFun("(ranges, i) => ranges.end(i)")
internal external fun jsTimeRangeEnd(ranges: JsTimeRanges, index: Int): Double

internal external interface JsTextTrack : JsAny {
    val kind: JsString
    val language: JsString?
    val label: JsString?
    var mode: JsString
}

internal external interface JsTextTrackList : JsAny {
    val length: Int
}

@JsFun("(list, i) => list[i]")
internal external fun jsTextTrackAt(list: JsTextTrackList, index: Int): JsTextTrack

internal external interface JsAudioTrack : JsAny {
    val id: JsString
    val language: JsString?
    val label: JsString?
    var enabled: Boolean
}

internal external interface JsAudioTrackList : JsAny {
    val length: Int
}

@JsFun("(list, i) => list[i]")
internal external fun jsAudioTrackAt(list: JsAudioTrackList, index: Int): JsAudioTrack

internal external interface JsMediaError : JsAny {
    val code: Int
    val message: JsString?
}

// The subset of HTMLVideoElement this backend touches. `audioTracks` is a
// non-standard Chrome/Edge extension (absent from lib.dom's own strict
// surface too) — nullable so a browser without it degrades to zero audio
// tracks rather than throwing.
internal external interface JsVideoElement : JsAny {
    var src: JsString
    var currentTime: Double
    val duration: Double
    var volume: Double
    var muted: Boolean
    var playbackRate: Double
    var preload: JsString
    val paused: Boolean
    val ended: Boolean
    val readyState: Int
    val videoWidth: Int
    val videoHeight: Int
    val buffered: JsTimeRanges
    val seekable: JsTimeRanges
    val textTracks: JsTextTrackList
    val audioTracks: JsAudioTrackList?
    val error: JsMediaError?

    fun play(): JsAny?
    fun pause()
    fun load()
    fun addEventListener(type: JsString, listener: (JsAny) -> Unit)
    fun removeEventListener(type: JsString, listener: (JsAny) -> Unit)
    fun removeAttribute(name: JsString)
}

@JsFun("() => document.createElement('video')")
internal external fun jsCreateVideoElement(): JsVideoElement

@JsFun("(el) => { el.pause(); el.removeAttribute('src'); el.load(); }")
internal external fun jsResetVideoElement(el: JsVideoElement)
