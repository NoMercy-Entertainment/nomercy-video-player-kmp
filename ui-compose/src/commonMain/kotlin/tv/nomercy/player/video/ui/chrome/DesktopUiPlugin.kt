// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest

// The desktop chrome, as a plugin.
//
// It already existed and already worked — as composables. That is the right way
// to declare UI and it is not the question this answers. The question is that
// every other plugin can be enabled, disabled, asked what it is and handed to
// `player.getPlugin(...)`, and the chrome could not: it had no class, so
// `enable()`, `disable()`, `overlay()`, `holdChrome()` and `releaseChrome()` had
// nowhere to live and a consumer had no handle on the chrome at all.
//
// The composables stay exactly where they are. This is the handle to them.
//
// The reference's `overlay()` returns the HTMLElement other plugins mount their
// UI into, so their elements inherit the chrome's show/hide lifecycle instead of
// floating over a hidden bar. There is no element to return here, and the honest
// counterpart of "mount your UI into the layer I own" in a declarative toolkit
// is "give me the composable and I will draw it inside that layer" — same
// guarantee, same lifecycle, expressed the way the toolkit expresses things.
public class DesktopUiPlugin : Plugin<Unit>() {

    override val manifest: PluginManifest = Manifest
    override val options: Unit = Unit

    public companion object Manifest : PluginManifest {
        override val id: String = "video/desktop-ui"
        override val version: String = "0.1.0"
    }

    // Set when the chrome composes and cleared when it leaves. Null before
    // then, exactly as the reference returns null before use() has built its
    // DOM — a consumer asking early gets an honest "not yet" rather than a
    // handle to something that does not exist.
    internal var controller: ChromeController? = null

    private val overlays: SnapshotStateList<@Composable () -> Unit> = mutableStateListOf()

    /**
     * The chrome's own overlay layer, as the things drawn in it.
     *
     * Anything added here is drawn inside the auto-hiding layer and follows its
     * lifecycle, which is the whole point of the reference handing out its
     * overlay root: a panel that draws itself over the video instead would stay
     * on screen after the bars had gone.
     */
    public fun overlay(): List<@Composable () -> Unit> = overlays.toList()

    /** Draw [content] inside the chrome's overlay layer. */
    public fun addOverlay(content: @Composable () -> Unit) {
        overlays += content
    }

    public fun removeOverlay(content: @Composable () -> Unit) {
        overlays -= content
    }

    /**
     * Pin the chrome visible while an overlay of your own is open — a cast
     * panel or device picker anchored to the bars, which must not let them
     * auto-hide underneath it.
     *
     * Balance each call with exactly one [releaseChrome].
     */
    public fun holdChrome() {
        controller?.holdChrome()
    }

    /**
     * Release one [holdChrome]. The auto-hide countdown resumes only once the
     * last hold is gone.
     */
    /**
     * Bring the controls up now, as a pointer moving would.
     *
     * [holdChrome] pins them against the auto-hide and does NOT show them: a
     * consumer that called it on a chrome already faded out got a hold over
     * nothing and no controls, which is a handle answering politely and doing
     * nothing — the exact failure the controller binding exists to prevent.
     */
    public fun showChrome() {
        controller?.bumpActivity()
    }

    /** Take them away now, subject to any hold still outstanding. */
    public fun hideChrome() {
        controller?.maybeHide()
    }

    public fun releaseChrome() {
        controller?.releaseChrome()
    }

    override fun dispose() {
        overlays.clear()
        controller = null
        super.dispose()
    }
}
