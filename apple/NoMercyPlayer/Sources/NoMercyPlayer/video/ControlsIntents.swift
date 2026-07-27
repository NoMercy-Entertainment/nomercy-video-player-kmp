// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import Foundation

/// What each control does, as something a test can call.
///
/// The view stores these and its buttons invoke them, so the binding between a
/// control and the engine is a named function rather than a closure buried in a
/// view hierarchy. That is what lets the gate assert the binding without walking
/// a rendered tree — and a traversal library is a dependency that lags every new
/// SwiftUI release, which is a fragile thing to hang a correctness gate on.
public struct ControlsIntents<Player: VideoChromePlayer> {

    private let player: Player
    private let visibility: ControlsVisibility

    public init(player: Player, visibility: ControlsVisibility) {
        self.player = player
        self.visibility = visibility
    }

    /// Any of these is activity, so the controls stay up while somebody is
    /// using them. Bumping inside the intent rather than at each button is what
    /// stops one control being the one that forgets.
    @MainActor
    public func togglePlayPause() {
        visibility.bumpActivity()
        player.togglePlayPause()
    }

    @MainActor
    public func seekCommit(to seconds: Double) {
        visibility.setScrubbing(false)
        visibility.bumpActivity()
        player.seek(to: seconds)
    }

    /// While a scrub is in progress the film does not move and the controls do
    /// not go away. Seeking on every step is the seek storm the engine answers
    /// by stalling.
    @MainActor
    public func seekBegin() {
        visibility.setScrubbing(true)
    }

    @MainActor
    public func selectAudio(_ option: TrackOption) {
        visibility.bumpActivity()
        player.selectAudio(option)
    }

    @MainActor
    public func selectSubtitle(_ option: TrackOption?) {
        visibility.bumpActivity()
        player.selectSubtitle(option)
    }

    @MainActor
    public func selectQuality(_ option: QualityOption?) {
        visibility.bumpActivity()
        player.selectQuality(option)
    }

    @MainActor
    public func setMenuOpen(_ open: Bool) {
        visibility.setMenuOpen(open)
    }

    /// A tap on the picture, in two halves because the decision needs what was
    /// on screen when the finger landed rather than when it lifted.
    @MainActor
    public func tapDown() {
        visibility.onTapDown()
        visibility.bumpActivity()
    }

    @MainActor
    @discardableResult
    public func tapUp() -> Bool {
        visibility.onSingleTap()
    }
}
