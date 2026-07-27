// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

#if os(tvOS)

/// What the SwiftUI chrome observes, and the only place a press is translated.
///
/// It owns no rules. Every decision belongs to the shared controller, which is
/// tested in Kotlin against twenty cases; what this adds is the two things
/// SwiftUI needs and Kotlin cannot give it: an observable object, and the
/// vocabulary of a move command.
///
/// Keeping it this thin is deliberate. A model that decided anything would be a
/// second state machine, and the two would disagree the first time one of them
/// was changed.
public final class TvChromeModel: ObservableObject {

    @Published public private(set) var preScreenVisible: Bool = true
    @Published public private(set) var controlsVisible: Bool = false
    @Published public private(set) var seekMode: Bool = false
    @Published public private(set) var isPlaying: Bool = false

    @Published public var title: String = ""
    @Published public var subtitle: String = ""
    @Published public var elapsed: String = "0:00"
    @Published public var remaining: String = "0:00"
    @Published public var previewLabel: String = "0:00"
    @Published public var progress: Double = 0
    @Published public var hasEpisodes: Bool = false

    private let actions: TvChromeActions

    public init(actions: TvChromeActions) {
        self.actions = actions
    }

    /// The remote, in the vocabulary the shared controller understands.
    ///
    /// tvOS reports a swipe on the touch surface and a press on the ring as the
    /// same move command, which is right: to a viewer they are the same gesture,
    /// and the controller was never told which kind of remote it has.
    public func move(_ direction: MoveCommandDirection) {
        switch direction {
        case .left: actions.onLeft()
        case .right: actions.onRight()
        case .up: actions.onUp()
        case .down: actions.onDown()
        @unknown default: break
        }
    }

    public func back() { actions.onBack() }

    public func togglePlay() { actions.onTogglePlay() }

    public func play() { actions.onPlay() }

    public func restart() { actions.onRestart() }

    public func openEpisodes() { actions.onOpenEpisodes() }

    public func openSubtitles() { actions.onOpenSubtitles() }

    /// Pushed in by whatever owns the shared controller, rather than pulled.
    ///
    /// The controller publishes its state as a flow, and a host bridges that
    /// flow to here. Doing it the other way round would mean this file holding a
    /// Kotlin object and deciding when to read it, which is where the second
    /// state machine starts.
    public func apply(preScreen: Bool, controls: Bool, seeking: Bool, playing: Bool) {
        preScreenVisible = preScreen
        controlsVisible = controls
        seekMode = seeking
        isPlaying = playing
    }
}

/// What the chrome asks for, as plain closures.
///
/// Closures rather than a protocol so a host can bridge them to the Kotlin
/// controller in one place without writing a conforming type per screen.
public struct TvChromeActions {

    public var onLeft: () -> Void
    public var onRight: () -> Void
    public var onUp: () -> Void
    public var onDown: () -> Void
    public var onBack: () -> Void
    public var onTogglePlay: () -> Void
    public var onPlay: () -> Void
    public var onRestart: () -> Void
    public var onOpenEpisodes: () -> Void
    public var onOpenSubtitles: () -> Void

    public init(
        onLeft: @escaping () -> Void = {},
        onRight: @escaping () -> Void = {},
        onUp: @escaping () -> Void = {},
        onDown: @escaping () -> Void = {},
        onBack: @escaping () -> Void = {},
        onTogglePlay: @escaping () -> Void = {},
        onPlay: @escaping () -> Void = {},
        onRestart: @escaping () -> Void = {},
        onOpenEpisodes: @escaping () -> Void = {},
        onOpenSubtitles: @escaping () -> Void = {}
    ) {
        self.onLeft = onLeft
        self.onRight = onRight
        self.onUp = onUp
        self.onDown = onDown
        self.onBack = onBack
        self.onTogglePlay = onTogglePlay
        self.onPlay = onPlay
        self.onRestart = onRestart
        self.onOpenEpisodes = onOpenEpisodes
        self.onOpenSubtitles = onOpenSubtitles
    }
}

#endif
