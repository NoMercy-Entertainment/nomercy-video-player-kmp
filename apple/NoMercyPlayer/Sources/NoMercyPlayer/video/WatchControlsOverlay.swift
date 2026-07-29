// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// The chrome over the picture, on a screen somebody touches.
///
/// It reads the facade and nothing else. The views this is seeded from each
/// observed the application's own playback controller, reached its theme manager
/// for a colour and its cast subsystem for a button — which is why none of them
/// could be mounted anywhere but that one app. What is left here is a view that
/// takes a player and draws it.
///
/// Cast is a callback rather than a feature. Where a session goes and what it
/// looks like is the host's; a library that owned it would be one that knows how
/// the application talks to a television.
@available(iOS 15.0, tvOS 15.0, *)
public struct WatchControlsOverlay<Player: VideoChromePlayer>: View {

    @ObservedObject private var player: Player
    @ObservedObject private var visibility: ControlsVisibility

    private let title: String
    private let subtitle: String
    private let strings: VideoChromeStrings
    private let tint: Color
    private let onCast: (() -> Void)?
    private let onClose: (() -> Void)?

    public let intents: ControlsIntents<Player>

    public init(
        player: Player,
        visibility: ControlsVisibility,
        title: String = "",
        subtitle: String = "",
        strings: VideoChromeStrings = VideoChromeStrings(),
        tint: Color = .white,
        onCast: (() -> Void)? = nil,
        onClose: (() -> Void)? = nil
    ) {
        self.player = player
        self.visibility = visibility
        self.title = title
        self.subtitle = subtitle
        self.strings = strings
        self.tint = tint
        self.onCast = onCast
        self.onClose = onClose
        self.intents = ControlsIntents(player: player, visibility: visibility)
    }

    private var model: ControlsOverlayModel<Player> {
        ControlsOverlayModel(player: player, strings: strings)
    }

    public var body: some View {
        ZStack {
            // The whole picture wakes the chrome, and the tap is decided in two
            // halves because the answer needs what was on screen when the finger
            // landed rather than when it lifted.
            //
            // iOS only, and not merely because onTapGesture arrived on tvOS 16:
            // a television has nothing to tap. Its chrome is driven by the focus
            // engine and the remote, which is a different view entirely. The
            // Compose side draws the same line — tap zones exist on the touch
            // form factors and nowhere else.
            #if os(iOS)
            Color.black.opacity(0.001)
                .contentShape(Rectangle())
                .onTapGesture {
                    intents.tapDown()
                    intents.tapUp()
                }
            #endif

            if visibility.isActive {
                VStack {
                    topBar
                    Spacer()
                    bottomBar
                }
                .transition(.opacity)
            }
        }
        .onChange(of: player.isPlaying) { playing in
            visibility.setPlaying(playing)
        }
        .onAppear {
            visibility.setPlaying(player.isPlaying)
        }
    }

    // Buttons on one side, the title on the other, right-aligned. The web's
    // `.top-bar` is `justify-content: space-between` with the two columns either
    // end of it, and this drew a close cross with the title left-aligned beside
    // it and the cast button off on its own at the far end.
    private var topBar: some View {
        HStack(alignment: .top, spacing: 0) {
            HStack(spacing: PlayerGlyph.buttonGap) {
                // Absent when the host has nowhere to cast to. A control
                // somebody presses to find out it does nothing is worse than one
                // that is not there.
                if let onCast {
                    Button(action: onCast) {
                        PlayerGlyph(FluentIcons.cast)
                    }
                    .accessibilityLabel(strings.cast)
                }

                if let onClose {
                    Button(action: onClose) {
                        PlayerGlyph(FluentIcons.close)
                    }
                    .accessibilityLabel(strings.close)
                }
            }

            Spacer(minLength: 0)

            VStack(alignment: .trailing, spacing: 2) {
                Text(title).font(.headline)
                // Absent rather than blank: an empty line is a gap a viewer
                // reads as something that failed to load.
                if !subtitle.isEmpty {
                    Text(subtitle).font(.subheadline)
                }
            }
            .multilineTextAlignment(.trailing)
        }
        .foregroundColor(tint)
        .padding()
    }

    private var bottomBar: some View {
        VStack(spacing: 8) {
            HStack {
                Text(model.elapsed)
                Spacer()
                Text(model.remaining)
            }
            .font(.caption)
            .foregroundColor(tint)

            // The web's order, from its own builder: play, previous, the chapter
            // jumps, next, volume, then the menus. Not the order they were
            // thought of, and not four of eighteen — every one this protocol can
            // drive is here, each gated on the same question the browser asks.
            //
            // Audio wears the globe rather than a waveform because that is the
            // glyph the browser's audio button carries.
            HStack(spacing: 24) {
                Button(action: intents.togglePlayPause) {
                    PlayerGlyph(model.transport.icon)
                }
                .accessibilityLabel(model.transport.label)
                .accessibilityIdentifier("nmPlayPause")

                // Dimmed and unpressable at the ends of a playlist rather than
                // removed. The web disables rather than hides, and the
                // difference is not cosmetic: a control that vanishes on the
                // first item and returns on the second reflows the whole bar,
                // so every other control moves under the finger reaching for one.
                Button(action: player.previous) {
                    PlayerGlyph(FluentIcons.previous)
                }
                .disabled(!player.hasPrevious)
                .accessibilityLabel(strings.previous)

                if !player.chapters.isEmpty {
                    Button(action: player.chapterBack) {
                        PlayerGlyph(FluentIcons.chapterBack)
                    }
                    .accessibilityLabel(strings.chapterBack)

                    Button(action: player.chapterForward) {
                        PlayerGlyph(FluentIcons.chapterForward)
                    }
                    .accessibilityLabel(strings.chapterForward)
                }

                Button(action: player.next) {
                    PlayerGlyph(FluentIcons.next)
                }
                .disabled(!player.hasNext)
                .accessibilityLabel(strings.next)

                Button { player.setMuted(!player.isMuted) } label: {
                    PlayerGlyph(model.volumeIcon)
                }
                .accessibilityLabel(strings.mute)

                if model.offersSubtitleMenu {
                    Button {
                        intents.setMenuOpen(true)
                    } label: {
                        PlayerGlyph(FluentIcons.subtitles)
                    }
                    .accessibilityLabel(strings.subtitles)
                }

                if model.offersAudioMenu {
                    Button {
                        intents.setMenuOpen(true)
                    } label: {
                        PlayerGlyph(FluentIcons.language)
                    }
                    .accessibilityLabel(strings.audio)
                }

                if model.offersQualityMenu {
                    Button {
                        intents.setMenuOpen(true)
                    } label: {
                        PlayerGlyph(FluentIcons.quality)
                    }
                    .accessibilityLabel(strings.quality)
                }

                // Last on the row, as it is in the browser, and drawing the exit
                // glyph while fullscreen rather than the same one both ways.
                Button { player.setFullscreen(!player.isFullscreen) } label: {
                    PlayerGlyph(player.isFullscreen ? FluentIcons.exitFullscreen : FluentIcons.fullscreen)
                }
                .accessibilityLabel(player.isFullscreen ? strings.exitFullscreen : strings.fullscreen)
            }
            .foregroundColor(tint)
        }
        .padding()
    }
}
