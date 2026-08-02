// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// One of the web chrome's icons, drawn at the size a control wants it.
///
/// `FluentIcon` is a `Shape`, which is the right thing for it to be and the
/// wrong thing to write out at every call site: a shape has no size of its own,
/// so each button would repeat a fill and a frame, and the one that forgot would
/// draw a glyph stretched to whatever the layout gave it.
///
/// This exists because the generated icons had no users at all. The chrome drew
/// SF Symbols — `xmark`, `airplayvideo`, `waveform`, `captions.bubble`,
/// `gearshape` — while the same package carried the browser's own path data, so
/// the iOS player looked like an Apple app rather than like the player it is a
/// port of. Nothing in the build noticed, because both sides compiled.
public struct PlayerGlyph: View {

    private let icon: FluentIcon
    private let size: CGFloat

    /// `margin-right: 8px` on the web's top-bar buttons.
    public static let buttonGap: CGFloat = 8

    /// The web renders these through `svgFromIcon`, whose default size is 22.
    public init(_ icon: FluentIcon, size: CGFloat = 22) {
        self.icon = icon
        self.size = size
    }

    public var body: some View {
        // No colour of its own. `fill()` and `stroke()` both take the foreground
        // style, so the bar tints every glyph in one place exactly as
        // `color: #fff` does on the web, and a control cannot end up a different
        // white from its neighbour.
        //
        // Which of the two is the icon's own answer, because it is the web's:
        // `svgFromIcon` reads `classes` for `fill-none` and renders those two
        // glyphs with `stroke-width: 2` and round caps. Filled, the chapter
        // jumps' `M14 18L8 12L14 6` is a solid triangle where the browser draws
        // a thin chevron — which is what the Compose bar drew until the
        // generator started carrying the flag.
        Group {
            if icon.stroked {
                // Scaled with the glyph. `stroke-width: 2` is two units of the
                // 24-unit viewBox, so a browser drawing at 22 strokes 1.83
                // px — a flat 2pt here would be a visibly heavier chevron
                // beside a bar of 22px icons.
                icon.stroke(
                    style: StrokeStyle(
                        lineWidth: 2 * size / FluentIcon.viewport,
                        lineCap: .round,
                        lineJoin: .round
                    )
                )
            } else {
                icon.fill()
            }
        }
        .frame(width: size, height: size)
    }
}
