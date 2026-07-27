// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import NoMercyVideoPlayer
import SwiftUI

/// A scrubber divided into the item's chapters.
///
/// Where the segments sit and how far each is filled comes from the shared
/// maths, not from a Swift copy of it. That is the whole point of the slice: the
/// three chromes drawing this bar disagreed about where a chapter began, and the
/// only way they stop disagreeing is by asking the same function.
public struct ChapterProgressBar: View {

    private let chapters: [Chapter]
    private let currentSeconds: Double
    private let duration: Double
    private let bufferedFraction: Double
    private let hoverSeconds: Double?

    public init(
        chapters: [Chapter],
        currentSeconds: Double,
        duration: Double,
        bufferedFraction: Double = 0,
        hoverSeconds: Double? = nil
    ) {
        self.chapters = chapters
        self.currentSeconds = currentSeconds
        self.duration = duration
        self.bufferedFraction = bufferedFraction
        self.hoverSeconds = hoverSeconds
    }

    private var markers: [ChapterMarker] {
        ChapterMarkerMathKt.chapterMarkers(chapters: chapters, duration: duration)
    }

    private var progressPercent: Double {
        duration > 0 ? min(1, max(0, currentSeconds / duration)) * 100 : 0
    }

    /// Nought when nothing is hovered, so every segment fills to nothing rather
    /// than the first one filling to all of it.
    private var hoverPercent: Double {
        guard let hoverSeconds, duration > 0 else { return 0 }
        return min(1, max(0, hoverSeconds / duration)) * 100
    }

    public var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.4))

                Rectangle()
                    .fill(Color.white.opacity(0.08))
                    .frame(width: geometry.size.width * min(1, max(0, bufferedFraction)))

                ForEach(markers, id: \.index) { marker in
                    segment(marker, in: geometry.size)
                }
            }
            .clipShape(Capsule())
        }
        .frame(height: 8)
        .accessibilityLabel("Seek bar")
        .accessibilityValue("\(Int(currentSeconds)) of \(Int(duration)) seconds")
    }

    private func segment(_ marker: ChapterMarker, in size: CGSize) -> some View {
        let left = size.width * marker.leftPercent / 100
        let right = size.width * marker.rightPercent / 100

        // The gap comes off the end of every segment but the last, so the bar
        // still reaches its own right edge.
        let isLast = marker.index == markers.count - 1
        let end = isLast ? right : max(left, right - 4)
        let width = max(0, end - left)

        let hover = ChapterMarkerMathKt.chapterFill(marker: marker, valuePercent: hoverPercent)
        let progress = ChapterMarkerMathKt.chapterFill(marker: marker, valuePercent: progressPercent)

        return ZStack(alignment: .leading) {
            Rectangle().fill(Color.white.opacity(0.3)).frame(width: width)
            Rectangle().fill(Color.white.opacity(0.5)).frame(width: width * hover)
            Rectangle().fill(Color.white.opacity(0.8)).frame(width: width * progress)
        }
        .frame(width: width, alignment: .leading)
        .offset(x: left)
    }
}
