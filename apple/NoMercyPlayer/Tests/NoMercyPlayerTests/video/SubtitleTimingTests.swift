// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyPlayer

/// Which lines are on screen at a moment.
///
/// The only thing about subtitles worth asserting without eyes on them, and the
/// one that goes wrong silently: a line that appears a second late reads as a
/// bad rip rather than as a bug.
@MainActor
final class SubtitleTimingTests: XCTestCase {

    private let dialogue = [
        SubtitleCue(start: 0, end: 5, lines: ["Hello"]),
        SubtitleCue(start: 5, end: 10, lines: ["World"]),
    ]

    func testTheLineCoveringTheMomentIsTheOneOnScreen() {
        let renderer = SubtitleRenderer()
        renderer.loadCues(dialogue)

        renderer.update(currentTime: 3)
        XCTAssertEqual(renderer.visible, ["Hello"])

        renderer.update(currentTime: 7)
        XCTAssertEqual(renderer.visible, ["World"])
    }

    func testACueOwnsItsStartAndNotItsEnd() {
        // Or two cues meeting at a second both show on it, which reads as a
        // duplicated line.
        let renderer = SubtitleRenderer()
        renderer.loadCues(dialogue)

        renderer.update(currentTime: 5)

        XCTAssertEqual(renderer.visible, ["World"])
    }

    func testAMomentPastTheLastCueIsBlank() {
        let renderer = SubtitleRenderer()
        renderer.loadCues(dialogue)
        renderer.update(currentTime: 3)

        renderer.update(currentTime: 20)

        XCTAssertEqual(renderer.visible, [])
    }

    func testOverlappingCuesAreBothShown() {
        // A sign and a line of dialogue at once is a real thing subtitle files
        // do, and showing only the first drops half of what was written.
        let renderer = SubtitleRenderer()
        renderer.loadCues([
            SubtitleCue(start: 0, end: 10, lines: ["- a sign -"]),
            SubtitleCue(start: 2, end: 4, lines: ["and a line"]),
        ])

        renderer.update(currentTime: 3)

        XCTAssertEqual(renderer.visible, ["- a sign -", "and a line"])
    }

    func testCuesOutOfOrderStillReadTopToBottomInTimeOrder() {
        // A server sends what the file had, and files are not always ordered.
        //
        // Two cues covering the same moment is what makes this observable at
        // all: with one match the order of the list cannot show, which is what
        // the first version of this test asserted and why a defect that dropped
        // the sort survived it.
        let renderer = SubtitleRenderer()
        renderer.loadCues([
            SubtitleCue(start: 2, end: 4, lines: ["and a line"]),
            SubtitleCue(start: 0, end: 10, lines: ["- a sign -"]),
        ])

        renderer.update(currentTime: 3)

        XCTAssertEqual(renderer.visible, ["- a sign -", "and a line"])
    }

    func testAMultiLineCueKeepsBothLines() {
        let renderer = SubtitleRenderer()
        renderer.loadCues([SubtitleCue(start: 0, end: 5, lines: ["first", "second"])])

        renderer.update(currentTime: 1)

        XCTAssertEqual(renderer.visible, ["first", "second"])
    }

    func testLoadingNewCuesClearsWhatWasOnScreen() {
        // A track change with the old line still up is the previous language
        // sitting over the new one.
        let renderer = SubtitleRenderer()
        renderer.loadCues(dialogue)
        renderer.update(currentTime: 3)

        renderer.loadCues([])

        XCTAssertEqual(renderer.visible, [])
    }

    func testClearingTakesTheLinesAway() {
        let renderer = SubtitleRenderer()
        renderer.loadCues(dialogue)
        renderer.update(currentTime: 3)

        renderer.clear()
        renderer.update(currentTime: 3)

        XCTAssertEqual(renderer.visible, [])
    }
}
