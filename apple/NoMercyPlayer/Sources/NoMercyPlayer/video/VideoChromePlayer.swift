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

    /// What the transport row needs beyond play and pause.
    ///
    /// The bar drew four controls where the browser draws eighteen, and the
    /// reason was here rather than in the view: there was nothing to call. A
    /// chrome cannot offer a next-episode button to a protocol with no next.
    ///
    /// Defaulted in the extension below so an existing conformer keeps
    /// compiling and reports honestly — a build with no playlist says it has no
    /// next item, and the button is absent rather than dead.
    var hasNext: Bool { get }
    var hasPrevious: Bool { get }
    var chapters: [ChapterMark] { get }

    var volume: Double { get }
    var isMuted: Bool { get }
    var isFullscreen: Bool { get }

    /// The rest of the web's row: the two view modes, the rate, the fitting and
    /// whether there is a queue worth opening.
    ///
    /// Reads rather than commands, because each of these controls draws
    /// differently depending on the answer — theater and picture-in-picture
    /// swap their glyph when active, and the speed and aspect menus mark what
    /// is currently set. A command-only protocol gives a button that toggles
    /// something and never says which way it went.
    var isTheater: Bool { get }
    var isPictureInPicture: Bool { get }
    var rate: Double { get }
    var aspectRatio: AspectFitting { get }
    var hasPlaylist: Bool { get }

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

    func next()
    func previous()

    /// Jump to the chapter boundary either side of where the film is.
    ///
    /// Separate from `seek(to:)` because the chrome does not know where the
    /// boundaries are until it has the marks, and asking it to work them out
    /// puts the same arithmetic in every chrome that draws the buttons.
    func chapterBack()
    func chapterForward()

    func setVolume(_ percent: Double)
    func setMuted(_ muted: Bool)
    func setFullscreen(_ fullscreen: Bool)

    /// Ten seconds either way, which is what the web's two seek buttons do and
    /// what their labels say. A step the consumer could choose would be a step
    /// that disagrees with the label the library ships.
    func seekBack()
    func seekForward()

    func setTheater(_ theater: Bool)
    func setPictureInPicture(_ pip: Bool)
    func setRate(_ rate: Double)
    func setAspectRatio(_ fitting: AspectFitting)
}

/// How the picture is fitted, with the web's four tokens.
///
/// The same values Compose's `Stretching` carries and the same spellings —
/// `exactfit`, not `uniformFill` — because a preference stored by one client is
/// read by another and a renamed token is a value the other end throws on.
public enum AspectFitting: String, CaseIterable, Sendable {
    case uniform
    case fill
    case exactfit
    case none
}

/// A chapter boundary, as the bar and the preview bubble read it.
///
/// The same two fields Compose's `TvChapter` carries and the same nullable
/// title, because a source that gives a boundary without a name is normal and a
/// chrome that required one would drop the boundary with it.
public struct ChapterMark: Equatable, Sendable {
    public let startSeconds: Double
    public let title: String?

    public init(startSeconds: Double, title: String? = nil) {
        self.startSeconds = startSeconds
        self.title = title
    }
}

/// What a build that has not wired these yet honestly reports.
///
/// Defaults rather than requirements so adding them breaks nobody, and each one
/// is the answer that makes its control disappear rather than the answer that
/// makes it appear and do nothing. A button somebody presses to find out it is
/// dead is worse than a button that was never offered.
public extension VideoChromePlayer {
    var hasNext: Bool { false }
    var hasPrevious: Bool { false }
    var chapters: [ChapterMark] { [] }

    /// A percentage, as it is everywhere else in the ecosystem. Full by default:/n    /// a build that has not wired volume is not a silent one.
    var volume: Double { 100 }
    var isMuted: Bool { false }
    var isFullscreen: Bool { false }

    func next() {}
    func previous() {}

    /// The boundary before the one the film is in, or the start of the film.
    ///
    /// A back press part-way through a chapter goes to the top of that chapter,
    /// not the one before it — the same rule the web uses and the one every
    /// music player taught people to expect. Within the grace of a boundary it
    /// steps to the previous one instead, or a viewer pressing back twice in a
    /// row never leaves the chapter they are in. Falls back to the start of the
    /// film when there is no earlier boundary, as `previousChapter` does.
    func chapterBack() {
        let marks = chapters.map(\.startSeconds).sorted()
        let target = marks.last { $0 < currentTime - chapterGrace } ?? 0
        seek(to: target)
    }

    /// Nothing when already past the last boundary, which is `nextChapter`'s
    /// no-op rather than a seek to the end of the film.
    ///
    /// The grace applies on this side too. It is not symmetry for its own sake:
    /// without it, a forward press landing exactly on a boundary picks that same
    /// boundary and the film does not move.
    func chapterForward() {
        let marks = chapters.map(\.startSeconds).sorted()
        guard let target = marks.first(where: { $0 > currentTime + chapterGrace }) else { return }
        seek(to: target)
    }

    func setVolume(_ percent: Double) {}
    func setMuted(_ muted: Bool) {}
    func setFullscreen(_ fullscreen: Bool) {}

    var isTheater: Bool { false }
    var isPictureInPicture: Bool { false }
    var rate: Double { 1 }
    var aspectRatio: AspectFitting { .uniform }
    var hasPlaylist: Bool { false }

    /// Clamped at both ends. A seek past the end is a seek to the end, and one
    /// before the start is a seek to zero — an unclamped step is how a button
    /// press near a boundary produces an error instead of a picture.
    func seekBack() {
        seek(to: max(0, currentTime - seekStep))
    }

    func seekForward() {
        seek(to: min(duration, currentTime + seekStep))
    }

    func setTheater(_ theater: Bool) {}
    func setPictureInPicture(_ pip: Bool) {}
    func setRate(_ rate: Double) {}
    func setAspectRatio(_ fitting: AspectFitting) {}
}

/// The step both seek buttons take, in seconds.
///
/// Ten, which is the number in the web's own labels — "Seek back 10 s". A
/// different step here would make the library's own tooltip a lie.
private let seekStep: Double = 10

/// How close to a boundary still counts as being on it, in seconds.
///
/// One, which is what desktop-ui/helpers/chapters.ts uses on both sides —
/// `start < currentTime - 1` going back and `start > currentTime + 1` going
/// forward. Read off that file rather than picked: a different figure here
/// would make the same press land somewhere else than it does in a browser.
private let chapterGrace: Double = 1
