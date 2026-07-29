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
        // No colour of its own. `fill()` takes the foreground style, so the bar
        // tints every glyph in one place exactly as `color: #fff` does on the
        // web, and a control cannot end up a different white from its neighbour.
        icon
            .fill()
            .frame(width: size, height: size)
    }
}
