// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// The drop-in player for a screen somebody touches.
///
/// One line in an application and a viewer has a player: the picture, the
/// subtitles over it, the gesture grid over that and the chrome on top. Nothing
/// underneath is hidden — every layer is public and can be taken on its own —
/// but nobody should have to assemble them.
///
/// iOS only, and that is the same line Compose draws. A television has nothing
/// to tap and its chrome is the focus engine; that view is its own.
#if os(iOS)
@available(iOS 15.0, *)
public struct NMVideoChromeView<Player: VideoChromePlayer>: View {

    @ObservedObject private var player: Player
    @StateObject private var visibility: ControlsVisibility
    @ObservedObject private var subtitles: SubtitleRenderer

    private let title: String
    private let subtitle: String
    private let strings: VideoChromeStrings

    // Which player this is, forwarded to the bar. The full one and the
    // trailer differ by their control set, not by their layout.
    private let kind: VideoChromeKind
    private let onCast: (() -> Void)?
    private let onClose: (() -> Void)?
    private let onBrightness: ((Double) -> Void)?

    // The renderer is not defaulted in the signature. A default argument is
    // evaluated in a nonisolated context, and SubtitleRenderer is main-actor
    // bound — which the compiler reports as a call to a main-actor initializer
    // from nowhere in particular. A second init is clearer than an unsafe hop.
    @MainActor
    public init(
        player: Player,
        subtitles: SubtitleRenderer,
        title: String = "",
        subtitle: String = "",
        strings: VideoChromeStrings = VideoChromeStrings(),
        autohide: Bool = true,
        kind: VideoChromeKind = .full,
        onCast: (() -> Void)? = nil,
        onClose: (() -> Void)? = nil,
        onBrightness: ((Double) -> Void)? = nil
    ) {
        self.kind = kind
        self.player = player
        self.subtitles = subtitles
        self.title = title
        self.subtitle = subtitle
        self.strings = strings
        self.onCast = onCast
        self.onClose = onClose
        self.onBrightness = onBrightness

        // The visibility outlives a redraw, so it is the view's own state rather
        // than something rebuilt each time the player publishes. Rebuilt, the
        // autohide timer would restart on every frame of playback and the
        // controls would never go away.
        _visibility = StateObject(
            wrappedValue: ControlsVisibility(isPlaying: { player.isPlaying }, autohide: autohide)
        )
    }

    /// The ordinary case: a player and nothing else.
    @MainActor
    public init(
        player: Player,
        title: String = "",
        subtitle: String = "",
        strings: VideoChromeStrings = VideoChromeStrings(),
        autohide: Bool = true,
        kind: VideoChromeKind = .full,
        onCast: (() -> Void)? = nil,
        onClose: (() -> Void)? = nil,
        onBrightness: ((Double) -> Void)? = nil
    ) {
        self.init(
            player: player,
            subtitles: SubtitleRenderer(),
            title: title,
            subtitle: subtitle,
            strings: strings,
            autohide: autohide,
            kind: kind,
            onCast: onCast,
            onClose: onClose,
            onBrightness: onBrightness
        )
    }

    public var body: some View {
        ZStack {
            PlayerLayerHost(player: player.avPlayer)

            SubtitleOverlay(renderer: subtitles)

            PlayerGestureGrid(
                onSingleTap: {
                    visibility.onTapDown()
                    visibility.bumpActivity()
                    visibility.onSingleTap()
                },
                onAction: handle
            )

            WatchControlsOverlay(
                player: player,
                visibility: visibility,
                title: title,
                subtitle: subtitle,
                strings: strings,
                kind: kind,
                onCast: onCast,
                onClose: onClose
            )
        }
        .onChange(of: player.currentTime) { seconds in
            subtitles.update(currentTime: seconds)
        }
    }

    /// What a gesture asked for, routed to whoever owns it. Seeking and volume
    /// are the player's; brightness goes to the host's closure when it gave
    /// one, and to the screen otherwise.
    func handle(_ action: GestureAction) {
        switch action {
        case .togglePlayPause:
            visibility.bumpActivity()
            player.togglePlayPause()

        case let .seek(delta):
            visibility.bumpActivity()
            player.seek(to: max(0, player.currentTime + delta))

        case let .volume(amount):
            visibility.bumpActivity()
            player.setVolume(min(100, max(0, player.volume + amount * 100)))

        case let .brightness(amount):
            visibility.bumpActivity()
            (onBrightness ?? Self.adjustScreenBrightness)(amount)

        case .nothing:
            break
        }
    }

    private static func adjustScreenBrightness(by amount: Double) {
        let screen = UIScreen.main
        screen.brightness = min(1, max(0, screen.brightness + CGFloat(amount)))
    }
}
#endif
