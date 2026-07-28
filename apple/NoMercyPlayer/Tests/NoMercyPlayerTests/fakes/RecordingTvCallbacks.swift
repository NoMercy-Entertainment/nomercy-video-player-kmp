// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import Foundation
import NoMercyVideoPlayer

/// What the chrome asked the player to do, in order.
///
/// The Swift counterpart of the recorder the Kotlin tests drive the same machine
/// with. Order matters: "pause then seek" and "seek then pause" leave a viewer
/// in different places, and a set of booleans cannot tell them apart.
final class RecordingTvCallbacks: TvChromeCallbacks {

    private(set) var calls: [String] = []
    private(set) var seeks: [Float] = []
    private(set) var overrides: [Float?] = []

    func play() { calls.append("play") }

    func pause() { calls.append("pause") }

    func togglePlay() { calls.append("togglePlay") }

    func seek(seconds: Float) {
        calls.append("seek")
        seeks.append(seconds)
    }

    func overrideTime(seconds: KotlinFloat?) {
        calls.append("overrideTime")
        overrides.append(seconds?.floatValue)
    }

    func restart() { calls.append("restart") }

    func next() { calls.append("next") }

    func exitPlayer() { calls.append("exitPlayer") }
}

/// A scheduler that runs the work now.
///
/// The autohide is a timer in production and a hazard in a test: a suite that
/// waited four seconds per assertion would be a suite nobody runs. The machine
/// takes the scheduler as a parameter for exactly this reason.
final class ImmediateScheduler: Scheduler {

    func schedule(delayMs: Int64, action: @escaping () -> Void) -> Cancellable {
        action()
        return NoOpCancellable()
    }
}

final class NoOpCancellable: Cancellable {
    func cancel() {}
}
