// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
import NoMercyVideoPlayer
@testable import NoMercyPlayer

/// The trailer is a different player, not a narrow one.
///
/// The distinction the whole variant rests on. A trailer over a detail page has
/// no queue to step through, no chapters to jump between and no settings to
/// open — and if the difference were only width, the responsive rule would put
/// every one of those back on a desktop, where there is room. So each case here
/// asks the rule for a bar wide enough to fit everything and checks what the
/// trailer still refuses to offer.
final class ChromeKindTests: XCTestCase {

    // Wider than the widest breakpoint, so nothing is dropped for want of room
    // and every absence below is a decision rather than a measurement.
    private let desktop: Int32 = 1920

    private func laidOut(_ kind: VideoChromeKind) -> [ChromeControl] {
        ChromeResponsiveKt.visibleControlsIn(
            widthDp: desktop,
            enabled: kind.controls,
            unavailable: [],
            portrait: false,
            noHover: false
        )
    }

    func testTheFullPlayerOffersTheMenusOnADesktop() {
        let controls = laidOut(.full)

        XCTAssertTrue(controls.contains(.settings), "the full player dropped its settings menu at 1920dp")
        XCTAssertTrue(controls.contains(.chapterNext), "the full player dropped chapter skipping at 1920dp")
    }

    // The same width, the same rule, a different player.
    func testTheTrailerRefusesThemAtTheSameWidth() {
        let controls = laidOut(.trailer)

        XCTAssertFalse(controls.contains(.settings), "the trailer offered a settings menu it has nothing to put in")
        XCTAssertFalse(controls.contains(.chapterNext), "the trailer offered chapter skipping over a single clip")
        XCTAssertFalse(controls.contains(.playlist), "the trailer offered a queue it does not have")
    }

    func testTheTrailerStillOffersWhatATrailerNeeds() {
        let controls = laidOut(.trailer)

        XCTAssertTrue(controls.contains(.play), "the trailer cannot be started")
        XCTAssertTrue(controls.contains(.mute), "the trailer cannot be silenced, which is the one control it must have")
        XCTAssertTrue(controls.contains(.fullscreen), "the trailer cannot be opened out")
    }

    // The trailer's bar is the full bar with things taken out, in the order the
    // full one already had them.
    //
    // A subsequence rather than a set comparison, because that is the claim
    // that makes this one enum instead of a second chrome: both bars come out
    // of the same rule, so the trailer cannot put mute before play just by
    // having fewer controls to place.
    func testTheTrailerIsASubsequenceOfTheFullBar() {
        var remaining = laidOut(.full)[...]

        for control in laidOut(.trailer) {
            guard let at = remaining.firstIndex(of: control) else {
                return XCTFail("the trailer placed \(control) somewhere the full bar's order does not allow")
            }
            remaining = remaining[remaining.index(after: at)...]
        }
    }
}
