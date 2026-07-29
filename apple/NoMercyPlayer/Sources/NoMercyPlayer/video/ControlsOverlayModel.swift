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
    /// The web's own glyph, not an SF Symbol. `play.fill` and `pause.fill` are
    /// Apple's triangles and they are not the shapes the browser draws, so a
    /// viewer moving between the two players was looking at a different button.
    public let icon: FluentIcon
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

    // The rest of the transport row's labels, in the browser's wording from
    // desktop-ui/i18n/en.ts. "Previous chapter" rather than "Back a chapter"
    // because that is what a viewer of the web player has already read.
    public var previous: String = "Previous"
    public var next: String = "Next"
    public var chapterBack: String = "Previous chapter"
    public var chapterForward: String = "Next chapter"
    public var mute: String = "Mute / Unmute"
    public var fullscreen: String = "Fullscreen"
    public var exitFullscreen: String = "Exit fullscreen"

    // The rest of the row, in the browser's wording from desktop-ui/i18n/en.ts.
    public var seekBack: String = "Seek back 10 s"
    public var seekForward: String = "Seek forward 10 s"
    public var aspectRatio: String = "Aspect ratio"
    public var theater: String = "Theater mode"
    public var pictureInPicture: String = "Picture-in-picture"
    public var speed: String = "Playback speed"
    public var playlist: String = "Playlist"
    public var settings: String = "Settings"

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
            ? TransportGlyph(icon: FluentIcons.pause, label: strings.pause)
            : TransportGlyph(icon: FluentIcons.play, label: strings.play)
    }

    /// The speaker, at the level it is actually at.
    ///
    /// Four glyphs rather than two, as the browser has: muted, and then low,
    /// medium or high. A bar that only knew loud from silent told a viewer
    /// nothing about where the slider they just moved had landed.
    public var volumeIcon: FluentIcon {
        if player.isMuted || player.volume <= 0 { return FluentIcons.volumeMuted }
        if player.volume < volumeLowCeiling { return FluentIcons.volumeLow }
        // At or below, as the web's medium arm is. A strict comparison puts the
        // speaker one glyph higher at exactly sixty, which is where a slider
        // dragged to a round number lands.
        if player.volume <= volumeMediumCeiling { return FluentIcons.volumeMedium }
        return FluentIcons.volumeHigh
    }

    /// The aspect button's glyph, which says which fitting is in effect.
    ///
    /// Three glyphs for four modes, as the web has: `none` and `uniform` both
    /// draw the fit icon there, because neither crops and the difference is not
    /// something an icon can carry.
    public var aspectIcon: FluentIcon {
        switch player.aspectRatio {
        case .fill: return FluentIcons.aspectFill
        case .exactfit: return FluentIcons.aspectOriginal
        case .uniform, .none: return FluentIcons.aspectFit
        }
    }

    /// The fitting one press moves to, cycling in the web's order.
    ///
    /// Derived here rather than in the view so pressing the button twice on
    /// either player lands on the same picture — the cycle IS the behaviour,
    /// and a view that computed its own would drift the moment a case is added.
    public var nextAspect: AspectFitting {
        let order = AspectFitting.allCases
        let index = order.firstIndex(of: player.aspectRatio) ?? 0
        return order[(index + 1) % order.count]
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

/// Where the speaker changes shape, on the browser's own scale.
///
/// `desktop-ui/helpers/buttonState.ts`: `volume < 30` is low and `volume <= 60`
/// is medium. Volume is a percentage across the whole ecosystem, so these are
/// thirty and sixty rather than fractions.
private let volumeLowCeiling: Double = 30
private let volumeMediumCeiling: Double = 60
