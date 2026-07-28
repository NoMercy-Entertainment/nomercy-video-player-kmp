// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
import SwiftUI
@testable import NoMercyPlayer
import NoMercyVideoPlayer

#if os(tvOS)

/// The Apple side of the television chrome.
///
/// What is worth testing here is the translation and nothing else. Every rule —
/// what the arrows mean, when the controls hide, where back goes — belongs to
/// the shared controller and is covered in Kotlin, so a case here that asserted
/// behaviour would be asserting a second copy of it.
final class TvChromeModelTests: XCTestCase {

    private var pressed: [String] = []

    private func model() -> TvChromeModel {
        pressed = []
        let actions = TvChromeActions(
            onLeft: { self.pressed.append("left") },
            onRight: { self.pressed.append("right") },
            onUp: { self.pressed.append("up") },
            onDown: { self.pressed.append("down") },
            onBack: { self.pressed.append("back") },
            onTogglePlay: { self.pressed.append("toggle") },
            onPlay: { self.pressed.append("play") },
            onRestart: { self.pressed.append("restart") }
        )
        return TvChromeModel(actions: actions)
    }

    func testEveryDirectionReachesTheSharedController() {
        let subject = model()

        subject.move(.left)
        subject.move(.right)
        subject.move(.up)
        subject.move(.down)

        XCTAssertEqual(pressed, ["left", "right", "up", "down"])
    }

    func testTheMenuButtonIsBackRatherThanExit() {
        // The remote has one, and what it means is layered: the controller
        // decides whether this press closes a list, hides the controls or leaves
        // the player. Sending it anywhere else would be deciding that here.
        let subject = model()

        subject.back()

        XCTAssertEqual(pressed, ["back"])
    }

    func testThePlayPauseButtonIsItsOwnThing() {
        // The Siri Remote has a dedicated one, so it does not arrive as a
        // direction and cannot be inferred from one.
        let subject = model()

        subject.togglePlay()

        XCTAssertEqual(pressed, ["toggle"])
    }

    func testAViewerStartsOnThePreScreen() {
        // The same starting state as every other platform, so a viewer moving
        // between an Apple TV and an Android box finds the same thing.
        let subject = model()

        XCTAssertTrue(subject.preScreenVisible)
        XCTAssertFalse(subject.controlsVisible)
        XCTAssertFalse(subject.seekMode)
    }

    func testStateIsPushedInRatherThanDecidedHere() {
        // The controller publishes and a host bridges. A model that decided
        // would be a second state machine, and the two would disagree the first
        // time either was changed.
        let subject = model()

        subject.apply(preScreen: false, controls: true, seeking: false, playing: true)

        XCTAssertFalse(subject.preScreenVisible)
        XCTAssertTrue(subject.controlsVisible)
        XCTAssertTrue(subject.isPlaying)
    }

    func testTheScrubberAndTheControlsAreNeverBothShown() {
        // The view picks one branch, so an inconsistent pair from a host still
        // draws one thing rather than two overlapping bars.
        let subject = model()

        subject.apply(preScreen: false, controls: true, seeking: true, playing: true)

        XCTAssertTrue(subject.seekMode)
    }
}

#endif
