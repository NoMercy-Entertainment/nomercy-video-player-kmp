// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import NoMercyVideoPlayer
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

    private let kind: VideoChromeKind

    public init(
        player: Player,
        visibility: ControlsVisibility,
        title: String = "",
        subtitle: String = "",
        strings: VideoChromeStrings = VideoChromeStrings(),
        tint: Color = .white,
        kind: VideoChromeKind = .full,
        onCast: (() -> Void)? = nil,
        onClose: (() -> Void)? = nil
    ) {
        self.kind = kind
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
                // Measured, because what the bar draws depends on how much room
                // it has and SwiftUI will not tell a view its width any other
                // way. Without this the row asked for the width of eighteen
                // buttons — about 930 points — on a 393 point phone, and a
                // SwiftUI parent does not clip an oversized child: it sizes
                // itself to fit it. So the picture, the pane and finally the
                // host application all came out wider than the screen and were
                // centred in it, clipping their own titles at one edge and
                // their content at the other.
                GeometryReader { geometry in
                    VStack {
                        topBar
                        Spacer()
                        bottomBar(
                            width: Int(geometry.size.width),
                            portrait: geometry.size.height >= geometry.size.width
                        )
                    }
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

    /// What this item cannot offer, which is the web's rule 2.
    ///
    /// Answered once, here, instead of at each button. A control the item cannot
    /// offer is not on the bar and costs the row no width — so a film without
    /// chapters must not push the fullscreen button off the end.
    private var unavailable: Set<ChromeControl> {
        var absent: Set<ChromeControl> = []
        if player.chapters.isEmpty { absent.formUnion([.chapterPrev, .chapterNext]) }
        if !model.offersSubtitleMenu { absent.insert(.subtitles) }
        if !model.offersAudioMenu { absent.insert(.audio) }
        if !model.offersQualityMenu { absent.insert(.quality) }
        if !player.hasPlaylist { absent.insert(.playlist) }
        return absent
    }

    private func bottomBar(width: Int, portrait: Bool) -> some View {
        // The shared rule, not a Swift copy of it. `visibleControlsIn` calls the
        // same `visibleControls` the Compose bar calls, so a viewer moving
        // between an iPhone and a desktop finds the same control disappearing at
        // the same width — which is the entire promise of one player across
        // three toolkits.
        //
        // noHover is true because nothing here has a pointer. It is not a
        // shortcut: on the web the volume slider expands beside mute when there
        // IS one, and the bar has to hold room for that expansion before it
        // happens. With no pointer, mute costs what any other button costs.
        let fits = Set(
            ChromeResponsiveKt.visibleControlsIn(
                widthDp: Int32(width),
                enabled: kind.controls,
                unavailable: unavailable,
                portrait: portrait,
                noHover: true
            )
        )

        // ONE row, as `.bottom-row` is.
        //
        // The clocks sat on a line of their own above the controls, which put
        // the elapsed time at the far left of the picture and the remaining time
        // at the far right with nothing between them. The browser threads both
        // through the SAME row — transport, volume, elapsed, the flex divider,
        // remaining, then the view toggles — and it is the divider that pushes
        // the right-hand group to the edge. A separate row is a different bar.
        //
        // The order below is the web's own builder: play, previous, the chapter
        // jumps, next, volume, then the menus. Order and survival stay separate
        // decisions exactly as they are in the Compose bar — this array says
        // WHERE a control goes, `fits` says WHETHER it is drawn.
        let drawn: [ChromeControl] = layoutOrder.filter(fits.contains)

        return HStack(spacing: WebBar.controlGap) {
            ForEach(drawn, id: \.self) { control in
                button(for: control)
            }

            Text(model.elapsed)
                .font(.system(size: WebBar.clockSize, design: .monospaced))
                .foregroundColor(WebBar.clockTint)
                .padding(.leading, WebBar.clockMargin)

            // `.divider { flex: 1 }` — the element that splits the bar. Without
            // it every control clumps against one edge.
            Spacer(minLength: WebBar.dividerMinWidth)

            Text(model.remaining)
                .font(.system(size: WebBar.clockSize, design: .monospaced))
                .foregroundColor(WebBar.clockTint)
                .padding(.trailing, WebBar.clockMargin)
        }
        .foregroundColor(tint)
        .frame(height: WebBar.rowHeight)
        .padding(.vertical, WebBar.rowPaddingVertical)
        .padding(.horizontal, WebBar.rowPaddingHorizontal)
    }


    // Audio wears the globe rather than a waveform because that is the glyph the
    // browser's audio button carries.
    @ViewBuilder
    private func button(for control: ChromeControl) -> some View {
        switch control {
        case .play:
            Button(action: intents.togglePlayPause) {
                PlayerGlyph(model.transport.icon)
            }
            .accessibilityLabel(model.transport.label)
            .accessibilityIdentifier("nmPlayPause")

        // Dimmed and unpressable at the ends of a playlist rather than removed.
        // The web disables rather than hides, and the difference is not
        // cosmetic: a control that vanishes on the first item and returns on the
        // second reflows the whole bar, so every other control moves under the
        // finger reaching for one.
        case .previous:
            Button(action: player.previous) {
                PlayerGlyph(FluentIcons.previous)
            }
            .disabled(!player.hasPrevious)
            .accessibilityLabel(strings.previous)

        case .seekBack:
            Button(action: player.seekBack) {
                PlayerGlyph(FluentIcons.seekBack)
            }
            .accessibilityLabel(strings.seekBack)

        case .seekForward:
            Button(action: player.seekForward) {
                PlayerGlyph(FluentIcons.seekForward)
            }
            .accessibilityLabel(strings.seekForward)

        case .chapterPrev:
            Button(action: player.chapterBack) {
                PlayerGlyph(FluentIcons.chapterBack)
            }
            .accessibilityLabel(strings.chapterBack)

        case .chapterNext:
            Button(action: player.chapterForward) {
                PlayerGlyph(FluentIcons.chapterForward)
            }
            .accessibilityLabel(strings.chapterForward)

        case .next:
            Button(action: player.next) {
                PlayerGlyph(FluentIcons.next)
            }
            .disabled(!player.hasNext)
            .accessibilityLabel(strings.next)

        case .mute:
            Button { player.setMuted(!player.isMuted) } label: {
                PlayerGlyph(model.volumeIcon)
            }
            .accessibilityLabel(strings.mute)

        case .aspectRatio:
            Button { player.setAspectRatio(model.nextAspect) } label: {
                PlayerGlyph(model.aspectIcon)
            }
            .accessibilityLabel(strings.aspectRatio)

        case .theater:
            Button { player.setTheater(!player.isTheater) } label: {
                PlayerGlyph(player.isTheater ? FluentIcons.theaterExit : FluentIcons.theater)
            }
            .accessibilityLabel(strings.theater)

        case .pip:
            Button { player.setPictureInPicture(!player.isPictureInPicture) } label: {
                PlayerGlyph(player.isPictureInPicture ? FluentIcons.pipExit : FluentIcons.pipEnter)
            }
            .accessibilityLabel(strings.pictureInPicture)

        case .speed:
            Button { intents.setMenuOpen(true) } label: {
                PlayerGlyph(FluentIcons.speed)
            }
            .accessibilityLabel(strings.speed)

        case .subtitles:
            Button { intents.setMenuOpen(true) } label: {
                PlayerGlyph(FluentIcons.subtitles)
            }
            .accessibilityLabel(strings.subtitles)

        case .audio:
            Button { intents.setMenuOpen(true) } label: {
                PlayerGlyph(FluentIcons.language)
            }
            .accessibilityLabel(strings.audio)

        case .quality:
            Button { intents.setMenuOpen(true) } label: {
                PlayerGlyph(FluentIcons.quality)
            }
            .accessibilityLabel(strings.quality)

        case .playlist:
            Button { intents.setMenuOpen(true) } label: {
                PlayerGlyph(FluentIcons.playlist)
            }
            .accessibilityLabel(strings.playlist)

        case .settings:
            Button { intents.setMenuOpen(true) } label: {
                PlayerGlyph(FluentIcons.settings)
            }
            .accessibilityLabel(strings.settings)

        // Last on the row, as it is in the browser, and drawing the exit glyph
        // while fullscreen rather than the same one both ways.
        case .fullscreen:
            Button { player.setFullscreen(!player.isFullscreen) } label: {
                PlayerGlyph(player.isFullscreen ? FluentIcons.exitFullscreen : FluentIcons.fullscreen)
            }
            .accessibilityLabel(player.isFullscreen ? strings.exitFullscreen : strings.fullscreen)

        // The level itself, which on the web is a slider that expands beside
        // mute. There is no slider on a touch bar — the volume rocker is the
        // device's — so this bar has no control to draw for it.
        case .volume:
            EmptyView()
        }
    }
}

/// Where a control goes, in the browser's own layout order.
private let layoutOrder: [ChromeControl] = [
    .play,
    .previous,
    .seekBack,
    .seekForward,
    .chapterPrev,
    .chapterNext,
    .next,
    .mute,
    .aspectRatio,
    .theater,
    .pip,
    .speed,
    .subtitles,
    .audio,
    .quality,
    .playlist,
    .settings,
    .fullscreen,
]

/// Rule 1: what this bar can draw at all.
///
/// Everything in [layoutOrder], which is every ranked control except the
/// volume slider. Passed rather than assumed, because the rule charges width
/// for what a bar declares it can show, and declaring a control it cannot
/// draw would take room from one it can.
private let drawable: Set<ChromeControl> = Set(layoutOrder)

/// Which player this bar is, which is a smaller question than which platform.
///
/// The counterpart of Compose's `VideoUiKind`, and the same two members for the
/// same reason: an application has a full player on a watch page and a trailer
/// over a detail page, and the trailer is not a narrow full player. It is a
/// different set of controls — no queue to step through, no chapters to jump
/// between, no settings to open — and the responsive rule then lays out
/// whatever it is given.
public enum VideoChromeKind {
    case full
    case trailer

    /// The controls this kind offers the rule.
    ///
    /// Read off the same list ChromeButtons uses on the Compose side, so a
    /// trailer draws the same controls on a phone as it does on a desktop and
    /// the difference between the two players is one set rather than two
    /// layouts.
    var controls: Set<ChromeControl> {
        switch self {
        case .full:
            return drawable
        case .trailer:
            return [.play, .mute, .subtitles, .fullscreen]
        }
    }
}

/// The browser's own bottom row, in numbers.
///
/// `.bottom-row { height: 40px; gap: 2px; padding: 4px 16px }`, `.divider
/// { min-width: 16px }`, `.time { font-size: 0.82rem; color: rgb(221,221,221) }`
/// — measured off the running player rather than read off the stylesheet, and
/// the same values `scripts/check-render-paint.py` grades the Compose bar
/// against.
///
/// File-level because the view is generic and Swift has no static stored
/// properties on a generic type.
private enum WebBar {
    static let rowHeight: CGFloat = 40
    static let controlGap: CGFloat = 2
    static let rowPaddingVertical: CGFloat = 4
    static let rowPaddingHorizontal: CGFloat = 16
    static let dividerMinWidth: CGFloat = 16
    static let clockMargin: CGFloat = 8
    static let clockSize: CGFloat = 13.12
    static let clockTint = Color(white: 221.0 / 255.0)
}
