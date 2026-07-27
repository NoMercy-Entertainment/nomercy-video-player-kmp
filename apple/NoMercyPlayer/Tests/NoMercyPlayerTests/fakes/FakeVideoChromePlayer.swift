// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import AVFoundation
import Combine
@testable import NoMercyPlayer

/// A player that really changes when it is told to.
///
/// Not a mock. `play()` genuinely flips `isPlaying`, `seek(to:)` genuinely moves
/// the position, and a chosen track genuinely becomes the selected one — because
/// every view test in this package mounts this, and a stand-in that only
/// recorded calls would let a view bind to the wrong property and still pass.
@MainActor
final class FakeVideoChromePlayer: VideoChromePlayer {

    @Published private(set) var isPlaying: Bool = false
    @Published private(set) var currentTime: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var bufferedPercent: Double = 0

    @Published private(set) var levels: [QualityOption] = []
    @Published private(set) var selectedQuality: QualityOption?
    @Published private(set) var actualPlayingLevel: QualityOption?

    @Published private(set) var audioOptions: [TrackOption] = []
    @Published private(set) var selectedAudioID: String?
    @Published private(set) var subtitleOptions: [TrackOption] = []
    @Published private(set) var selectedSubtitleID: String?

    @Published private(set) var error: PlayerChromeError?

    let avPlayer = AVPlayer()

    private(set) var recordedSeeks: [Double] = []
    private(set) var recordedQuality: QualityOption?
    private(set) var qualityWasSet = false

    init(
        isPlaying: Bool = false,
        duration: Double = 0,
        levels: [QualityOption] = [],
        audioOptions: [TrackOption] = [],
        subtitleOptions: [TrackOption] = []
    ) {
        self.isPlaying = isPlaying
        self.duration = duration
        self.levels = levels
        self.audioOptions = audioOptions
        self.subtitleOptions = subtitleOptions
    }

    func togglePlayPause() {
        isPlaying.toggle()
    }

    func play() {
        isPlaying = true
    }

    func pause() {
        isPlaying = false
    }

    func seek(to seconds: Double) {
        recordedSeeks.append(seconds)
        currentTime = seconds
    }

    func selectQuality(_ option: QualityOption?) {
        qualityWasSet = true
        recordedQuality = option
        selectedQuality = option
    }

    func selectAudio(_ option: TrackOption) {
        selectedAudioID = option.id
    }

    func selectSubtitle(_ option: TrackOption?) {
        selectedSubtitleID = option?.id
    }
}
