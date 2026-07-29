// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.ui.tv.TvChromeStrings

/**
 * A plugin that draws the player.
 *
 * The contract, separate from the plugin, because a consumer replacing the
 * whole chrome implements this and registers their own — which is the point of
 * the UI being a plugin at all. `player.getPlugin(...)` then hands back
 * something the host can call `handleBack()` on without knowing whose UI it is.
 */
public interface PlayerUiPlugin {

    @Composable
    public fun Content(player: NMVideoPlayer, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null)

    /** The screen a television shows before playback: resume, restart, subtitles. */
    public fun showPreScreen()

    public fun hidePreScreen()

    public fun isPreScreenVisible(): Boolean

    /**
     * Take the back press, if the UI has something layered to close.
     *
     * True means handled and the host must NOT navigate. A menu that closed on
     * back AND popped the screen behind it is the bug this returns a boolean to
     * prevent, and a host cannot know whether a dialog is open.
     */
    public fun handleBack(): Boolean
}

/**
 * Which of the two players this is.
 *
 * There are two in the NoMercy client and there always were: the full player
 * over a film, and a compact one over a trailer on a detail page. They are the
 * same chrome with different answers to three questions, which is why the app
 * ships them as `MobileUiPlugin` and `TrailerMobileUiPlugin` rather than one
 * class with a flag threaded through it.
 *
 * A kind rather than a second plugin id because the web has exactly one chrome
 * plugin and the port must expose the same surface — a native-only
 * `trailer-ui` would be a plugin no web consumer has ever seen, which is the
 * renamed-plugin failure in the other direction.
 */
public enum class VideoUiKind {
    /** Everything, over a film or an episode. */
    Full,

    /**
     * A trailer on a detail page.
     *
     * Subtitles and nothing else. A trailer has one audio track, one rendition
     * of interest, no episodes and no chapters, so the menus those rows open
     * would each be a press onto an empty list. It has nowhere to cast to
     * either — casting a trailer to a television is not a thing anybody wants.
     */
    Trailer,
}

public data class VideoUiOptions(
    val formFactor: FormFactor,
    val kind: VideoUiKind = VideoUiKind.Full,
    val buttons: ChromeButtons = ChromeButtons.forKind(kind),
    val strings: TvChromeStrings = TvChromeStrings(),

    // The rest of DesktopUiOptions, which this had four of.
    //
    // The web plugin takes fourteen, and the missing ten were not obscure: his
    // own site passes buttonPriority, portraitHidden and buttonOrder, so the
    // configuration the NoMercy player actually ships with could not be
    // expressed through the plugin at all. A consumer moving the same line
    // across got a chrome that ignored most of it — silently, because an
    // unknown option in Kotlin is a compile error only if the field exists.

    /** `inactivityMs`. Four seconds is the web's; his mobile player uses three. */
    val inactivityMs: Long = DEFAULT_INACTIVITY_MS,

    /**
     * `buttonPriority` — the order controls are dropped in as the bar narrows.
     *
     * His site raises `chapterNext` and `next` above the menus, because skipping
     * an intro or jumping an episode one-handed is what a phone viewer opens the
     * controls for. The library had the walk and no way to reorder it.
     */
    val buttonPriority: List<ChromeControl> = CHROME_PRIORITY,

    /** `portraitHidden` — dropped at any width in portrait. His site replaces it. */
    val portraitHidden: Set<ChromeControl> = CHROME_PORTRAIT_HIDDEN,

    /** `hideTitle`. A player embedded under its own heading does not repeat it. */
    val hideTitle: Boolean = false,

    /**
     * `disableClickToPause`.
     *
     * A player inside a link, or one whose surface is a hit target for something
     * else, must not toggle playback on every press.
     */
    val disableClickToPause: Boolean = false,
)

/**
 * The player's UI, as a plugin.
 *
 * The same `desktop-ui` id the web plugin has, so a consumer moving code across
 * writes the same line:
 *
 *     player.addPlugin(VideoUiPlugin(VideoUiOptions(formFactor = FormFactor.Phone)))
 *
 * This shape is not decoration and it is not new. The web ships its chrome as
 * `desktop-ui`, a plugin; the NoMercy Android client ships its chrome as
 * `MobileUiPlugin` and `TvUiPlugin` over a `UiComposablePlugin` contract, also
 * plugins. This library shipped it as a bare `VideoChrome` composable — nothing
 * registered, nothing reachable through `getPlugin`, no owner for the pre-screen
 * or the back press.
 *
 * That gap is why the parity gate could excuse `desktop-ui` as "the chrome
 * itself, not a plugin" and report a full surface while the most visible part
 * of the port went unmeasured. A consumer's `player.getPlugin("desktop-ui")`
 * returned nothing, which is the same failure as a renamed plugin and harder to
 * see.
 *
 * [VideoChrome] still exists and still draws everything. This owns it, gives it
 * an id, and puts it where a consumer expects to find it.
 */
public open class VideoUiPlugin(
    private val opts: VideoUiOptions,
) : Plugin<VideoUiOptions>(), PlayerUiPlugin {

    public companion object Manifest : PluginManifest {
        override val id: String = "desktop-ui"

        // Two, matching the web plugin this mirrors.
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    override val options: VideoUiOptions get() = opts

    private val preScreen: MutableState<Boolean> = mutableStateOf(false)

    // Set by the chrome while it has something layered open, so back closes
    // that rather than leaving the player. Null when nothing is open.
    private var backHandler: (() -> Boolean)? = null

    @Composable
    override fun Content(player: NMVideoPlayer, modifier: Modifier, onBack: (() -> Unit)?) {
        val resolved: VideoUiOptions = resolvedOptions ?: opts

        VideoChrome(
            player = player,
            formFactor = resolved.formFactor,
            modifier = modifier,
            strings = resolved.strings,
            buttons = resolved.buttons,
            inactivityMs = resolved.inactivityMs,
            layout = ChromeLayout(
                priority = resolved.buttonPriority,
                portraitHidden = resolved.portraitHidden,
                hideTitle = resolved.hideTitle,
            ),
            onClose = onBack,
        )
    }

    /**
     * No-op for a trailer, as `TrailerMobileUiPlugin.showPreScreen` is.
     *
     * A pre-screen is the poster-and-play-button a full player shows before it
     * starts. A trailer is already inside a page that showed the poster, and one
     * over it would be the same picture twice with a second button to press.
     */
    override fun showPreScreen() {
        if (opts.kind == VideoUiKind.Trailer) return

        preScreen.value = true
    }

    override fun hidePreScreen() {
        preScreen.value = false
    }

    override fun isPreScreenVisible(): Boolean = preScreen.value

    override fun handleBack(): Boolean = backHandler?.invoke() ?: false

    /**
     * What back should do while something is layered over the player.
     *
     * Registered by whatever opened it rather than guessed here, because this
     * plugin cannot see which menu is up and a host cannot see any of it.
     */
    public fun onBackPressed(handler: (() -> Boolean)?) {
        backHandler = handler
    }
}
