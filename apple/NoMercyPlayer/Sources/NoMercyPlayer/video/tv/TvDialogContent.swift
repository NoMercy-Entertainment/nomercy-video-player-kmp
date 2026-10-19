// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import Foundation

/// Which list the television dialog shows.
public enum TvDialogKind: Equatable, Sendable {
    case episodes
    case audio
    case subtitles
    case subtitleSearch
}

/// One choosable line in a television dialog.
public struct TvDialogRow: Identifiable, Equatable, Sendable {
    /// The id subtitles use for the explicit off row.
    public static let offID = "nomercy.off"

    public let id: String
    public let title: String
    public let detail: String?
    public let isCurrent: Bool

    public init(id: String, title: String, detail: String? = nil, isCurrent: Bool = false) {
        self.id = id
        self.title = title
        self.detail = detail
        self.isCurrent = isCurrent
    }
}

/// What a television dialog draws: its heading and the rows under it.
public struct TvDialogContent: Equatable, Sendable {
    public let heading: String
    public let rows: [TvDialogRow]

    public static func make(
        kind: TvDialogKind,
        audio: [TrackOption],
        selectedAudioID: String?,
        subtitles: [TrackOption],
        selectedSubtitleID: String?,
        episodes: [TvDialogRow],
        off: String = "Off"
    ) -> TvDialogContent {
        switch kind {
        case .episodes:
            return TvDialogContent(heading: "Episodes", rows: episodes)

        case .audio:
            let rows = audio.map {
                TvDialogRow(id: $0.id, title: $0.label, detail: $0.language, isCurrent: $0.id == selectedAudioID)
            }
            return TvDialogContent(heading: "Audio", rows: rows)

        case .subtitles:
            let offRow = TvDialogRow(id: TvDialogRow.offID, title: off, isCurrent: selectedSubtitleID == nil)
            let rows = subtitles.map {
                TvDialogRow(id: $0.id, title: $0.label, detail: $0.language, isCurrent: $0.id == selectedSubtitleID)
            }
            return TvDialogContent(heading: "Subtitles", rows: [offRow] + rows)

        case .subtitleSearch:
            return TvDialogContent(heading: "Find subtitles", rows: [])
        }
    }
}
