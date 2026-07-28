// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyPlayer
import NoMercyVideoPlayer

/// What the bubble says about a moment.
///
/// The frame lookup is the shared one and already has its own tests; what is new
/// here is the chapter a moment belongs to, which has an edge a render would
/// hide.
final class SeekPreviewTests: XCTestCase {

    private let chapters = [
        Chapter(startTime: 0, title: "Prologue", imageUrl: nil, synthetic: false),
        Chapter(startTime: 60, title: "Part A", imageUrl: nil, synthetic: false),
        Chapter(startTime: 120, title: "Part B", imageUrl: nil, synthetic: false),
    ]

    func testAMomentBelongsToTheChapterItIsInside() {
        XCTAssertEqual(chapterTitle(at: 30, in: chapters), "Prologue")
        XCTAssertEqual(chapterTitle(at: 90, in: chapters), "Part A")
    }

    func testAChapterOwnsItsStartAndNotItsEnd() {
        // Without that rule the boundary second belongs to two chapters and
        // which one wins depends on the order they happen to arrive in.
        XCTAssertEqual(chapterTitle(at: 60, in: chapters), "Part A")
        XCTAssertEqual(chapterTitle(at: 59.9, in: chapters), "Prologue")
    }

    func testTheLastChapterRunsToTheEnd() {
        XCTAssertEqual(chapterTitle(at: 99_999, in: chapters), "Part B")
    }

    func testChaptersOutOfOrderStillAnswerCorrectly() {
        // A server sends what it has, and what it has is not always sorted.
        //
        // This particular order is the point. Picking the last match out of an
        // unsorted list only goes wrong when a later entry has an earlier start,
        // so an arrangement without that pair passes whether or not anything
        // sorts — which is what the first version of this test did.
        let unsorted = [chapters[1], chapters[0], chapters[2]]

        XCTAssertEqual(chapterTitle(at: 90, in: unsorted), "Part A")
    }

    func testAMomentBeforeTheFirstChapterHasNone() {
        let late = [Chapter(startTime: 60, title: "Part A", imageUrl: nil, synthetic: false)]

        XCTAssertNil(chapterTitle(at: 10, in: late))
    }

    func testAFilmWithNoChaptersHasNoTitle() {
        XCTAssertNil(chapterTitle(at: 10, in: []))
    }

    func testAnUnnamedChapterIsNoTitleRatherThanAnEmptyLine() {
        // A blank line under the frame reads as something that failed to load.
        let unnamed = [Chapter(startTime: 0, title: "", imageUrl: nil, synthetic: false)]

        XCTAssertNil(chapterTitle(at: 10, in: unnamed))
    }
}
