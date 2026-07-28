// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.menu

/**
 * How a menu moves under the arrow keys, and what closing one means.
 *
 * From `desktop-ui/helpers/menuControl.ts`. Four rules, and three of them are
 * the kind that get lost in a port because they are absences rather than
 * features:
 *
 * 1. **Movement wraps.** Down from the last row reaches the first. A menu that
 *    stops at the end makes a viewer press a key that does nothing, which reads
 *    as the menu having frozen.
 * 2. **Only the open pane moves.** When a sub-menu is open the rows are that
 *    pane's, not the whole frame's — otherwise arrowing through the subtitle
 *    list walks out into the settings rows behind it.
 * 3. **Hidden and disabled rows are skipped**, and so are rows not currently
 *    laid out. A menu that focuses an invisible row loses the highlight
 *    entirely.
 * 4. **The player's own keys do not fire while a menu is open.** The web stops
 *    propagation; here the caller checks [menuSwallowsKeys]. Without it,
 *    arrowing down a subtitle list also seeks and changes the volume.
 */

/**
 * The row an arrow key moves to, wrapping at both ends.
 *
 * [current] is -1 when nothing is focused yet. Down then lands on the first
 * row, which is what anyone expects; Up lands on the SECOND-TO-LAST, which
 * nobody does. That falls out of the web's own arithmetic and is kept: a viewer
 * pressing Up into a fresh menu should land in the same place on both, and
 * "fixing" it here would be a divergence wearing the clothes of a correction.
 */
public fun nextMenuRow(current: Int, rowCount: Int, down: Boolean): Int? {
    if (rowCount <= 0) return null

    return if (down) {
        (current + 1) % rowCount
    } else {
        (current - 1 + rowCount) % rowCount
    }
}

/**
 * The rows an arrow key may reach: enabled, not hidden, and laid out.
 *
 * The third is the web's `offsetParent !== null`, which catches a row inside a
 * collapsed pane. Focusing one of those loses the highlight with nothing on
 * screen to show where it went.
 */
public fun <T> navigableRows(rows: List<T>, reachable: (T) -> Boolean): List<T> =
    rows.filter(reachable)

/**
 * Whether the player's own key handling should stand down.
 *
 * True whenever a menu is open. The web achieves it with
 * `stopPropagation`; a native chrome has no propagation to stop, so it asks.
 */
public fun menuSwallowsKeys(menuOpen: Boolean): Boolean = menuOpen

/**
 * What closing leaves behind: no open menu, no open sub-menu, and every
 * trigger collapsed.
 *
 * A single value rather than three assignments, because the web's
 * `closeAllMenus` clears all of them together and a port that forgot one leaves
 * a trigger reading as expanded over a menu that is gone.
 */
public data class MenuOpenState(
    val open: Boolean = false,
    val subMenu: String? = null,
    val expandedTrigger: String? = null,
) {
    /** Every field cleared at once, which is what `closeAllMenus` does. */
    public fun closed(): MenuOpenState = MenuOpenState()

    /** Opening the main menu closes any sub-menu that was showing. */
    public fun openedMain(trigger: String? = null): MenuOpenState =
        MenuOpenState(open = true, subMenu = null, expandedTrigger = trigger)

    public fun openedSub(name: String, trigger: String? = null): MenuOpenState =
        MenuOpenState(open = true, subMenu = name, expandedTrigger = trigger)
}
