// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import Combine
import Foundation

/// What the view needs from a player, and nothing else.
///
/// The Kotlin engine's transport suspends, and a suspending call does not arrive
/// in Swift as something a SwiftUI button can invoke. This is the seam where
/// that is dealt with once — the app's adapter awaits the engine, the view calls
/// a plain method — rather than in every view that wants a play button.
///
/// It is also what makes the view testable without a running engine: a test
/// drives the same protocol the app implements.
public protocol PlayerTransport {
    func play()
    func pause()
}

/// The engine's state, as something SwiftUI will redraw for.
///
/// A Kotlin StateFlow is not an ObservableObject and SwiftUI will not watch one.
/// The app's adapter collects the flow and writes here; the view observes this.
/// Keeping the bridge in one published property rather than scattering
/// @State across views is what stops two parts of the chrome disagreeing about
/// whether the player is playing.
public final class PlayerStateObserver: ObservableObject {

    @Published public private(set) var isPlaying: Bool

    public init(isPlaying: Bool = false) {
        self.isPlaying = isPlaying
    }

    /// Called by whatever is collecting the engine's state.
    public func update(isPlaying: Bool) {
        if self.isPlaying != isPlaying {
            self.isPlaying = isPlaying
        }
    }
}
