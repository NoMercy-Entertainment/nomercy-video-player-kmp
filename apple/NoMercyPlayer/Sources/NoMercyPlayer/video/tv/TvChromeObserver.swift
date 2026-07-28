// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import NoMercyVideoPlayer
import SwiftUI

/// The machine's state, as something SwiftUI redraws from.
///
/// A `StateFlow` is not an `ObservableObject`, so somebody has to carry one into
/// the other. Doing it here once is the difference between a chrome that follows
/// the machine and one where every view remembers to poll.
///
/// The subscription is a task that lives as long as this object. A view that
/// started one per body would start a new one on every redraw, and the old ones
/// would keep publishing into a view that no longer exists.
@MainActor
public final class TvChromeObserver: ObservableObject {

    @Published public private(set) var model: TvChromeViewModel

    public let controller: TvChromeController

    private var subscription: Task<Void, Never>?

    public init(controller: TvChromeController) {
        self.controller = controller
        self.model = TvChromeViewModel(ui: controller.ui.value)

        subscription = Task { [weak self] in
            guard let stream = self?.controller.ui else { return }

            for await ui in stream {
                guard let self else { return }
                self.model = TvChromeViewModel(ui: ui)
            }
        }
    }

    deinit {
        subscription?.cancel()
    }

    /// A remote gesture, handed to the machine.
    ///
    /// The return says whether the press was consumed, which is what lets a
    /// focused button take its own centre press and what lets the host decide
    /// that a back press at the bottom means leaving the player.
    @discardableResult
    public func send(_ gesture: TvRemote.Gesture) -> Bool {
        controller.onKey(key: TvRemote.key(for: gesture))
    }

    @discardableResult
    public func back() -> Bool {
        controller.onBack()
    }
}
