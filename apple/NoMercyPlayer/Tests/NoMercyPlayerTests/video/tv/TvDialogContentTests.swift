// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyPlayer

/// What the television dialogs list. The host view only draws these rows.
final class TvDialogContentTests: XCTestCase {

    private let audio = [
        TrackOption(id: "a1", label: "English", language: "en"),
        TrackOption(id: "a2", label: "Japanese", language: "ja"),
    ]
    private let subtitles = [
        TrackOption(id: "s1", label: "English", language: "en"),
        TrackOption(id: "s2", label: "Dutch", language: "nl"),
    ]
    private let episodes = [
        TvDialogRow(id: "e1", title: "One", detail: "S1 E1"),
        TvDialogRow(id: "e2", title: "Two", detail: "S1 E2", isCurrent: true),
    ]

    private func content(_ kind: TvDialogKind, subtitleID: String? = "s2") -> TvDialogContent {
        TvDialogContent.make(
            kind: kind,
            audio: audio,
            selectedAudioID: "a1",
            subtitles: subtitles,
            selectedSubtitleID: subtitleID,
            episodes: episodes
        )
    }

    func testTheAudioDialogHasOneRowPerAudioTrack() {
        let result = content(.audio)

        XCTAssertEqual(result.heading, "Audio")
        XCTAssertEqual(result.rows.map(\.id), ["a1", "a2"])
        XCTAssertEqual(result.rows.map(\.isCurrent), [true, false])
    }

    func testTheSubtitleDialogOffersOffThenOneRowPerTrack() {
        let result = content(.subtitles)

        XCTAssertEqual(result.heading, "Subtitles")
        XCTAssertEqual(result.rows.map(\.id), [TvDialogRow.offID, "s1", "s2"])
        XCTAssertEqual(result.rows.map(\.isCurrent), [false, false, true])
    }

    func testOffIsTheCurrentRowWhenNoSubtitleIsSelected() {
        let result = content(.subtitles, subtitleID: nil)

        XCTAssertEqual(result.rows.first?.isCurrent, true)
    }

    func testTheEpisodeDialogListsTheEpisodesItWasGiven() {
        let result = content(.episodes)

        XCTAssertEqual(result.heading, "Episodes")
        XCTAssertEqual(result.rows, episodes)
    }

    func testTheSearchDialogKeepsItsHeadingAndHasNoRows() {
        let result = content(.subtitleSearch)

        XCTAssertEqual(result.heading, "Find subtitles")
        XCTAssertTrue(result.rows.isEmpty)
    }
}
