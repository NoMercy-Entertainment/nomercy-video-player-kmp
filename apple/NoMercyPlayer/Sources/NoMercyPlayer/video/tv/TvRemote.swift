// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import NoMercyVideoPlayer
import SwiftUI

/// A Siri Remote gesture, as a key the shared machine understands.
///
/// Mapping is all this layer does. Every decision about what a press means —
/// what seeking does to the controls, what back means at each depth, when the
/// bar goes away — belongs to the Kotlin controller, and a Swift copy of any of
/// it would be the second implementation this campaign exists to avoid.
public enum TvRemote {

    /// What a television remote can send. Named for the gesture rather than for
    /// the key, because tvOS reports a swipe and a press, and which key that is
    /// depends on the mapping below rather than on the hardware.
    public enum Gesture: CaseIterable, Sendable {
        case left
        case right
        case up
        case down
        case select
        case playPause
        case play
        case pause
    }

    public static func key(for gesture: Gesture) -> PlayerKey {
        switch gesture {
        case .left: return .left
        case .right: return .right
        case .up: return .up
        case .down: return .down
        // Centre rather than Play. A remote's centre button selects what is
        // focused and only means play when nothing is, which is a distinction
        // the machine makes and this layer must not flatten.
        case .select: return .center
        case .playPause: return .playPause
        case .play: return .play
        case .pause: return .pause
        }
    }

    #if os(tvOS)
    /// What SwiftUI reports from a directional swipe or press.
    public static func gesture(for direction: MoveCommandDirection) -> Gesture {
        switch direction {
        case .left: return .left
        case .right: return .right
        case .up: return .up
        case .down: return .down
        @unknown default: return .select
        }
    }
    #endif
}
