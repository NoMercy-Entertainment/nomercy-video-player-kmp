// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// The pieces of the television chrome.
///
/// Each is its own view so the focus engine has something to move between: a
/// television is navigated by focus, and one large view is one stop.
#if os(tvOS)

/// What is playing, along the top.
@available(tvOS 15.0, *)
public struct TvTopBar: View {

    private let title: String
    private let subtitle: String

    public init(title: String, subtitle: String) {
        self.title = title
        self.subtitle = subtitle
    }

    public var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.title2.bold())
                    .lineLimit(1)

                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.body)
                        .lineLimit(1)
                }
            }

            Spacer()
        }
        .foregroundColor(.white)
        .accessibilityElement(children: .combine)
    }
}

/// Where in the film, along the bottom.
///
/// Elapsed on the left and remaining on the right, which is the pair a viewer
/// deciding whether to start another episode is actually asking about. The music
/// chrome answers the other question, because somebody looking at a song wants
/// to know how long it is.
@available(tvOS 15.0, *)
public struct TvTransportBar: View {

    private let elapsed: String
    private let remaining: String
    private let progress: Double

    public init(elapsed: String, remaining: String, progress: Double) {
        self.elapsed = elapsed
        self.remaining = remaining
        self.progress = progress
    }

    public var body: some View {
        VStack(spacing: 8) {
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Color.white.opacity(0.25)
                    Color.white.frame(width: geometry.size.width * min(1, max(0, progress)))
                }
            }
            .frame(height: 6)

            HStack {
                Text(elapsed)
                Spacer()
                Text("-\(remaining)")
            }
            .font(.callout)
        }
        .foregroundColor(.white)
        .accessibilityLabel("\(elapsed), \(remaining) remaining")
    }
}

/// Where a scrub has got to.
///
/// Its own layer rather than a state of the bar, because while somebody is
/// hunting the bar is hidden: the strip is what they are looking at and drawing
/// both would cover it.
@available(tvOS 15.0, *)
public struct TvSeekStrip: View {

    private let seconds: Double
    private let duration: Double

    public init(seconds: Double, duration: Double) {
        self.seconds = seconds
        self.duration = duration
    }

    public var body: some View {
        VStack {
            Spacer()

            Text(formatTvTime(seconds))
                .font(.system(size: 48, weight: .semibold))
                .foregroundColor(.white)
                .padding(24)
                .background(Color.black.opacity(0.6))
                .cornerRadius(12)

            Spacer().frame(height: 120)
        }
        .accessibilityLabel(formatTvTime(seconds))
    }
}

/// Whichever list the machine says is open.
///
/// One host rather than four independent overlays: the machine models this as
/// one value precisely so two cannot be open at once, and four views each
/// deciding for themselves would put that back.
@available(tvOS 15.0, *)
public struct TvDialogHost: View {

    private let model: TvChromeViewModel

    public init(model: TvChromeViewModel) {
        self.model = model
    }

    public var body: some View {
        VStack {
            Text(heading)
                .font(.title)
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.opacity(0.85))
        .focusSection()
    }

    private var heading: String {
        if model.showsEpisodesDialog { return "Episodes" }
        if model.showsLanguageDialog { return "Audio" }
        if model.showsSubtitleDialog { return "Subtitles" }
        if model.showsSubtitleSearchDialog { return "Find subtitles" }
        return ""
    }
}
#endif
