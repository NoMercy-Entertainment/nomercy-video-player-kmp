package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The scrolling body of a settings pane.
 *
 * Every pane in this menu is a list of rows inside the same card, so every pane
 * wants the same inset, the same gap between rows and the same ability to
 * scroll when there are more rows than height. Each one used to say so for
 * itself, which meant each one could forget: the auto-skip pane's two rows sat
 * flush against the card's edges, and the subtitle property list — the font
 * faces, the colours — had no padding and no scrolling at all, so it ran off
 * the bottom of the card with the rest unreachable.
 *
 * Rows go through [content] as `item`/`items`, the same as any LazyColumn. A
 * pane that needs something other than a plain list still builds it itself;
 * this is the shape almost all of them are.
 */
@Composable
internal fun MenuPane(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = MENU_LIST_PADDING,
        verticalArrangement = Arrangement.spacedBy(MENU_LIST_GAP),
        content = content,
    )
}
