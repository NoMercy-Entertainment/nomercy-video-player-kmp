// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI
import NoMercyVideoPlayer

#if os(tvOS)

/// The television chrome on an Apple TV.
///
/// Every decision here is made by the shared controller. tvOS disagrees with
/// Android about how a view is built and agrees completely about what a back
/// press should do, so this file is layout and focus and nothing else: what the
/// arrows mean, when the controls hide, and where back goes are all decided in
/// Kotlin and tested there.
///
/// The Siri Remote is the reason the split matters. Its touch surface reports
/// as directional input rather than as touch, so the same state machine that
/// drives a directional pad drives this without knowing which it is talking to.
public struct TvChromeView<Surface: View>: View {

    @ObservedObject private var model: TvChromeModel
    private let surface: Surface

    public init(model: TvChromeModel, @ViewBuilder surface: () -> Surface) {
        self.model = model
        self.surface = surface()
    }

    public var body: some View {
        ZStack {
            surface

            if model.preScreenVisible {
                TvPreScreenView(model: model)
            } else if model.seekMode {
                TvSeekView(model: model)
            } else if model.controlsVisible {
                TvControlsView(model: model)
            }
        }
        // The system's own safe area, because tvOS both knows what the panel
        // crops and builds the allowance into its layout margins. Matching it is
        // what makes the player look like it belongs on the same screen as
        // everything else rather than merely being inside the visible part.
        .ignoresSafeArea(.all, edges: [])
        // Directions rather than gestures. The remote's touch surface reports as
        // directional input, and reading it as touch is how a television ends up
        // with controls that can only be hit by aiming.
        .onMoveCommand { direction in
            model.move(direction)
        }
        .onExitCommand {
            model.back()
        }
        .onPlayPauseCommand {
            model.togglePlay()
        }
    }
}

/// What the controls look like while a film is playing.
private struct TvControlsView: View {

    @ObservedObject var model: TvChromeModel

    var body: some View {
        VStack {
            HStack {
                VStack(alignment: .leading) {
                    Text(model.title).font(.title2)
                    if !model.subtitle.isEmpty {
                        Text(model.subtitle).font(.body).foregroundColor(.secondary)
                    }
                }
                Spacer()
            }

            Spacer()

            VStack {
                ProgressView(value: model.progress)
                HStack {
                    Text(model.elapsed)
                    Spacer()
                    Button(model.isPlaying ? "Pause" : "Play") { model.togglePlay() }
                    Spacer()
                    Text(model.remaining)
                }
            }
        }
        .padding(60)
    }
}

/// The scrubber, which moves a preview rather than the film.
private struct TvSeekView: View {

    @ObservedObject var model: TvChromeModel

    var body: some View {
        VStack {
            Spacer()
            Text(model.previewLabel)
                .font(.largeTitle)
                .padding()
                .background(Color.black.opacity(0.5))
        }
        .padding(60)
    }
}

/// Where a viewer lands when they press back, and where they start.
private struct TvPreScreenView: View {

    @ObservedObject var model: TvChromeModel

    // Resume is focused because somebody who pressed back by accident wants
    // exactly that, and it should be one press away rather than a hunt.
    @FocusState private var resumeFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(model.title).font(.largeTitle)
            if !model.subtitle.isEmpty {
                Text(model.subtitle).font(.title3).foregroundColor(.secondary)
            }

            Button("Resume") { model.play() }
                .focused($resumeFocused)

            Button("Restart") { model.restart() }

            if model.hasEpisodes {
                Button("Episodes") { model.openEpisodes() }
            }

            Button("Subtitles") { model.openSubtitles() }
        }
        .padding(60)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color.black.opacity(0.9))
        .onAppear { resumeFocused = true }
    }
}

#endif
