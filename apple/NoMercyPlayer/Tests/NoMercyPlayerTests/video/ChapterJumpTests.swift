// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest

@testable import NoMercyPlayer

/// The chapter jumps, at the second either side of a boundary.
///
/// A jump in the middle of a chapter is the case that cannot go wrong. What can
/// is the grace: `desktop-ui/helpers/chapters.ts` compares against
/// `currentTime - 1` going back and `currentTime + 1` going forward, and the
/// first draft of this had a three-second grace on one side and none on the
/// other — both plausible, neither the web's, and the difference is invisible
/// until somebody presses the button twice near a boundary.
@MainActor
final class ChapterJumpTests: XCTestCase {

    private func madePlayer() -> FakeVideoChromePlayer {
        let player = FakeVideoChromePlayer()
        player.chapters = [
            ChapterMark(startSeconds: 0, title: "Cold open"),
            ChapterMark(startSeconds: 60, title: "Titles"),
            ChapterMark(startSeconds: 300, title: "Act one"),
        ]
        return player
    }

    func testBackFromInsideAChapterGoesToTheTopOfIt() {
        let player = madePlayer()
        player.placeAt(180)

        player.chapterBack()

        XCTAssertEqual(player.recordedSeeks, [60])
    }

    // The whole reason the grace exists. Without it this lands on 60 again and
    // the film does not move, which reads as a broken button.
    func testBackAtTheTopOfAChapterStepsToThePreviousOne() {
        let player = madePlayer()
        player.placeAt(60)

        player.chapterBack()

        XCTAssertEqual(player.recordedSeeks, [0])
    }

    // One second, not three. A second past a boundary is still "on" it; two is
    // inside the chapter and back belongs at its top.
    func testJustPastABoundaryIsStillOnIt() {
        let player = madePlayer()
        player.placeAt(60.5)

        player.chapterBack()

        XCTAssertEqual(player.recordedSeeks, [0])
    }

    func testTwoSecondsInIsInsideTheChapter() {
        let player = madePlayer()
        player.placeAt(62)

        player.chapterBack()

        XCTAssertEqual(player.recordedSeeks, [60])
    }

    func testBackFromTheFirstChapterGoesToTheStartOfTheFilm() {
        let player = madePlayer()
        player.placeAt(10)

        player.chapterBack()

        XCTAssertEqual(player.recordedSeeks, [0])
    }

    func testForwardGoesToTheNextBoundary() {
        let player = madePlayer()
        player.placeAt(120)

        player.chapterForward()

        XCTAssertEqual(player.recordedSeeks, [300])
    }

    // The grace applies going forward too. Landing exactly on a boundary and
    // pressing forward has to move past it, not pick the same one again.
    func testForwardFromExactlyOnABoundaryMovesOn() {
        let player = madePlayer()
        player.placeAt(60)

        player.chapterForward()

        XCTAssertEqual(player.recordedSeeks, [300])
    }

    // A no-op rather than a seek to the end of the film, which is what
    // `nextChapter` does when it runs out of boundaries.
    func testForwardPastTheLastChapterDoesNothing() {
        let player = madePlayer()
        player.placeAt(400)

        player.chapterForward()

        XCTAssertEqual(player.recordedSeeks, [])
    }

    // An item with no chapters still has the buttons' code path, and it must
    // not seek anywhere. The chrome hides them, and a default that seeked to
    // zero would rewind the film for any consumer that drew them anyway.
    func testAnItemWithNoChaptersGoesNowhereForward() {
        let player = FakeVideoChromePlayer()
        player.placeAt(120)

        player.chapterForward()

        XCTAssertEqual(player.recordedSeeks, [])
    }
}
