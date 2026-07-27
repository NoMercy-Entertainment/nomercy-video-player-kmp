// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import AVFoundation
import Combine

/// One frame of what the engine says about itself.
///
/// A value rather than a stream of individual signals, because that is what the
/// Kotlin side already produces: one `PlayerState` per change. Two separate
/// publishers for "playing" and "position" is how a chrome comes to draw a pause
/// glyph over a position from before the track changed.
public struct VideoEngineState: Equatable, Sendable {
    public var playing: Bool = false
    public var currentTime: Double = 0
    public var duration: Double = 0
    public var bufferedPercent: Double = 0

    public var levels: [QualityOption] = []
    public var selectedQuality: QualityOption?
    public var actualPlayingLevel: QualityOption?

    public var audioOptions: [TrackOption] = []
    public var selectedAudioID: String?
    public var subtitleOptions: [TrackOption] = []
    public var selectedSubtitleID: String?

    public var error: PlayerChromeError?

    public init() {}
}

/// What the facade needs of an engine.
///
/// Deliberately not the SKIE-exposed Kotlin type. The chrome has to be testable
/// on a machine with no XCFramework, and — more importantly — the shape of what
/// the chrome asks for should be readable in one place rather than inferred from
/// which Kotlin methods happen to be called. The real engine conforms to this in
/// one adapter; everything else in this package is written against it.
@MainActor
public protocol VideoEngine: AnyObject {
    var avPlayer: AVPlayer { get }

    /// Called with every state the engine reports, starting with the current
    /// one. Starting with the current one matters: a chrome that only saw
    /// changes would draw an empty player until something moved.
    func observe(_ onState: @escaping (VideoEngineState) -> Void)

    func play()
    func pause()
    func seek(to seconds: Double)

    func selectQuality(_ option: QualityOption?)
    func selectAudio(_ option: TrackOption)
    func selectSubtitle(_ option: TrackOption?)
}

/// The engine, as SwiftUI can observe it.
///
/// A projection and a forwarder, and nothing else. No playback decision is made
/// here: what plays is the engine's, and this only republishes what it said and
/// passes on what the viewer pressed. The moment a rule lives here it is a rule
/// the Compose chrome does not have, and the two clients start disagreeing.
@MainActor
public final class EngineVideoPlayer<Engine: VideoEngine>: VideoChromePlayer {

    @Published public private(set) var isPlaying: Bool = false
    @Published public private(set) var currentTime: Double = 0
    @Published public private(set) var duration: Double = 0
    @Published public private(set) var bufferedPercent: Double = 0

    @Published public private(set) var levels: [QualityOption] = []
    @Published public private(set) var selectedQuality: QualityOption?
    @Published public private(set) var actualPlayingLevel: QualityOption?

    @Published public private(set) var audioOptions: [TrackOption] = []
    @Published public private(set) var selectedAudioID: String?
    @Published public private(set) var subtitleOptions: [TrackOption] = []
    @Published public private(set) var selectedSubtitleID: String?

    @Published public private(set) var error: PlayerChromeError?

    private let engine: Engine

    public var avPlayer: AVPlayer { engine.avPlayer }

    public init(engine: Engine) {
        self.engine = engine
        engine.observe { [weak self] state in
            self?.apply(state)
        }
    }

    /// Read off the engine rather than remembered. A player that pauses itself —
    /// the end of a track, an interruption, another app taking audio focus —
    /// leaves a remembered flag lying, and a button that lies about what the
    /// player is doing is worse than no button.
    public func togglePlayPause() {
        if isPlaying {
            engine.pause()
        } else {
            engine.play()
        }
    }

    public func play() {
        engine.play()
    }

    public func pause() {
        engine.pause()
    }

    public func seek(to seconds: Double) {
        engine.seek(to: seconds)
    }

    public func selectQuality(_ option: QualityOption?) {
        engine.selectQuality(option)
    }

    public func selectAudio(_ option: TrackOption) {
        engine.selectAudio(option)
    }

    public func selectSubtitle(_ option: TrackOption?) {
        engine.selectSubtitle(option)
    }

    private func apply(_ state: VideoEngineState) {
        isPlaying = state.playing
        currentTime = state.currentTime
        duration = state.duration
        bufferedPercent = state.bufferedPercent

        levels = state.levels
        selectedQuality = state.selectedQuality
        actualPlayingLevel = state.actualPlayingLevel

        audioOptions = state.audioOptions
        selectedAudioID = state.selectedAudioID
        subtitleOptions = state.subtitleOptions
        selectedSubtitleID = state.selectedSubtitleID

        error = state.error
    }
}
