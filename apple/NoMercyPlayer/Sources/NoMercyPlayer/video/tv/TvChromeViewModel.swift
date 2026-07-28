// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import NoMercyVideoPlayer

/// The machine's answer, as SwiftUI needs to ask it.
///
/// A projection and nothing else: every value here is read from one `TvChromeUi`
/// rather than accumulated, so an impossible combination cannot be produced on
/// this side of the boundary. Six independent flags is exactly the shape the
/// machine was written to replace.
public struct TvChromeViewModel: Sendable {

    private let ui: TvChromeUi

    public init(ui: TvChromeUi) {
        self.ui = ui
    }

    /// The bars. Hidden while seeking, because the filmstrip is the thing a
    /// viewer is looking past them at.
    public var showsControls: Bool { ui.controlsVisible && !ui.seekMode }

    public var showsSeekStrip: Bool { ui.seekMode }

    public var showsPreScreen: Bool { ui.preScreenVisible }

    public var showsVolumeIndicator: Bool { ui.volumeIndicatorVisible }

    public var topBarHasFocus: Bool { ui.topBarHasFocus }

    // One dialog is open, or none is. Asked separately rather than exposed as a
    // raw enum so a view cannot forget a case, and derived from the same value
    // so it cannot answer yes twice.
    public var showsEpisodesDialog: Bool { ui.dialog == .episodes }

    public var showsLanguageDialog: Bool { ui.dialog == .language }

    public var showsSubtitleDialog: Bool { ui.dialog == .subtitle }

    public var showsSubtitleSearchDialog: Bool { ui.dialog == .subtitleSearch }

    public var showsAnyDialog: Bool {
        showsEpisodesDialog || showsLanguageDialog || showsSubtitleDialog || showsSubtitleSearchDialog
    }
}
