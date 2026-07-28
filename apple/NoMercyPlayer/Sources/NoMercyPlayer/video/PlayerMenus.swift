// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import SwiftUI

/// Which list is open.
///
/// One value rather than a flag per menu. Two open at once is not a state
/// anybody designed; it is what happens when five booleans are set
/// independently, and it is how a viewer ends up choosing a quality from behind
/// a subtitle list.
public enum PlayerMenu: Equatable, Sendable {
    case main
    case quality
    case audio
    case subtitle
    case speed
}

/// Where the viewer is in the menus, and how they get back.
///
/// A stack rather than a current-page field, because back has to mean "the one
/// before this" and a field can only mean "the main menu". Two levels deep with
/// a field, the back button skips a level.
@MainActor
public final class PlayerMenuState: ObservableObject {

    @Published public private(set) var stack: [PlayerMenu] = []

    public init() {}

    public var current: PlayerMenu? { stack.last }

    public var isOpen: Bool { !stack.isEmpty }

    public func open() {
        stack = [.main]
    }

    public func push(_ menu: PlayerMenu) {
        stack.append(menu)
    }

    /// Back rather than close. Popping the last entry closes it, which is what
    /// makes the system back gesture and the button do the same thing.
    public func back() {
        guard !stack.isEmpty else { return }
        stack.removeLast()
    }

    public func close() {
        stack = []
    }
}

/// What the menus offer, and what each choice does.
///
/// Separate from the view for the same reason the gesture grid is: a list whose
/// rows are computed in a body can only be tested by looking at it, and every
/// interesting failure here is a row selecting the wrong thing.
@MainActor
public struct PlayerMenuActions<Player: VideoChromePlayer> {

    private let player: Player
    private let state: PlayerMenuState

    public init(player: Player, state: PlayerMenuState) {
        self.player = player
        self.state = state
    }

    /// Only the lists with something to choose from. A row that opens onto one
    /// option is a press that costs a viewer time and gives them no choice.
    public var offered: [PlayerMenu] {
        var menus: [PlayerMenu] = []
        if player.levels.count > 1 { menus.append(.quality) }
        if player.audioOptions.count > 1 { menus.append(.audio) }
        // Subtitles are offered whenever the chrome is: turning them off is a
        // choice, and so is finding out there are none.
        menus.append(.subtitle)
        menus.append(.speed)
        return menus
    }

    public func selectQuality(_ option: QualityOption?) {
        player.selectQuality(option)
        state.close()
    }

    public func selectAudio(_ option: TrackOption) {
        player.selectAudio(option)
        state.close()
    }

    /// Nil is off, which arrives here as a row like any other rather than as an
    /// absence — a viewer who turned subtitles on has to be able to turn them
    /// back off.
    public func selectSubtitle(_ option: TrackOption?) {
        player.selectSubtitle(option)
        state.close()
    }

    /// Whether a row should be drawn as the current one. By identifier, because
    /// a track list changes when a stream switches rendition and a position
    /// would tick the row that moved into the slot.
    public func isCurrentAudio(_ option: TrackOption) -> Bool {
        player.selectedAudioID == option.id
    }

    public func isCurrentSubtitle(_ option: TrackOption?) -> Bool {
        player.selectedSubtitleID == option?.id
    }

    public func isCurrentQuality(_ option: QualityOption?) -> Bool {
        player.selectedQuality?.id == option?.id
    }
}
