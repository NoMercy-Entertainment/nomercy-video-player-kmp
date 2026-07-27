// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import AVFoundation
import Combine

/// One rung of the quality ladder, named by what it is rather than by where it
/// sits in a list.
///
/// The client this replaces selected quality by index, and an index into a
/// ladder the engine has since refiltered picks a different stream than the one
/// whose name was read. It is also unimplementable on AVPlayer, which caps peak
/// bitrate rather than pinning a variant.
public struct QualityOption: Identifiable, Equatable, Sendable {
    public let id: String
    public let label: String
    public let height: Int
    public let bitrate: Int

    public init(id: String, label: String, height: Int, bitrate: Int) {
        self.id = id
        self.label = label
        self.height = height
        self.bitrate = bitrate
    }
}

/// An audio or subtitle track. The same shape for both, because they differ in
/// what they do and not in what a menu row needs to show.
public struct TrackOption: Identifiable, Equatable, Sendable {
    public let id: String
    public let label: String
    public let language: String?

    public init(id: String, label: String, language: String? = nil) {
        self.id = id
        self.label = label
        self.language = language
    }
}

/// Something that went wrong, in the vocabulary the rest of the ecosystem uses.
///
/// The code is the shared `namespace:category/reason` string rather than a Swift
/// enum, so a consumer matching on it matches the same value a web or an Android
/// client would report for the same failure.
public struct PlayerChromeError: Equatable, Sendable {
    public let code: String
    public let message: String
    public let fatal: Bool

    public init(code: String, message: String, fatal: Bool) {
        self.code = code
        self.message = message
        self.fatal = fatal
    }
}

/// The one surface the whole SwiftUI video chrome binds to.
///
/// Every view reads this and calls this, and none of them reaches the engine or
/// an application store. That is the seam the extraction is for: the views this
/// is seeded from each observed the app's own playback controller, which is why
/// none of them could be mounted anywhere else and why none of them could be
/// tested without the whole application behind them.
///
/// The counterpart of Compose's `ChromeState` plus `ChromeCommands`, and
/// deliberately one protocol rather than two: SwiftUI binds to an
/// `ObservableObject`, so the reads have to live on the observed object, and a
/// separate command object would be a second thing every view has to be handed.
@MainActor
public protocol VideoChromePlayer: ObservableObject {
    var isPlaying: Bool { get }
    var currentTime: Double { get }
    var duration: Double { get }
    var bufferedPercent: Double { get }

    var levels: [QualityOption] { get }
    /// Nil is automatic, which is a selection rather than a missing one.
    var selectedQuality: QualityOption? { get }
    /// What automatic is currently playing. A menu showing "Auto" and nothing
    /// else leaves a viewer unable to find out what they are actually watching.
    var actualPlayingLevel: QualityOption? { get }

    var audioOptions: [TrackOption] { get }
    var selectedAudioID: String? { get }
    var subtitleOptions: [TrackOption] { get }
    /// Nil is off, which is a row in the menu rather than an absence.
    var selectedSubtitleID: String? { get }

    var error: PlayerChromeError? { get }

    /// The layer host's surface. Non-optional because the backend always owns
    /// one, and an optional here would put a force-unwrap in every view that
    /// draws the picture.
    var avPlayer: AVPlayer { get }

    func togglePlayPause()
    func play()
    func pause()
    func seek(to seconds: Double)

    func selectQuality(_ option: QualityOption?)
    func selectAudio(_ option: TrackOption)
    func selectSubtitle(_ option: TrackOption?)
}
