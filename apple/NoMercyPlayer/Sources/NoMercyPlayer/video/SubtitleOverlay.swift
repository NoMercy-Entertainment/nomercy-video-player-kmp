// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// One line of dialogue and when it is on screen.
///
/// The library's own value rather than the engine's cue type, because the
/// renderer has to be drivable from a fixture: subtitle timing is the one thing
/// here worth asserting and it should not need a running player to assert it.
public struct SubtitleCue: Equatable, Sendable {
    public let start: Double
    public let end: Double
    public let lines: [String]

    public init(start: Double, end: Double, lines: [String]) {
        self.start = start
        self.end = end
        self.lines = lines
    }

    /// A cue owns its start and not its end, so two cues meeting at a second do
    /// not both show on it. Overlapping cues are a real thing subtitle files do —
    /// a sign and a line of dialogue at once — and this only excludes the
    /// boundary, not the overlap.
    public func covers(_ seconds: Double) -> Bool {
        seconds >= start && seconds < end
    }
}

/// Which lines are on screen at a moment.
///
/// The cues arrive already parsed. Fetching a sidecar, authenticating for it and
/// turning bytes into cues is the subtitle plugin's work; this renders what it
/// produced. The view this is seeded from did the fetch itself, with the
/// application's token store in hand, which is why it could not be used anywhere
/// else and why it broke whenever auth changed.
@MainActor
public final class SubtitleRenderer: ObservableObject {

    @Published public private(set) var visible: [String] = []

    private var cues: [SubtitleCue] = []

    public init() {}

    public func loadCues(_ cues: [SubtitleCue]) {
        self.cues = cues.sorted { $0.start < $1.start }
        visible = []
    }

    /// Every cue covering the moment, not the first. Overlapping cues are how a
    /// sign and a line of dialogue appear together, and showing only one of them
    /// drops half of what was written.
    public func update(currentTime seconds: Double) {
        let lines = cues.filter { $0.covers(seconds) }.flatMap(\.lines)

        // Assigned only on a change. A published property written every frame
        // redraws the whole overlay sixty times a second to say the same thing.
        if lines != visible {
            visible = lines
        }
    }

    public func clear() {
        cues = []
        visible = []
    }
}

/// The lines, over everything.
///
/// Bottom-centred and outlined rather than boxed, which is what makes them
/// readable over a bright picture without a slab of black across it.
@available(iOS 15.0, tvOS 15.0, *)
public struct SubtitleOverlay: View {

    @ObservedObject private var renderer: SubtitleRenderer
    private let style: SubtitleStyle

    public init(renderer: SubtitleRenderer, style: SubtitleStyle = SubtitleStyle()) {
        self.renderer = renderer
        self.style = style
    }

    public var body: some View {
        VStack(spacing: 2) {
            ForEach(Array(renderer.visible.enumerated()), id: \.offset) { _, line in
                Text(line)
                    .font(.system(size: style.size, weight: style.weight))
                    .foregroundColor(style.colour)
                    .shadow(color: style.outline, radius: 1, x: 1, y: 1)
                    .shadow(color: style.outline, radius: 1, x: -1, y: -1)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.bottom, style.bottomInset)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .allowsHitTesting(false)
        .accessibilityHidden(renderer.visible.isEmpty)
    }
}

/// How the lines are drawn.
///
/// The viewer's preference, held by whoever knows about preferences. A library
/// that stored it would be a second place the setting lives.
public struct SubtitleStyle: Sendable {
    public var size: CGFloat = 22
    public var weight: Font.Weight = .semibold
    public var colour: Color = .white
    public var outline: Color = .black
    public var bottomInset: CGFloat = 48

    public init() {}
}
