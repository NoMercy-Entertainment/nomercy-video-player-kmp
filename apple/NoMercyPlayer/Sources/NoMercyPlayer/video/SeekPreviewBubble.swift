// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// Which chapter a moment falls in.
///
/// Its own function so the rule can be asserted without a render, and because
/// the rule has an edge that a view body hides: a chapter owns its start and not
/// its end. Without that, the boundary second belongs to two chapters and which
/// one wins depends on the order they happen to be in.
public func chapterTitle(at seconds: Double, in chapters: [Chapter]) -> String? {
    let sorted = chapters.sorted { $0.startTime < $1.startTime }

    guard let current = sorted.last(where: { $0.startTime <= seconds }) else { return nil }
    return current.title.isEmpty ? nil : current.title
}

/// The popup above the scrubber while somebody is hunting.
///
/// A frame where there is one, and the time either way. The fallback is the
/// point: a server that has not generated a sprite sheet yet is the ordinary
/// case on a freshly added film, and a bubble that showed nothing at all would
/// make scrubbing feel broken rather than unillustrated.
@available(iOS 15.0, tvOS 15.0, *)
public struct SeekPreviewBubble: View {

    private let seconds: Double
    private let sheet: UIImage?
    private let frames: [SpriteCue]
    private let chapters: [Chapter]
    private let width: CGFloat

    public init(
        seconds: Double,
        sheet: UIImage? = nil,
        frames: [SpriteCue] = [],
        chapters: [Chapter] = [],
        width: CGFloat = 160
    ) {
        self.seconds = seconds
        self.sheet = sheet
        self.frames = frames
        self.chapters = chapters
        self.width = width
    }

    public var body: some View {
        VStack(spacing: 4) {
            if let sheet, let cue = spriteFrame(in: frames, at: seconds) {
                SpriteFrameView(sheet: sheet, cue: cue, width: width)
                    .cornerRadius(4)
            }

            if let title = chapterTitle(at: seconds, in: chapters) {
                Text(title)
                    .font(.caption2)
                    .lineLimit(1)
            }

            Text(formatTime(seconds))
                .font(.caption)
                .monospacedDigit()
        }
        .padding(6)
        .background(Color.black.opacity(0.75))
        .foregroundColor(.white)
        .cornerRadius(6)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(formatTime(seconds))
    }
}
