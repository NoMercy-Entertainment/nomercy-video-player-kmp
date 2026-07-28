// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyPlayer

/// The menus, driven the way a finger drives them.
@MainActor
final class PlayerMenusTests: XCTestCase {

    // The identifier is not the language code, deliberately. A real track id is
    // the engine's own handle — "audio-1", "track:5.1" — and a fixture where the
    // two happen to be the same string cannot tell a lookup by id from a lookup
    // by language. The first version of this used "en" for both, and a planted
    // defect that matched on language survived it.
    private let english = TrackOption(id: "audio-1", label: "English", language: "en")
    private let japanese = TrackOption(id: "audio-2", label: "Japanese", language: "ja")
    private let seven20 = QualityOption(id: "720-2500", label: "720p", height: 720, bitrate: 2500)
    private let ten80 = QualityOption(id: "1080-5000", label: "1080p", height: 1080, bitrate: 5000)

    private func made(
        levels: [QualityOption] = [],
        audio: [TrackOption] = [],
        subtitles: [TrackOption] = []
    ) -> (FakeVideoChromePlayer, PlayerMenuState, PlayerMenuActions<FakeVideoChromePlayer>) {
        let player = FakeVideoChromePlayer(levels: levels, audioOptions: audio, subtitleOptions: subtitles)
        let state = PlayerMenuState()
        return (player, state, PlayerMenuActions(player: player, state: state))
    }

    func testBackGoesOneLevelRatherThanAllTheWayOut() {
        // A current-page field can only mean "the main menu", so two levels deep
        // the back button skips one.
        let (_, state, _) = made()
        state.open()
        state.push(.subtitle)

        state.back()

        XCTAssertEqual(state.current, .main)
        XCTAssertTrue(state.isOpen)
    }

    func testBackOutOfTheMainMenuCloses() {
        // Which is what makes the system back gesture and the close button do
        // the same thing.
        let (_, state, _) = made()
        state.open()

        state.back()

        XCTAssertFalse(state.isOpen)
        XCTAssertNil(state.current)
    }

    func testBackOnANothingIsNotACrash() {
        let (_, state, _) = made()

        state.back()

        XCTAssertFalse(state.isOpen)
    }

    func testChoosingAnAudioTrackReachesTheEngine() {
        let (player, _, actions) = made(audio: [english, japanese])

        actions.selectAudio(japanese)

        XCTAssertEqual(player.selectedAudioID, "audio-2")
    }

    func testAndClosesTheMenuBehindIt() {
        // A menu left open over the picture after a choice is one the viewer has
        // to dismiss to see what they chose.
        let (_, state, actions) = made(audio: [english, japanese])
        state.open()
        state.push(.audio)

        actions.selectAudio(japanese)

        XCTAssertFalse(state.isOpen)
    }

    func testTurningSubtitlesOffIsAChoiceLikeAnyOther() {
        let (player, _, actions) = made(subtitles: [english])
        actions.selectSubtitle(english)
        XCTAssertEqual(player.selectedSubtitleID, "audio-1")

        actions.selectSubtitle(nil)

        XCTAssertNil(player.selectedSubtitleID)
    }

    func testQualityIsChosenByDescriptorRatherThanPosition() {
        let (player, _, actions) = made(levels: [seven20, ten80])

        actions.selectQuality(ten80)

        XCTAssertEqual(player.recordedQuality?.id, "1080-5000")
    }

    func testAutomaticIsASelectionRatherThanAnAbsence() {
        let (player, _, actions) = made(levels: [seven20, ten80])

        actions.selectQuality(nil)

        XCTAssertTrue(player.qualityWasSet)
        XCTAssertNil(player.recordedQuality)
    }

    func testAListWithOneOptionIsNotOffered() {
        // A row that opens onto one option is a press that costs a viewer time
        // and gives them no choice.
        let (_, _, one) = made(levels: [seven20], audio: [english])

        XCTAssertFalse(one.offered.contains(.quality))
        XCTAssertFalse(one.offered.contains(.audio))
    }

    func testAListWithTwoIs() {
        let (_, _, two) = made(levels: [seven20, ten80], audio: [english, japanese])

        XCTAssertTrue(two.offered.contains(.quality))
        XCTAssertTrue(two.offered.contains(.audio))
    }

    func testSubtitlesAreOfferedEvenWithNoneLoaded() {
        // Finding out there are none is an answer. Hiding the row makes it look
        // like the player has no subtitle support at all.
        let (_, _, actions) = made()

        XCTAssertTrue(actions.offered.contains(.subtitle))
    }

    func testTheCurrentRowIsMarkedByIdentifierNotPosition() {
        // A track list changes when a stream switches rendition, and a position
        // ticks whichever row moved into the slot.
        let (_, _, actions) = made(audio: [english, japanese])
        actions.selectAudio(japanese)

        XCTAssertTrue(actions.isCurrentAudio(japanese))
        XCTAssertFalse(actions.isCurrentAudio(english))
    }

    func testOffIsTheMarkedSubtitleRowWhenNothingIsChosen() {
        let (_, _, actions) = made(subtitles: [english])

        XCTAssertTrue(actions.isCurrentSubtitle(nil))
        XCTAssertFalse(actions.isCurrentSubtitle(english))
    }
}
