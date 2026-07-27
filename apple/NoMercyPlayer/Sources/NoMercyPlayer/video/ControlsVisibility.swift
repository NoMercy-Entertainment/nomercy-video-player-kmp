// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import Foundation

/// When the controls are on screen and when they go away.
///
/// The same five rules the Compose chrome's `ChromeController` holds, and that is
/// a contract rather than a coincidence: a viewer moving between an iPhone and a
/// television should find the chrome behaving identically, and the two clients
/// this replaces did not.
///
/// A Swift copy rather than the Kotlin one, and the reason is worth stating: the
/// controller lives in the Compose module, which is Android and desktop only, so
/// there is nothing for SKIE to expose. What keeps them from drifting is that
/// both are tested against the same five statements, written the same way.
///
///  1. anything the viewer does brings them back
///  2. idle for long enough and the picture is what they came for
///  3. a pointer leaving is stronger than one that merely stopped
///  4. paused holds them open, forever
///  5. a tap is decided against what was on screen when the finger landed
@MainActor
public final class ControlsVisibility: ObservableObject {

    @Published public private(set) var isActive: Bool = false

    /// The three things somebody is in the middle of. Hiding out from under any
    /// of them takes away the thing they are using.
    @Published public private(set) var isScrubbing: Bool = false
    @Published public private(set) var isMenuOpen: Bool = false

    private let isPlaying: () -> Bool
    private let inactivity: TimeInterval
    private var hideWork: DispatchWorkItem?

    /// What was on screen when a finger landed, captured before anything woke.
    ///
    /// Rule five, and the only one that needs memory. The surface wakes the
    /// controls on the way down, so by the time a tap resolves they are always
    /// visible and a naive toggle hides them again — the show-then-hide flicker.
    private var tapFoundThemUp: Bool?

    public init(isPlaying: @escaping () -> Bool, inactivity: TimeInterval = 3.5) {
        self.isPlaying = isPlaying
        self.inactivity = inactivity
    }

    public func bumpActivity() {
        isActive = true
        restartTimer()
    }

    public func maybeHide() {
        guard !heldOpen() else { return }

        isActive = false
        cancelTimer()
    }

    public func setPlaying(_ playing: Bool) {
        if playing {
            restartTimer()
        } else {
            showAndHold()
        }
    }

    public func setScrubbing(_ scrubbing: Bool) {
        isScrubbing = scrubbing
        reconcile()
    }

    public func setMenuOpen(_ open: Bool) {
        isMenuOpen = open
        reconcile()
    }

    /// Called on touch-down, before the wake.
    public func onTapDown() {
        tapFoundThemUp = isActive
    }

    /// Whether the tap was ours to consume. Read once and cleared: a second tap
    /// with no press before it falls back to what is actually on screen rather
    /// than to a snapshot from a gesture that has finished.
    @discardableResult
    public func onSingleTap() -> Bool {
        let wasUp = tapFoundThemUp ?? isActive
        tapFoundThemUp = nil

        // Hidden when the finger landed means the wake has already shown them,
        // and that was the whole point of the tap.
        guard wasUp else { return false }

        maybeHide()
        return !isActive
    }

    public func dispose() {
        cancelTimer()
    }

    private func heldOpen() -> Bool {
        !isPlaying() || isMenuOpen || isScrubbing
    }

    private func reconcile() {
        if heldOpen() {
            showAndHold()
        } else {
            restartTimer()
        }
    }

    private func showAndHold() {
        cancelTimer()
        isActive = true
    }

    private func restartTimer() {
        cancelTimer()
        guard !heldOpen() else { return }

        let work = DispatchWorkItem { [weak self] in self?.maybeHide() }
        hideWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + inactivity, execute: work)
    }

    private func cancelTimer() {
        hideWork?.cancel()
        hideWork = nil
    }
}
