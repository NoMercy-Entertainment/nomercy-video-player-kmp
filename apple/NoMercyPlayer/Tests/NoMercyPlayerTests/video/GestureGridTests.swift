// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyPlayer

/// What a gesture at a place on the picture means.
///
/// A table rather than a screenshot, because the interesting failures here are
/// all "the wrong cell did the wrong thing" — invisible in a render and obvious
/// in a list of cells.
final class GestureGridTests: XCTestCase {

    private let grid = GestureGrid()

    func testTheMiddleOfThePictureIsPlayback() {
        XCTAssertEqual(grid.doubleTap(row: 1, column: 1), .togglePlayPause)
    }

    func testTheSidesSeekAwayFromTheMiddle() {
        XCTAssertEqual(grid.doubleTap(row: 1, column: 2), .seek(10))
        XCTAssertEqual(grid.doubleTap(row: 1, column: 0), .seek(-10))
    }

    func testTheTopAndBottomRowsDoNothing() {
        // Deliberately. A double tap near the bars is somebody aiming at a
        // control and missing, and seeking on it moves the film they were about
        // to scrub.
        XCTAssertEqual(grid.doubleTap(row: 0, column: 1), .nothing)
        XCTAssertEqual(grid.doubleTap(row: 2, column: 0), .nothing)
    }

    func testBrightnessIsOnTheLeftAndVolumeOnTheRight() {
        // The arrangement every player on the platform uses, which is the whole
        // reason it is discoverable without a legend.
        guard case .brightness = grid.verticalDrag(column: 0, deltaY: -10) else {
            return XCTFail("the left column should carry brightness")
        }
        guard case .volume = grid.verticalDrag(column: 2, deltaY: -10) else {
            return XCTFail("the right column should carry volume")
        }
    }

    func testDraggingUpAsksForMore() {
        // Up is more, which is the direction every hardware control on the
        // device already moves. A sign flip here is a player whose volume goes
        // down when a viewer swipes up.
        guard case let .volume(up) = grid.verticalDrag(column: 2, deltaY: -10) else {
            return XCTFail("expected a volume change")
        }
        guard case let .volume(down) = grid.verticalDrag(column: 2, deltaY: 10) else {
            return XCTFail("expected a volume change")
        }

        XCTAssertGreaterThan(up, 0)
        XCTAssertLessThan(down, 0)
    }

    func testTheMiddleColumnHasNoSlider() {
        XCTAssertEqual(grid.verticalDrag(column: 1, deltaY: -10), .nothing)
    }

    func testAPointOutsideTheSurfaceStillLandsOnTheGrid() {
        // A drag that leaves the surface reports a position outside it, and an
        // unclamped index reads off the end.
        let size = CGSize(width: 300, height: 300)

        XCTAssertEqual(grid.cell(at: CGPoint(x: -50, y: -50), in: size).column, 0)
        XCTAssertEqual(grid.cell(at: CGPoint(x: 900, y: 900), in: size).column, 2)
        XCTAssertEqual(grid.cell(at: CGPoint(x: 900, y: 900), in: size).row, 2)
    }

    func testASurfaceWithNoSizeYetDoesNotDivideByZero() {
        // The first layout pass, every time.
        let cell = grid.cell(at: CGPoint(x: 10, y: 10), in: .zero)

        XCTAssertEqual(cell.row, 1)
        XCTAssertEqual(cell.column, 1)
    }

    func testTheCellsSplitTheSurfaceInThirds() {
        let size = CGSize(width: 300, height: 300)

        XCTAssertEqual(grid.cell(at: CGPoint(x: 50, y: 150), in: size).column, 0)
        XCTAssertEqual(grid.cell(at: CGPoint(x: 150, y: 150), in: size).column, 1)
        XCTAssertEqual(grid.cell(at: CGPoint(x: 250, y: 150), in: size).column, 2)
    }
}
