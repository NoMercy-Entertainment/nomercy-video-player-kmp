// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import NoMercyVideoPlayer
import XCTest
@testable import NoMercyPlayer

/// The Apple half of the preview and the chapter bar.
///
/// Two things are worth proving here and they are different things. The crop is
/// Swift's own arithmetic and nothing else checks it. The frame lookup and the
/// marker positions are the shared maths, and what is proven is that this side
/// actually reaches them — a Swift copy that drifted is exactly the bug the
/// slice exists to remove, and it would pass every Kotlin test ever written.
final class SpriteAndChapterTests: XCTestCase {

    private func cue(
        x: Int32,
        y: Int32,
        start: Double = 0,
        width: Int32 = 320,
        height: Int32 = 178
    ) -> SpriteCue {
        SpriteCue(
            start: start,
            end: start + 10,
            url: "sheet.webp",
            x: x,
            y: y,
            width: width,
            height: height
        )
    }

    // MARK: - The crop

    func testTheSheetIsScaledSoTheWantedFrameComesOutTheAskedWidth() {
        let crop = SpriteCrop(cue: cue(x: 320, y: 0), width: 200)

        XCTAssertEqual(crop.scale, 200.0 / 320.0, accuracy: 0.0001)
        XCTAssertEqual(crop.size.width, 200)
    }

    func testTheSheetSlidesSoTheWantedFrameLandsAtTheOrigin() {
        // The second frame starts 320 across in the sheet's own space. At the
        // scale above that is 200 points, so the sheet moves 200 points left.
        let crop = SpriteCrop(cue: cue(x: 320, y: 0), width: 200)

        XCTAssertEqual(crop.offset.width, -200, accuracy: 0.0001)
        XCTAssertEqual(crop.offset.height, 0, accuracy: 0.0001)
    }

    func testASecondRowSlidesUpAsWellAsAcross() {
        let crop = SpriteCrop(cue: cue(x: 640, y: 178), width: 320)

        XCTAssertEqual(crop.offset.width, -640, accuracy: 0.0001)
        XCTAssertEqual(crop.offset.height, -178, accuracy: 0.0001)
    }

    func testTheBoxIsShapedByTheFrameRatherThanTheSheet() {
        let crop = SpriteCrop(cue: cue(x: 0, y: 0), width: 200)

        XCTAssertEqual(crop.size.height, 200 / (320.0 / 178.0), accuracy: 0.0001)
    }

    func testAFrameDeclaringNothingGetsAShapeRatherThanAnInfinity() {
        // A VTT whose rect was written wrong. Dividing by it gives an infinite
        // scale and a box with no height.
        let crop = SpriteCrop(cue: cue(x: 0, y: 0, width: 0, height: 0), width: 200)

        XCTAssertTrue(crop.scale.isFinite)
        XCTAssertEqual(crop.size.height, 200 / (16.0 / 9.0), accuracy: 0.0001)
    }

    // MARK: - Reaching the shared maths

    func testTheFrameLookupIsTheSharedOne() {
        // Held to the same answers as the Kotlin tests: the last frame that has
        // started, the first one before anything has, the last one past the end.
        //
        // The starts have to differ, which is not a detail. Three cues all
        // starting at zero make every lookup return the last of them, and the
        // assertions pass or fail for reasons that have nothing to do with the
        // rule.
        let frames = [
            cue(x: 0, y: 0, start: 0),
            cue(x: 320, y: 0, start: 10),
            cue(x: 640, y: 0, start: 20),
        ]

        XCTAssertEqual(spriteFrame(in: frames, at: -1)?.x, 0)
        XCTAssertEqual(spriteFrame(in: frames, at: 5)?.x, 0)
        XCTAssertEqual(spriteFrame(in: frames, at: 15)?.x, 320)
        XCTAssertEqual(spriteFrame(in: frames, at: 9_999)?.x, 640)
        XCTAssertNil(spriteFrame(in: [], at: 5))
    }

    func testTheMarkersAreTheSharedOnes() {
        let chapters = [
            Chapter(startTime: 0, title: "Prologue", imageUrl: nil, synthetic: false),
            Chapter(startTime: 60, title: "Part A", imageUrl: nil, synthetic: false),
        ]

        let markers = ChapterMarkerMathKt.chapterMarkers(chapters: chapters, duration: 120)

        XCTAssertEqual(markers.count, 2)
        XCTAssertEqual(markers[0].leftPercent, 0, accuracy: 0.0001)
        XCTAssertEqual(markers[0].rightPercent, 50, accuracy: 0.0001)
        XCTAssertEqual(markers[1].rightPercent, 100, accuracy: 0.0001)
    }

    func testASegmentFillsThroughTheSharedRule() {
        let marker = ChapterMarker(index: 0, leftPercent: 10, rightPercent: 30)

        XCTAssertEqual(ChapterMarkerMathKt.chapterFill(marker: marker, valuePercent: 5), 0)
        XCTAssertEqual(ChapterMarkerMathKt.chapterFill(marker: marker, valuePercent: 20), 0.5, accuracy: 0.0001)
        XCTAssertEqual(ChapterMarkerMathKt.chapterFill(marker: marker, valuePercent: 40), 1)
    }
}
