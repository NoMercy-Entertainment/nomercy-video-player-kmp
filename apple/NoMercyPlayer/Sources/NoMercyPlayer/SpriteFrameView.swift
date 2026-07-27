// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import NoMercyVideoPlayer
import SwiftUI
import UIKit

/// The shared cue type, under a name worth typing.
///
/// A dependency's classes come through the framework carrying the module they
/// came from — `Nomercy_player_core_kmpSpriteCue` — which is accurate and
/// unusable. The alias costs nothing and is the difference between an API that
/// reads as Swift and one that reads as a build artifact.
public typealias SpriteCue = Nomercy_player_core_kmpSpriteCue
public typealias Chapter = Nomercy_player_core_kmpChapter

/// Where one frame sits inside the sheet, once the sheet is drawn at a size that
/// makes that frame the width the chrome asked for.
///
/// Apple decodes the whole sheet at full resolution rather than reading a region
/// out of it, which is the opposite of what Compose does and is right for the
/// platform: there is no 256MB heap to stay inside, and `UIImage` gives no
/// region reader worth using. What that costs is this — the frame has to be cut
/// out at draw time by scaling the whole sheet and sliding the wanted frame into
/// view.
///
/// There is no downsample correction here, and there must not be. The Android
/// version had one only because it was shrinking the sheet to fit in memory,
/// which moved every cue coordinate with it.
public struct SpriteCrop: Equatable {

    /// What the whole sheet is scaled by so this frame comes out `width` wide.
    public let scale: CGFloat

    /// How far the sheet slides so the frame lands at the origin.
    public let offset: CGSize

    /// The visible box, shaped by the frame's own aspect.
    public let size: CGSize

    public init(cue: SpriteCue, width: CGFloat) {
        let declaredWidth = CGFloat(cue.width)
        let declaredHeight = CGFloat(cue.height)

        // A cue with no width would scale the sheet by infinity and lay out a
        // frame nobody can see. One is wrong by a factor nobody notices.
        scale = width / max(1, declaredWidth)
        offset = CGSize(width: -CGFloat(cue.x) * scale, height: -CGFloat(cue.y) * scale)

        let aspect = declaredHeight > 0 ? declaredWidth / declaredHeight : 16.0 / 9.0
        size = CGSize(width: width, height: width / aspect)
    }
}

/// One preview frame, cut out of the sheet it shares with every other frame.
public struct SpriteFrameView: View {

    private let sheet: UIImage
    private let cue: SpriteCue
    private let width: CGFloat

    public init(sheet: UIImage, cue: SpriteCue, width: CGFloat = 200) {
        self.sheet = sheet
        self.cue = cue
        self.width = width
    }

    public var body: some View {
        let crop = SpriteCrop(cue: cue, width: width)

        Image(uiImage: sheet)
            .resizable(resizingMode: .stretch)
            .interpolation(.medium)
            .frame(
                width: sheet.size.width * crop.scale,
                height: sheet.size.height * crop.scale,
                alignment: .topLeading
            )
            .offset(x: crop.offset.width, y: crop.offset.height)
            .frame(width: crop.size.width, height: crop.size.height, alignment: .topLeading)
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}

/// The frame a scrub position lands on, chosen by the shared lookup rather than
/// by a second implementation of it.
///
/// Three clients each had their own answer to this before, and the one that
/// differed showed a viewer the scene after the one they were pointing at.
public func spriteFrame(in frames: [SpriteCue], at seconds: Double) -> SpriteCue? {
    SpriteFramesKt.frameAt(frames: frames, seconds: seconds)
}
