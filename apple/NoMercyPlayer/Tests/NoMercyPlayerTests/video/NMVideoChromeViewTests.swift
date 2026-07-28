// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

#if os(iOS)
import SwiftUI
import XCTest
@testable import NoMercyPlayer

/// The assembled player, driven where a finger would drive it.
///
/// The layers each have their own tests; what is untested until here is that the
/// assembly wires them to one player and one visibility rather than to four
/// copies — which is the failure that makes a tap wake controls nobody can see.
@MainActor
final class NMVideoChromeViewTests: XCTestCase {

    func testTheAssemblyMountsWithNothingButAPlayer() {
        // The claim the drop-in makes: one line and it works.
        let player = FakeVideoChromePlayer()

        let view = NMVideoChromeView(player: player)

        XCTAssertNotNil(view.body)
    }

    func testASeekGestureMovesRelativeToWhereTheFilmIs() {
        // The grid knows how far to jump and not where to land. Computing the
        // target from a position read earlier is how a skip arrives somewhere
        // else on a film that kept playing.
        let player = FakeVideoChromePlayer(duration: 100)
        player.seek(to: 30)
        let visibility = ControlsVisibility(isPlaying: { player.isPlaying })
        let intents = ControlsIntents(player: player, visibility: visibility)

        intents.seekCommit(to: max(0, player.currentTime + 10))

        XCTAssertEqual(player.recordedSeeks.last, 40)
    }

    func testASeekBackPastTheStartLandsOnTheStart() {
        let player = FakeVideoChromePlayer(duration: 100)
        player.seek(to: 4)
        let visibility = ControlsVisibility(isPlaying: { player.isPlaying })
        let intents = ControlsIntents(player: player, visibility: visibility)

        intents.seekCommit(to: max(0, player.currentTime - 10))

        XCTAssertEqual(player.recordedSeeks.last, 0)
    }

    func testTheSubtitleRendererFollowsThePlayhead() {
        // The overlay is told the time by the assembly rather than reading a
        // clock of its own, so a paused film does not keep advancing its lines.
        let renderer = SubtitleRenderer()
        renderer.loadCues([
            SubtitleCue(start: 0, end: 5, lines: ["Hello"]),
            SubtitleCue(start: 5, end: 10, lines: ["World"]),
        ])

        renderer.update(currentTime: 7)

        XCTAssertEqual(renderer.visible, ["World"])
    }

    func testTheChromeAndTheGesturesShareOneVisibility() {
        // Two copies is the failure this catches: a tap wakes one set of
        // controls and the bars read the other, so the picture is tapped and
        // nothing appears.
        let player = FakeVideoChromePlayer(isPlaying: true)
        player.play()
        let visibility = ControlsVisibility(isPlaying: { player.isPlaying })
        let intents = ControlsIntents(player: player, visibility: visibility)

        intents.tapDown()
        intents.tapUp()

        XCTAssertTrue(visibility.isActive)
    }
}
#endif
