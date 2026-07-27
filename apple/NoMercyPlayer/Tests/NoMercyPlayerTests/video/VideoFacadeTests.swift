// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyPlayer

/// The seam every view in this package reads.
///
/// Both directions are asserted here so the views above can be tested against
/// the facade rather than against an engine: a control reaches the engine, and
/// what the engine says reaches the published surface. A seam proven in only one
/// direction is one where a view can bind to a property nothing ever writes.
@MainActor
final class VideoFacadeTests: XCTestCase {

    func testAControlReachesTheEngine() {
        let engine = FakeVideoEngine()
        let facade = EngineVideoPlayer(engine: engine)

        facade.togglePlayPause()
        facade.seek(to: 42)

        XCTAssertEqual(engine.playCount, 1)
        XCTAssertEqual(engine.recordedSeeks.last, 42)
    }

    func testToggleReadsTheEngineRatherThanRememberingWhatItAsked() {
        // A player that pauses itself — an interruption, another app taking
        // audio focus — leaves a remembered flag lying, and the next press then
        // asks for the opposite of what the viewer meant.
        let engine = FakeVideoEngine()
        let facade = EngineVideoPlayer(engine: engine)
        engine.emit { $0.playing = true }

        facade.togglePlayPause()

        XCTAssertEqual(engine.pauseCount, 1)
        XCTAssertEqual(engine.playCount, 0)
    }

    func testWhatTheEngineSaysReachesTheChrome() {
        let engine = FakeVideoEngine()
        let facade = EngineVideoPlayer(engine: engine)
        XCTAssertFalse(facade.isPlaying)

        engine.emit {
            $0.playing = true
            $0.currentTime = 10
            $0.duration = 100
        }

        XCTAssertTrue(facade.isPlaying)
        XCTAssertEqual(facade.currentTime, 10)
        XCTAssertEqual(facade.duration, 100)
    }

    func testTheChromeSeesTheStateItWasBornInto() {
        // Not only changes. A facade that subscribed to changes alone would show
        // an empty player until something moved, which on a paused resume is
        // until the viewer presses something.
        let engine = FakeVideoEngine()
        engine.emit { $0.duration = 240 }

        let facade = EngineVideoPlayer(engine: engine)

        XCTAssertEqual(facade.duration, 240)
    }

    func testTrackListsArriveWithWhatIsSelected() {
        let engine = FakeVideoEngine()
        let facade = EngineVideoPlayer(engine: engine)

        engine.emit {
            $0.audioOptions = [
                TrackOption(id: "en", label: "English", language: "en"),
                TrackOption(id: "nl", label: "Nederlands", language: "nl"),
            ]
            $0.selectedAudioID = "nl"
        }

        XCTAssertEqual(facade.audioOptions.count, 2)
        XCTAssertEqual(facade.selectedAudioID, "nl")
    }

    func testQualityIsChosenByDescriptorRatherThanByPosition() {
        // An index into a ladder the engine has since refiltered picks a
        // different stream than the one whose name was read, and on AVPlayer an
        // index cannot pin a variant at all.
        let engine = FakeVideoEngine()
        let facade = EngineVideoPlayer(engine: engine)
        let wanted = QualityOption(id: "1080-5000", label: "1080p", height: 1080, bitrate: 5000)

        facade.selectQuality(wanted)

        XCTAssertEqual(engine.recordedQuality?.id, "1080-5000")
    }

    func testAutomaticIsASelectionRatherThanAnAbsence() {
        let engine = FakeVideoEngine()
        let facade = EngineVideoPlayer(engine: engine)

        facade.selectQuality(nil)

        XCTAssertTrue(engine.qualityWasSet)
        XCTAssertNil(engine.recordedQuality)
    }

    func testSubtitlesOffIsAlsoASelection() {
        let engine = FakeVideoEngine()
        let facade = EngineVideoPlayer(engine: engine)

        facade.selectSubtitle(nil)

        XCTAssertTrue(engine.subtitleWasSet)
        XCTAssertNil(engine.recordedSubtitle)
    }

    func testTheFakeChromePlayerReallyChangesWhenItIsTold() {
        // The collaborator every view test mounts. If it only recorded calls, a
        // view could bind to a property nothing writes and still pass.
        let player = FakeVideoChromePlayer()
        XCTAssertFalse(player.isPlaying)

        player.togglePlayPause()
        player.seek(to: 42)
        player.selectSubtitle(TrackOption(id: "en", label: "English", language: "en"))

        XCTAssertTrue(player.isPlaying)
        XCTAssertEqual(player.currentTime, 42)
        XCTAssertEqual(player.recordedSeeks.last, 42)
        XCTAssertEqual(player.selectedSubtitleID, "en")
    }
}
