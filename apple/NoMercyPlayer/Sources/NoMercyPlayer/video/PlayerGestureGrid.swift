// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// What a gesture at a place on the picture means.
///
/// Its own type so the rule can be asserted without a render pass. A grid whose
/// meaning is computed inside a view body is a grid whose only test is a
/// screenshot, and the interesting failures here are all "the wrong cell did the
/// wrong thing" — invisible in a screenshot and obvious in a table.
public enum GestureAction: Equatable, Sendable {
    case togglePlayPause
    case seek(Double)
    case brightness(Double)
    case volume(Double)
    case nothing
}

/// The invisible three-by-three over the picture.
///
/// The middle column is playback, the sides are seeking, and the outer columns
/// carry brightness on the left and volume on the right — the arrangement every
/// player on the platform uses, which is the whole reason it is discoverable
/// without a legend.
///
/// Brightness and volume are the device's, not the engine's. A player that put
/// screen brightness in its own state would be a player fighting whatever else
/// on the phone changes it.
public struct GestureGrid: Sendable {

    public static let columns = 3
    public static let rows = 3

    public let seekSeconds: Double
    public let step: Double

    public init(seekSeconds: Double = 10, step: Double = 0.05) {
        self.seekSeconds = seekSeconds
        self.step = step
    }

    /// A double tap, which is the only gesture with a per-cell meaning. Single
    /// taps wake the chrome wherever they land: a viewer reaching for the
    /// controls should not have to hit a particular third of the screen.
    public func doubleTap(row: Int, column: Int) -> GestureAction {
        guard row == 1 else { return .nothing }

        switch column {
        case 0: return .seek(-seekSeconds)
        case 1: return .togglePlayPause
        case 2: return .seek(seekSeconds)
        default: return .nothing
        }
    }

    /// A vertical drag. Up is more of whatever that side controls, which is the
    /// direction every hardware control on the device already moves.
    public func verticalDrag(column: Int, deltaY: Double) -> GestureAction {
        let amount = -deltaY * step

        switch column {
        case 0: return .brightness(amount)
        case 2: return .volume(amount)
        default: return .nothing
        }
    }

    /// Which cell a point falls in. Clamped, because a drag that leaves the
    /// surface reports a position outside it and an unclamped index reads off
    /// the end of the grid.
    public func cell(at point: CGPoint, in size: CGSize) -> (row: Int, column: Int) {
        guard size.width > 0, size.height > 0 else { return (1, 1) }

        let column = min(Self.columns - 1, max(0, Int(point.x / size.width * CGFloat(Self.columns))))
        let row = min(Self.rows - 1, max(0, Int(point.y / size.height * CGFloat(Self.rows))))
        return (row, column)
    }
}

/// The grid, as a view.
///
/// Transparent and over the whole picture, because on a phone the picture is the
/// control surface and somebody aiming at a target they cannot see still hits it
/// every time.
@available(iOS 15.0, *)
public struct PlayerGestureGrid: View {

    private let grid: GestureGrid
    private let onAction: (GestureAction) -> Void
    private let onSingleTap: () -> Void

    public init(
        grid: GestureGrid = GestureGrid(),
        onSingleTap: @escaping () -> Void,
        onAction: @escaping (GestureAction) -> Void
    ) {
        self.grid = grid
        self.onSingleTap = onSingleTap
        self.onAction = onAction
    }

    @State private var lastTouch: CGPoint = .zero

    public var body: some View {
        GeometryReader { geometry in
            Color.black.opacity(0.001)
                .contentShape(Rectangle())
                // Where the touch landed, from a drag that never has to move.
                // SpatialTapGesture would say it directly and arrived in iOS 16;
                // this package's floor is 15, and raising a deployment target to
                // avoid writing four lines is a cost paid by every consumer.
                .simultaneousGesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            lastTouch = value.startLocation

                            let cell = grid.cell(at: value.startLocation, in: geometry.size)
                            let action = grid.verticalDrag(column: cell.column, deltaY: value.translation.height)
                            if action != .nothing {
                                onAction(action)
                            }
                        }
                )
                // The double tap before the single one. SwiftUI resolves the
                // longer sequence first when both are attached, and a single tap
                // registered ahead of it would claim every touch.
                .gesture(
                    TapGesture(count: 2).onEnded {
                        let cell = grid.cell(at: lastTouch, in: geometry.size)
                        onAction(grid.doubleTap(row: cell.row, column: cell.column))
                    }
                )
                .gesture(TapGesture(count: 1).onEnded { onSingleTap() })
        }
    }
}
