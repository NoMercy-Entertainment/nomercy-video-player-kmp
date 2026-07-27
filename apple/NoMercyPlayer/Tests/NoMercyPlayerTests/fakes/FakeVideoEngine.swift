// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import AVFoundation
@testable import NoMercyPlayer

/// An engine that reports like a real one, without the framework behind it.
///
/// It stands in for the SKIE-exposed Kotlin player so the facade can be driven
/// on a machine with no XCFramework — and, more usefully, so a test can make the
/// engine announce something the chrome has to react to, which is the half a
/// recording stand-in cannot do.
@MainActor
final class FakeVideoEngine: VideoEngine {

    let avPlayer = AVPlayer()

    private(set) var playCount = 0
    private(set) var pauseCount = 0
    private(set) var recordedSeeks: [Double] = []
    private(set) var recordedQuality: QualityOption?
    private(set) var qualityWasSet = false
    private(set) var recordedAudio: TrackOption?
    private(set) var recordedSubtitle: TrackOption?
    private(set) var subtitleWasSet = false

    private var state = VideoEngineState()
    private var listener: ((VideoEngineState) -> Void)?

    func observe(_ onState: @escaping (VideoEngineState) -> Void) {
        listener = onState
        // The current state first. A chrome that only saw changes would draw an
        // empty player until something moved.
        onState(state)
    }

    /// What the engine says next. Named for what it is rather than "setState":
    /// the point of it is that the facade finds out the same way it will in
    /// production, by being told.
    func emit(_ mutate: (inout VideoEngineState) -> Void) {
        mutate(&state)
        listener?(state)
    }

    func play() {
        playCount += 1
    }

    func pause() {
        pauseCount += 1
    }

    func seek(to seconds: Double) {
        recordedSeeks.append(seconds)
    }

    func selectQuality(_ option: QualityOption?) {
        qualityWasSet = true
        recordedQuality = option
    }

    func selectAudio(_ option: TrackOption) {
        recordedAudio = option
    }

    func selectSubtitle(_ option: TrackOption?) {
        subtitleWasSet = true
        recordedSubtitle = option
    }
}
