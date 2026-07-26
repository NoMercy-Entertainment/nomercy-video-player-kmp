// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import AVFoundation
import SwiftUI

/// The drop-in player: a surface with one control over it.
///
/// The same shape as the Compose view and for the same reason — an app can mount
/// a working player before it has designed one. It is deliberately not the full
/// chrome; a skeleton that grew a scrubber would stop being the thing you can
/// drop in on day one.
///
/// On tvOS the play/pause button on the remote drives it, because there is
/// nothing to aim at ten feet from a television. Menu is deliberately left
/// alone: a player that answers the button used to leave it is a trap.
public struct NMVideoPlayerView: View {

    @ObservedObject private var state: PlayerStateObserver
    private let player: AVPlayer
    private let transport: PlayerTransport

    public init(player: AVPlayer, transport: PlayerTransport, state: PlayerStateObserver) {
        self.player = player
        self.transport = transport
        self.state = state
    }

    public var body: some View {
        ZStack {
            PlayerLayerHost(player: player)
            PlayPauseControl(isPlaying: state.isPlaying) { toggle() }
        }
        #if os(tvOS)
        .focusable()
        .onPlayPauseCommand { toggle() }
        #endif
    }

    /// One decision in one place, so the button and the remote cannot disagree
    /// about what toggle means.
    public func toggle() {
        if state.isPlaying {
            transport.pause()
        } else {
            transport.play()
        }
    }
}

/// One button, driven by what the player says it is doing.
///
/// `isPlaying` comes from the engine rather than from a tap, so a player that
/// stops on its own — end of item, an interruption, another app taking the audio
/// session — is drawn accurately without the button knowing why.
///
/// The glyphs are drawn rather than imported: a triangle and two bars are less
/// code than a symbol lookup that behaves differently per OS version.
public struct PlayPauseControl: View {

    private let isPlaying: Bool
    private let onToggle: () -> Void

    public init(isPlaying: Bool, onToggle: @escaping () -> Void) {
        self.isPlaying = isPlaying
        self.onToggle = onToggle
    }

    public var body: some View {
        Button(action: onToggle) {
            glyph
                .frame(width: 64, height: 64)
                .foregroundColor(.white)
        }
        .accessibilityLabel(isPlaying ? "Pause" : "Play")
    }

    @ViewBuilder
    private var glyph: some View {
        if isPlaying {
            HStack(spacing: 12) {
                Rectangle().frame(width: 16)
                Rectangle().frame(width: 16)
            }
        } else {
            Triangle()
        }
    }
}

private struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}
