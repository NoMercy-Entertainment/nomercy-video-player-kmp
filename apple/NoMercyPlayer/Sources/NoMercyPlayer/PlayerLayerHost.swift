// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import AVFoundation
import SwiftUI

/// The engine's own layer, hosted in SwiftUI.
///
/// AVFoundation draws through a CALayer that is handed the player, not a frame
/// buffer, so the view's whole job is to own a layer, keep it the size of its
/// view, and point it at the right player. Everything else about playback goes
/// through the Kotlin engine.
public struct PlayerLayerHost: UIViewRepresentable {

    private let player: AVPlayer

    public init(player: AVPlayer) {
        self.player = player
    }

    public func makeUIView(context: Context) -> PlayerLayerView {
        Self.makeView(player: player)
    }

    /// Split from makeUIView because a Context cannot be constructed outside
    /// SwiftUI, and the part worth checking — that the layer ends up pointing at
    /// the right player, with the right gravity — does not need one.
    public static func makeView(player: AVPlayer) -> PlayerLayerView {
        let view = PlayerLayerView()
        view.playerLayer.player = player
        // Fit, not fill: a player that crops by default is a player that hides
        // subtitles burned into the edge of a scope print.
        view.playerLayer.videoGravity = .resizeAspect
        return view
    }

    /// Re-pointed rather than rebuilt. Replacing the view would tear down the
    /// output and restart the video on every recomposition.
    public func updateUIView(_ view: PlayerLayerView, context: Context) {
        if view.playerLayer.player !== player {
            view.playerLayer.player = player
        }
    }
}

/// A view whose backing layer *is* the player layer.
///
/// Backing it directly rather than adding a sublayer is what makes the video
/// resize with the view for free. A sublayer would need its frame set by hand on
/// every layout pass, and the bug that produces — video that lags a rotation by
/// one frame — is the kind nobody files.
public final class PlayerLayerView: UIView {

    public override class var layerClass: AnyClass { AVPlayerLayer.self }

    public var playerLayer: AVPlayerLayer {
        guard let layer = layer as? AVPlayerLayer else {
            fatalError("layerClass is AVPlayerLayer and UIKit gave back something else")
        }
        return layer
    }
}
