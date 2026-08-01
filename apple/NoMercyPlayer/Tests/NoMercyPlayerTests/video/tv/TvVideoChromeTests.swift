// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
@testable import NoMercyPlayer
import NoMercyVideoPlayer

/// The television chrome, driven the way a Siri Remote drives it.
///
/// What is asserted is that a remote press reaches the shared Kotlin machine and
/// that the view reads its answer — not that Swift reproduces the rules. The
/// rules have their own tests, in Kotlin, which now run on this platform too.
///
/// A second implementation of the five-rule autohide is the defect this whole
/// campaign exists to stop shipping again, so a test here that asserted a rule
/// would be evidence of exactly the thing it is meant to prevent.
@MainActor
final class TvVideoChromeTests: XCTestCase {

    private func controller(
        _ callbacks: RecordingTvCallbacks = RecordingTvCallbacks()
    ) -> TvChromeController {
        TvChromeController(
            callbacks: callbacks,
            scheduler: ImmediateScheduler(),
            playing: false,
            startOnPreScreen: false,
            content: nil,
            // AUTO_HIDE_MS. A Kotlin default does not reach the ObjC header, so
            // every parameter the controller grows becomes required here — the
            // same reason swift-conformance in core carries hdrOnSdr by hand.
            autoHideMs: 5_000
        )
    }

    func testARemotePressReachesTheSharedMachine() {
        // The whole point of the move that made this file possible: the tvOS
        // view routes into the same controller the Compose television chrome
        // uses, rather than into a Swift copy of its rules.
        let callbacks = RecordingTvCallbacks()
        let machine = controller(callbacks)

        _ = machine.onKey(key: TvRemote.key(for: .left))

        XCTAssertTrue(machine.ui.value.seekMode, "left enters seeking, decided in Kotlin")
    }

    func testPlayPauseOnTheRemoteReachesTheCallbacks() {
        let callbacks = RecordingTvCallbacks()
        let machine = controller(callbacks)

        _ = machine.onKey(key: TvRemote.key(for: .playPause))

        XCTAssertEqual(callbacks.calls.last, "togglePlay")
    }

    func testEveryRemoteGestureMapsToADistinctKey() {
        // A mapping that collapsed two gestures onto one key would send select
        // where a viewer pressed play, and the failure is silent: both do
        // something, and only one of them is what was asked for.
        let mapped = TvRemote.Gesture.allCases.map { TvRemote.key(for: $0) }

        XCTAssertEqual(Set(mapped.map { $0.combo }).count, TvRemote.Gesture.allCases.count)
    }

    func testTheViewModelShowsTheDialogTheMachineSaysIsOpen() {
        let ui = TvChromeUi(
            preScreenVisible: false,
            controlsVisible: true,
            seekMode: false,
            dialog: .language,
            topBarHasFocus: false,
            volumeIndicatorVisible: false
        )

        let model = TvChromeViewModel(ui: ui)

        XCTAssertTrue(model.showsControls)
        XCTAssertTrue(model.showsLanguageDialog)
        XCTAssertFalse(model.showsSeekStrip)
        XCTAssertFalse(model.showsEpisodesDialog)
    }

    func testExactlyOneDialogIsEverShown() {
        // The reason the machine models this as one value rather than six flags.
        // A view that answered each dialog independently could draw two at once
        // from a state that cannot occur, and the stack of them would look like
        // a rendering bug rather than a modelling one.
        for dialog in [TvDialog.none, .preScreen, .episodes, .language, .subtitle, .subtitleSearch] {
            let model = TvChromeViewModel(
                ui: TvChromeUi(
                    preScreenVisible: dialog == .preScreen,
                    controlsVisible: true,
                    seekMode: false,
                    dialog: dialog,
                    topBarHasFocus: false,
                    volumeIndicatorVisible: false
                )
            )

            let shown = [
                model.showsEpisodesDialog,
                model.showsLanguageDialog,
                model.showsSubtitleDialog,
                model.showsSubtitleSearchDialog,
            ].filter { $0 }

            XCTAssertLessThanOrEqual(shown.count, 1, "\(dialog) opened more than one dialog")
        }
    }

    func testSeekingHidesTheControlsAndShowsTheStrip() {
        // Not a rule this file owns — it is read off the machine, and asserted
        // here only because a view that showed both would cover the filmstrip
        // with the bar a viewer is trying to see past.
        let machine = controller()
        _ = machine.onKey(key: TvRemote.key(for: .left))

        let model = TvChromeViewModel(ui: machine.ui.value)

        XCTAssertTrue(model.showsSeekStrip)
        XCTAssertFalse(model.showsControls)
    }

    func testBackLeavesSeekingBeforeItLeavesThePlayer() {
        // A single back press that exited the player from inside the scrub bar
        // would throw away the position a viewer was hunting for.
        let callbacks = RecordingTvCallbacks()
        let machine = controller(callbacks)
        _ = machine.onKey(key: TvRemote.key(for: .left))

        _ = machine.onBack()

        XCTAssertFalse(machine.ui.value.seekMode)
        XCTAssertFalse(callbacks.calls.contains("exitPlayer"))
    }
}
