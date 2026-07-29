// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyPlayer

/// The controls, driven the way a finger drives them.
///
/// Through the intents rather than through a rendered hierarchy: the binding
/// between a control and the engine is a named function, so this asserts the
/// binding without depending on a traversal library that lags every SwiftUI
/// release. What the buttons do is what these call.
@MainActor
final class ControlsOverlayTests: XCTestCase {

    private func made(playing: Bool = false, duration: Double = 0) -> (FakeVideoChromePlayer, ControlsVisibility, ControlsIntents<FakeVideoChromePlayer>) {
        let player = FakeVideoChromePlayer(isPlaying: playing, duration: duration)
        let visibility = ControlsVisibility(isPlaying: { player.isPlaying })
        return (player, visibility, ControlsIntents(player: player, visibility: visibility))
    }

    func testThePlayButtonReachesTheEngine() {
        let (player, _, intents) = made()

        intents.togglePlayPause()

        XCTAssertTrue(player.isPlaying)
    }

    func testAndTheEngineDrivesTheGlyphBack() {
        // The half a remembered flag would fake. The button has to follow what
        // the engine is doing, not what it was last asked to do.
        let player = FakeVideoChromePlayer()
        let model = ControlsOverlayModel(player: player)
        XCTAssertEqual(model.transport.icon, FluentIcons.play)

        player.play()

        XCTAssertEqual(model.transport.icon, FluentIcons.pause)
    }

    func testTheGlyphAndItsLabelCannotDisagree() {
        // One decision rather than two conditions reading the same state. The
        // Compose chrome shipped a pause triangle announcing itself as Play, and
        // only a planted defect found it.
        let player = FakeVideoChromePlayer()
        let strings = VideoChromeStrings()
        let model = ControlsOverlayModel(player: player, strings: strings)

        XCTAssertEqual(model.transport.label, strings.play)
        player.play()
        XCTAssertEqual(model.transport.label, strings.pause)
    }

    func testCommittingAScrubMovesTheFilm() {
        let (player, _, intents) = made(duration: 100)

        intents.seekBegin()
        intents.seekCommit(to: 55)

        XCTAssertEqual(player.recordedSeeks.last, 55)
    }

    func testAScrubInProgressHoldsTheControlsOpen() {
        // Hiding out from under a scrub takes away the thing somebody is using.
        let (player, visibility, intents) = made(playing: true, duration: 100)
        player.play()
        visibility.bumpActivity()

        intents.seekBegin()
        visibility.maybeHide()

        XCTAssertTrue(visibility.isActive)
    }

    func testAndReleasingItLetsThemGoAgain() {
        let (player, visibility, intents) = made(playing: true, duration: 100)
        player.play()
        intents.seekBegin()

        intents.seekCommit(to: 10)
        visibility.maybeHide()

        XCTAssertFalse(visibility.isActive)
    }

    func testAPausedFilmKeepsItsControls() {
        // Rule four. A paused film with nothing over it is a still image, and
        // there is no way to tell it from a player that died.
        let (_, visibility, _) = made()

        visibility.setPlaying(false)
        visibility.maybeHide()

        XCTAssertTrue(visibility.isActive)
    }

    func testATapFromHiddenRevealsAndDoesNotImmediatelyHide() {
        // The race that cost the web five release cycles. The surface wakes the
        // controls on the way down, so by the time the tap resolves they are
        // visible and a naive toggle hides them again.
        let (player, _, intents) = made(playing: true)
        player.play()

        intents.tapDown()
        let consumed = intents.tapUp()

        XCTAssertFalse(consumed)
    }

    func testATapWhileTheyAreUpPutsThemAway() {
        let (player, visibility, intents) = made(playing: true)
        player.play()
        visibility.bumpActivity()

        intents.tapDown()
        let consumed = intents.tapUp()

        XCTAssertTrue(consumed)
        XCTAssertFalse(visibility.isActive)
    }

    func testAMenuIsOfferedOnlyWhereThereIsAChoice() {
        // One track is not a menu, it is a row that opens onto itself.
        let one = FakeVideoChromePlayer(audioOptions: [TrackOption(id: "en", label: "English")])
        let two = FakeVideoChromePlayer(audioOptions: [
            TrackOption(id: "en", label: "English"),
            TrackOption(id: "nl", label: "Nederlands"),
        ])

        XCTAssertFalse(ControlsOverlayModel(player: one).offersAudioMenu)
        XCTAssertTrue(ControlsOverlayModel(player: two).offersAudioMenu)
    }

    func testTheBarReadsRemainingRatherThanTotal() {
        // Somebody deciding whether to start another episode is asking how much
        // is left.
        let player = FakeVideoChromePlayer(duration: 100)
        player.seek(to: 40)

        let model = ControlsOverlayModel(player: player)

        XCTAssertEqual(model.elapsed, "0:40")
        XCTAssertEqual(model.remaining, "-1:00")
    }

    func testAFilmWithNoLengthYetHasNoProgress() {
        // Every stream starts this way and a live one never leaves it.
        let player = FakeVideoChromePlayer(duration: 0)
        player.seek(to: 30)

        XCTAssertEqual(ControlsOverlayModel(player: player).progress, 0)
    }

    func testTheClockShowsHoursOnlyWhenThereAreAny() {
        XCTAssertEqual(formatTime(40), "0:40")
        XCTAssertEqual(formatTime(3661), "1:01:01")
        XCTAssertEqual(formatTime(-5), "0:00")
    }
}
