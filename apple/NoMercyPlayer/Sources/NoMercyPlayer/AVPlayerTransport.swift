// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import AVFoundation
import Combine
import Foundation

/// ``PlayerTransport`` over an `AVPlayer`.
///
/// Until now the only thing conforming to the protocol was a stub in the tests,
/// which meant the view was proven and nothing could use it: an app wanting the
/// shipped chrome over AVFoundation had to write this adapter itself, and every
/// app would have written the same one. That is exactly what `playerCommandsOf`
/// fixed on the Kotlin side, and it lives beside the protocol here for the same
/// reason.
///
/// It observes `timeControlStatus` rather than assuming a tap worked. A player
/// that stops on its own — end of item, an interruption, another app taking the
/// audio session, a stall on a slow network — leaves a button that tracks taps
/// showing the wrong glyph, and the viewer presses it to get the state they can
/// already see.
public final class AVPlayerTransport: PlayerTransport {

    private let player: AVPlayer
    private weak var state: PlayerStateObserver?
    private var observation: NSKeyValueObservation?

    public init(player: AVPlayer, state: PlayerStateObserver) {
        self.player = player
        self.state = state

        // `initial: true` so the observer's first callback sets the starting
        // value. Without it the button draws whatever the observer was
        // constructed with until the player next changes state, which on a
        // paused player is never.
        observation = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak state] player, _ in
            let playing = player.timeControlStatus != .paused

            // The observer fires on whichever queue AVFoundation chooses, and a
            // @Published write from off the main queue is a SwiftUI violation
            // that shows up as a purple runtime warning or a crash, not as a
            // wrong picture.
            if Thread.isMainThread {
                state?.update(isPlaying: playing)
            } else {
                DispatchQueue.main.async { state?.update(isPlaying: playing) }
            }
        }
    }

    deinit {
        observation?.invalidate()
    }

    public func play() {
        player.play()
    }

    public func pause() {
        player.pause()
    }
}
