// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import NoMercyVideoPlayer
import SwiftUI

/// The television player.
///
/// Every rule it follows is the shared Kotlin machine's — what a sideways press
/// means, when the bar goes away, what back does at each depth. This file maps a
/// remote to a key, reads one state value, and draws. That division is the whole
/// point: a second implementation of the autohide is the defect this campaign
/// exists to stop shipping again, and the machine's own tests now run on this
/// platform.
///
/// tvOS only. `onExitCommand`, `onMoveCommand` and `focusSection` do not exist
/// elsewhere, and neither does the thing they are for.
#if os(tvOS)
@available(tvOS 15.0, *)
public struct NMTvVideoPlayerView<Player: VideoChromePlayer>: View {

    @ObservedObject private var player: Player
    @StateObject private var chrome: TvChromeObserver
    @ObservedObject private var subtitles: SubtitleRenderer

    private let title: String
    private let subtitle: String

    @MainActor
    public init(
        player: Player,
        controller: TvChromeController,
        subtitles: SubtitleRenderer,
        title: String = "",
        subtitle: String = ""
    ) {
        self.player = player
        self.subtitles = subtitles
        self.title = title
        self.subtitle = subtitle
        _chrome = StateObject(wrappedValue: TvChromeObserver(controller: controller))
    }

    public var body: some View {
        ZStack {
            PlayerLayerHost(player: player.avPlayer)

            SubtitleOverlay(renderer: subtitles)

            if chrome.model.showsControls {
                controls
            }

            if chrome.model.showsSeekStrip {
                TvSeekStrip(seconds: player.currentTime, duration: player.duration)
            }

            if chrome.model.showsAnyDialog {
                TvDialogHost(model: chrome.model)
            }
        }
        // The whole surface takes the remote. A television has no pointer, so a
        // player that only listened while a button was focused would be a player
        // that ignores the remote until somebody guesses where to press.
        .focusable(true)
        .onMoveCommand { direction in
            chrome.send(TvRemote.gesture(for: direction))
        }
        .onPlayPauseCommand {
            chrome.send(.playPause)
        }
        .onExitCommand {
            chrome.back()
        }
        .onChange(of: player.isPlaying) { playing in
            // The machine pauses before scrubbing and needs to know when the
            // engine actually did. Told only by the chrome, a pause that came
            // from a headset or from the system would leave it disagreeing.
            chrome.controller.onPlaybackChanged(isPlaying: playing)
        }
        .onChange(of: player.currentTime) { seconds in
            subtitles.update(currentTime: seconds)
        }
    }

    private var controls: some View {
        VStack {
            TvTopBar(title: title, subtitle: subtitle)
                .focusSection()

            Spacer()

            TvTransportBar(
                elapsed: formatTvTime(player.currentTime),
                remaining: formatTvTime(max(0, player.duration - player.currentTime)),
                progress: player.duration > 0 ? player.currentTime / player.duration : 0
            )
            .focusSection()
        }
        .padding(48)
        .transition(.opacity)
    }
}
#endif

/// A position, as a television shows it.
///
/// Its own function rather than the music package's: these are separate products
/// and a shared one would be a dependency between them for eight lines.
public func formatTvTime(_ seconds: Double) -> String {
    guard seconds.isFinite, seconds >= 0 else { return "0:00" }

    let total = Int(seconds)
    let hours = total / 3600
    let minutes = (total % 3600) / 60
    let remainder = total % 60
    let pad = { (value: Int) in value < 10 ? "0\(value)" : "\(value)" }

    return hours > 0
        ? "\(hours):\(pad(minutes)):\(pad(remainder))"
        : "\(minutes):\(pad(remainder))"
}
