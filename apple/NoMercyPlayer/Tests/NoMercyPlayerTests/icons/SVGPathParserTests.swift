// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyPlayer

/// Compose gets a path parser for free and SwiftUI does not, so this one is
/// hand-written — which makes it the piece most likely to draw a subtly wrong
/// icon. The cases here are the syntax the Fluent table actually uses.
final class SVGPathParserTests: XCTestCase {

    func testAbsoluteMoveAndLine() {
        let commands = SVGPathParser.parse("M10 20L30 40")

        XCTAssertEqual(commands.count, 2)
        if case .move(let point) = commands[0] {
            XCTAssertEqual(point.x, 10)
            XCTAssertEqual(point.y, 20)
        } else {
            XCTFail("expected a move")
        }
    }

    // Lowercase is relative to where the pen is, and getting this wrong draws
    // every icon as a scatter of correct shapes in wrong places.
    func testRelativeCommandsAccumulate() {
        let commands = SVGPathParser.parse("M10 10 l5 5 l5 5")

        guard case .line(let second) = commands[2] else { return XCTFail("expected a line") }
        XCTAssertEqual(second.x, 20)
        XCTAssertEqual(second.y, 20)
    }

    // A repeated command letter may be omitted. "L 1 2 3 4" is two lines, and a
    // parser that stopped after the first would silently truncate the glyph.
    func testAnOmittedCommandLetterRepeats() {
        let commands = SVGPathParser.parse("M0 0 L1 2 3 4")

        XCTAssertEqual(commands.count, 3)
        guard case .line(let second) = commands[2] else { return XCTFail("expected a line") }
        XCTAssertEqual(second.x, 3)
        XCTAssertEqual(second.y, 4)
    }

    // The one special case in the syntax: an implicit repeat after a moveto is
    // a lineto, not another moveto. Getting it wrong leaves the shape open.
    func testAnImplicitRepeatAfterMoveIsALine() {
        let commands = SVGPathParser.parse("M0 0 1 1")

        XCTAssertEqual(commands.count, 2)
        guard case .line = commands[1] else { return XCTFail("expected a line, not a move") }
    }

    func testHorizontalAndVerticalKeepTheOtherAxis() {
        let commands = SVGPathParser.parse("M5 7 H20 V30")

        guard case .line(let horizontal) = commands[1] else { return XCTFail("expected a line") }
        XCTAssertEqual(horizontal.x, 20)
        XCTAssertEqual(horizontal.y, 7)

        guard case .line(let vertical) = commands[2] else { return XCTFail("expected a line") }
        XCTAssertEqual(vertical.x, 20)
        XCTAssertEqual(vertical.y, 30)
    }

    func testCubicCurvesCarryBothControlPoints() {
        let commands = SVGPathParser.parse("M0 0 C1 2 3 4 5 6")

        guard case .curve(let to, let c1, let c2) = commands[1] else {
            return XCTFail("expected a curve")
        }
        XCTAssertEqual(c1.x, 1)
        XCTAssertEqual(c2.x, 3)
        XCTAssertEqual(to.x, 5)
    }

    func testCloseReturnsToTheSubpathStart() {
        let commands = SVGPathParser.parse("M10 10 L20 20 Z L30 30")

        guard case .line(let afterClose) = commands[3] else { return XCTFail("expected a line") }
        XCTAssertEqual(afterClose.x, 30)
    }

    func testNegativeNumbersNeedNoSeparator() {
        let commands = SVGPathParser.parse("M0 0L-5-7")

        guard case .line(let point) = commands[1] else { return XCTFail("expected a line") }
        XCTAssertEqual(point.x, -5)
        XCTAssertEqual(point.y, -7)
    }

    // Every icon in the table parses to something, which is the check that the
    // generated file and this parser agree.
    func testEveryGeneratedIconParses() {
        let icons: [FluentIcon] = [
            FluentIcons.play, FluentIcons.pause, FluentIcons.next, FluentIcons.previous,
            FluentIcons.volumeHigh, FluentIcons.volumeMuted, FluentIcons.settings,
            FluentIcons.subtitles, FluentIcons.quality, FluentIcons.playlist,
            FluentIcons.theater, FluentIcons.pipEnter, FluentIcons.speed,
            FluentIcons.aspectFit, FluentIcons.chapterBack, FluentIcons.chapterForward,
        ]

        for icon in icons {
            let path = icon.path(in: CGRect(x: 0, y: 0, width: 24, height: 24))
            XCTAssertFalse(path.isEmpty, "an icon parsed to an empty path")
        }
    }
}
