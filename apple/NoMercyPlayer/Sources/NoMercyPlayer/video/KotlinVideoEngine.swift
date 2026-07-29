// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import AVFoundation
import NoMercyVideoPlayer

/// The shipped chrome, over the shared engine.
///
/// `VideoEngine` had exactly one conformer before this — `FakeVideoEngine`, in
/// the test target — so the SwiftUI chrome had never once been driven by the
/// real player. Every Apple gate was green against a stand-in, and the testbed
/// mounted a play/pause view because there was nothing else to mount.
///
/// A translation layer and nothing else. Every decision it might be tempted to
/// make — which track to pick, when the bar hides, what fits on it — is already
/// in `commonMain`, where Compose reads the same answers. What is here is the
/// vocabulary: Kotlin says `QualityLevel`, Swift says `QualityOption`, and one
/// of them has to say it in the other's words.
@MainActor
public final class KotlinVideoEngine: VideoEngine {

    private let engine: AppleVideoEngine

    public var avPlayer: AVPlayer { engine.avPlayer }

    /// The player, so a host can add plugins to it.
    ///
    /// `player.addPlugin(...)` is the public API of the whole trio. An Apple
    /// consumer that could not reach it would be holding a player with no
    /// plugin surface, which was the state of things until now.
    public var player: NMVideoPlayer { engine.player }

    public init(engine: AppleVideoEngine) {
        self.engine = engine
    }

    /// The ordinary case: its own backend, its own player.
    ///
    /// The backend is named rather than defaulted because a Kotlin constructor
    /// with default arguments does not export a no-argument `init()` to Swift.
    /// It exports the one that takes the parameter and marks the empty form
    /// unavailable, so `AppleVideoEngine()` compiles on the Kotlin side and
    /// fails on the Mac.
    public convenience init() {
        self.init(engine: AppleVideoEngine(backend: AVPlayerVideoBackend()))
    }

    public func observe(_ onState: @escaping (VideoEngineState) -> Void) {
        engine.observe { snapshot in
            onState(Self.stateOf(snapshot))
        }
    }

    public func play() { engine.play() }

    public func pause() { engine.pause() }

    public func seek(to seconds: Double) { engine.seek(seconds: seconds) }

    /// Matched back by identity, not rebuilt.
    ///
    /// The chrome hands back an option it was given, so the level it stands for
    /// is the one at that identity in the list the engine still holds.
    /// Constructing a fresh `QualityLevel` from the three fields the option
    /// carries would drop the codec, the dynamic range and the width, and the
    /// engine would be asked to select a level it never offered.
    public func selectQuality(_ option: QualityOption?) {
        guard let option else {
            engine.tracks.selectQuality(level: nil)
            return
        }
        engine.tracks.selectQuality(level: engine.tracks.qualityLevels().first { Self.idOf($0) == option.id })
    }

    public func selectAudio(_ option: TrackOption) {
        guard let track = engine.tracks.audioTracks().first(where: { $0.id == option.id }) else { return }
        engine.tracks.selectAudio(track: track)
    }

    public func selectSubtitle(_ option: TrackOption?) {
        guard let option else {
            engine.tracks.selectSubtitle(track: nil)
            return
        }
        engine.tracks.selectSubtitle(track: engine.tracks.subtitleTracks().first { $0.id == option.id })
    }

    // A quality level has no id of its own — it is a height, a bitrate and a
    // codec — so one is made from the three, and made in ONE place. Two call
    // sites spelling it slightly differently is a menu whose selection never
    // matches anything.
    private static func idOf(_ level: QualityLevel) -> String {
        "\(level.height)p-\(level.bitrate)-\(level.codec)"
    }

    private static func optionOf(_ level: QualityLevel) -> QualityOption {
        QualityOption(
            id: idOf(level),
            label: level.label ?? "\(level.height)p",
            height: Int(level.height),
            bitrate: Int(level.bitrate)
        )
    }

    private static func optionOf(_ track: AudioTrack) -> TrackOption {
        TrackOption(id: track.id, label: track.label, language: track.language)
    }

    private static func optionOf(_ track: SubtitleTrack) -> TrackOption {
        TrackOption(id: track.id, label: track.label, language: track.language)
    }

    private static func stateOf(_ snapshot: AppleVideoSnapshot) -> VideoEngineState {
        var state = VideoEngineState()
        state.playing = snapshot.playing
        state.currentTime = snapshot.timeSeconds
        state.duration = snapshot.durationSeconds
        state.bufferedPercent = snapshot.bufferedFraction
        state.levels = snapshot.qualityLevels.map(optionOf)
        state.selectedQuality = snapshot.activeQuality.map(optionOf)
        state.audioOptions = snapshot.audioTracks.map(optionOf)
        state.selectedAudioID = snapshot.activeAudio?.id
        state.subtitleOptions = snapshot.subtitleTracks.map(optionOf)
        state.selectedSubtitleID = snapshot.activeSubtitle?.id
        return state
    }
}
