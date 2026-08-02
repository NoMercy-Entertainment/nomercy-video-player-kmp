// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// `.top-row` and the `.slider-bar` inside it, as the browser draws them.
///
/// The Apple chrome had a control row and nothing above it: no position to read
/// at a glance and no way to see how much had buffered. The Compose bar and the
/// browser both carry this strip, so a player photographed on iOS beside either
/// of them was missing the one element a viewer looks at before any button.
///
/// Takes plain numbers rather than the player. An earlier version read
/// `player.chapters` and `player.bufferedPercent` straight from the SwiftUI body
/// and the process aborted through Kotlin/Native's terminate handler — the
/// engine's periodic time observer writes on its own serial queue, and a view
/// body re-reading the same Kotlin object every frame is the other half of that
/// race. Everything here comes from the view model, which is where the rest of
/// the chrome already asks.
struct WebProgressStrip: View {

    /// How far through the item playback is, 0...1.
    let progress: Double

    /// How much has been fetched, 0...1.
    let buffered: Double

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width

            ZStack(alignment: .leading) {
                Capsule().fill(WebStrip.track)
                Capsule().fill(WebStrip.buffer).frame(width: width * clamp(buffered))
                Capsule().fill(WebStrip.progress).frame(width: width * clamp(progress))
            }
        }
        .frame(height: WebStrip.height)
    }

    private func clamp(_ value: Double) -> CGFloat { CGFloat(min(1, max(0, value))) }
}

/// The browser's own strip, in numbers — the same values
/// `scripts/check-render-paint.py` grades the Compose bar against.
enum WebStrip {
    static let height: CGFloat = 8
    static let inset: CGFloat = 24
    static let topMargin: CGFloat = 16

    static let track = Color.white.opacity(0.2)
    static let buffer = Color.white.opacity(0.4)
    static let progress = Color.white

    /// `.bottom-bar-shadow`: `linear-gradient(to top, rgba(0,0,0,0.85),
    /// rgba(0,0,0,0.4), rgba(0,0,0,0))`. Without it the controls are legible
    /// over a dark scene and gone over a bright one.
    static let scrim = LinearGradient(
        colors: [.clear, .black.opacity(0.4), .black.opacity(0.85)],
        startPoint: .top,
        endPoint: .bottom
    )
}
