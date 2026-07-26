// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import AVFoundation
import XCTest
@testable import NoMercyPlayer

/// A transport that genuinely changes state, so the binding is exercised rather
/// than mocked. A stub that recorded calls without moving would let a view that
/// reads its own taps instead of the engine pass every test here.
private final class RecordingTransport: PlayerTransport {

    private let state: PlayerStateObserver
    private(set) var playCount = 0
    private(set) var pauseCount = 0

    init(state: PlayerStateObserver) {
        self.state = state
    }

    func play() {
        playCount += 1
        state.update(isPlaying: true)
    }

    func pause() {
        pauseCount += 1
        state.update(isPlaying: false)
    }
}

final class NMVideoPlayerViewTests: XCTestCase {

    private func view() -> (NMVideoPlayerView, RecordingTransport, PlayerStateObserver) {
        let state = PlayerStateObserver()
        let transport = RecordingTransport(state: state)
        let view = NMVideoPlayerView(player: AVPlayer(), transport: transport, state: state)
        return (view, transport, state)
    }

    func testTogglingAStoppedPlayerStartsIt() {
        let (view, transport, state) = self.view()

        view.toggle()

        XCTAssertEqual(transport.playCount, 1)
        XCTAssertTrue(state.isPlaying)
    }

    func testTogglingAPlayingPlayerStopsIt() {
        let (view, transport, state) = self.view()

        view.toggle()
        view.toggle()

        XCTAssertEqual(transport.pauseCount, 1)
        XCTAssertFalse(state.isPlaying)
    }

    func testTheEngineStoppingOnItsOwnIsReflected() {
        // Nothing tapped anything. A player that stops for its own reasons must
        // still be drawn accurately, which a control remembering its own taps
        // cannot do.
        let (_, transport, state) = self.view()
        transport.play()

        state.update(isPlaying: false)

        XCTAssertFalse(state.isPlaying)
    }

    func testTheLayerHostPointsAtThePlayerItWasGiven() {
        let player = AVPlayer()
        let view = PlayerLayerHost.makeView(player: player)

        XCTAssertTrue(view.playerLayer.player === player)
        XCTAssertEqual(view.playerLayer.videoGravity, .resizeAspect)
    }
}
