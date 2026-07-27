// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import Foundation

/// A glyph and what it announces itself as, which are one choice.
///
/// Written apart, an edit to one is an edit to half of it — a pause glyph
/// announcing itself as Play, invisible to anyone looking at the screen and
/// wrong for everyone who is not. The Compose chrome had exactly that defect and
/// it took a planted test to find it.
public struct TransportGlyph: Equatable, Sendable {
    public let symbol: String
    public let label: String
}

/// The strings the overlay shows.
///
/// Supplied rather than spelled in the view. They are the shortest strings in
/// the product and the ones most likely to differ per language, and a view that
/// hard-coded them is a view nobody can ship outside English.
public struct VideoChromeStrings: Sendable {
    public var play: String = "Play"
    public var pause: String = "Pause"
    public var cast: String = "Cast to TV"
    public var audio: String = "Audio"
    public var subtitles: String = "Subtitles"
    public var quality: String = "Quality"
    public var close: String = "Close"

    public init() {}
}

/// What the overlay draws, derived from the facade in one place.
///
/// Its own type so the derivation can be asserted without a render pass: which
/// glyph, which time, whether a menu is worth offering. A view computing these
/// inline is a view whose only test is a screenshot.
@MainActor
public struct ControlsOverlayModel<Player: VideoChromePlayer> {

    private let player: Player
    private let strings: VideoChromeStrings

    public init(player: Player, strings: VideoChromeStrings = VideoChromeStrings()) {
        self.player = player
        self.strings = strings
    }

    public var transport: TransportGlyph {
        player.isPlaying
            ? TransportGlyph(symbol: "pause.fill", label: strings.pause)
            : TransportGlyph(symbol: "play.fill", label: strings.play)
    }

    public var elapsed: String { formatTime(player.currentTime) }

    /// Remaining rather than total. Somebody deciding whether to start another
    /// episode is asking how much is left.
    public var remaining: String {
        "-" + formatTime(max(0, player.duration - player.currentTime))
    }

    public var progress: Double {
        player.duration <= 0 ? 0 : min(1, max(0, player.currentTime / player.duration))
    }

    /// A menu is worth offering only where there is a choice. One track is not a
    /// menu, it is a row that opens onto itself.
    public var offersAudioMenu: Bool { player.audioOptions.count > 1 }

    /// Subtitles are offered whenever the feature is on, even with nothing
    /// loaded: turning them off is a choice, and so is finding out there are
    /// none.
    public var offersSubtitleMenu: Bool { true }

    public var offersQualityMenu: Bool { player.levels.count > 1 }
}

/// A position, as a clock reads it. Hours only when there are any: a
/// forty-minute episode written 0:40:12 makes a viewer parse a field that is
/// always zero.
public func formatTime(_ seconds: Double) -> String {
    guard seconds.isFinite, seconds >= 0 else { return "0:00" }

    let total = Int(seconds)
    let hours = total / 3600
    let minutes = (total % 3600) / 60
    let remainder = total % 60

    if hours > 0 {
        return String(format: "%d:%02d:%02d", hours, minutes, remainder)
    }
    return String(format: "%d:%02d", minutes, remainder)
}
