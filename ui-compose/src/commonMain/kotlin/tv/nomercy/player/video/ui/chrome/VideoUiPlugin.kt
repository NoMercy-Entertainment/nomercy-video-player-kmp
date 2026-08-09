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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.core.plugin.PluginOptionField
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

    /**
     * `buttonOrder` — the visual override, distinct from [buttonPriority].
     *
     * Controls named here move to the END of the row in this sequence; anything
     * unnamed stays where the web's builder put it. Priority decides what is
     * DROPPED as the row narrows and this decides what sits where, which the web
     * keeps apart and a port that conflated them would get wrong in both
     * directions.
     *
     * His site passes one, and until now it could not be expressed: the option
     * was named in a comment on this class and was not a field, so a consumer
     * moving the same configuration across silently got the default order.
     */
    val buttonOrder: List<ChromeControl> = emptyList(),

    /** `hideTitle`. A player embedded under its own heading does not repeat it. */
    val hideTitle: Boolean = false,

    /**
     * `volumeSlider`. Auto, a horizontal track, or a vertical popup.
     *
     * Auto by the web's reasoning: expand-on-hover needs a real pointer, so it
     * cannot be what a touch device is given.
     */
    val volumeSlider: VolumeSliderMode = VolumeSliderMode.Auto,

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
    opts: VideoUiOptions,
) : Plugin<VideoUiOptions>(), PlayerUiPlugin {

    public companion object Manifest : PluginManifest {
        override val id: String = "desktop-ui"

        // Two, matching the web plugin this mirrors.
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    // Held rather than fixed: every control below is one a testbed turns off to
    // see what the chrome does without it, which only means anything if the
    // chrome reads the change.
    private var opts: VideoUiOptions = opts

    override val options: VideoUiOptions get() = opts

    // Every button the chrome can draw, as a toggle.
    //
    // The form factor, the strings and the button ORDER are not here: they are
    // structure rather than values, and a generated control cannot express
    // "this list, in this order" without inventing an editor for it. What a
    // testbed actually wants is to turn one control off and watch the bar
    // reflow, which is exactly what these are.
    //
    // Built from a table rather than written out seventeen times, so adding a
    // button to the chrome is one row here and not a block that gets forgotten.
    override fun optionFields(): List<PluginOptionField> = BUTTON_FIELDS.map { field ->
        PluginOptionField.Toggle(
            key = "buttons.${field.key}",
            label = field.label,
            value = field.read(opts.buttons),
            apply = { on -> opts = opts.copy(buttons = field.write(opts.buttons, on)) },
        )
    }

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
                volumeSlider = resolved.volumeSlider,
                buttonOrder = resolved.buttonOrder,
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

    // ── The chrome layer itself ──────────────────────────────────────────────
    //
    // These used to be a SECOND plugin, DesktopUiPlugin, under the id
    // "video/desktop-ui" — a namespaced id no web plugin has, on a class nothing
    // ever registered. HoldChromePlugin looked it up by that id, got null, and
    // bound the live controller to nothing, so every one of these methods was a
    // handle that answered politely and did nothing. The web has one desktop-ui
    // plugin carrying all of it, and now so does this.

    // Set when the chrome composes and cleared when it leaves. Null before then,
    // exactly as the reference returns null before use() has built its DOM — a
    // consumer asking early gets an honest "not yet" rather than a handle to
    // something that does not exist.
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
     * Pin the chrome visible while an overlay of your own is open — a cast panel
     * or device picker anchored to the bars, which must not let them auto-hide
     * underneath it.
     *
     * Balance each call with exactly one [releaseChrome].
     */
    public fun holdChrome() {
        controller?.holdChrome()
    }

    /** Release one [holdChrome]. The countdown resumes once the last hold goes. */
    public fun releaseChrome() {
        controller?.releaseChrome()
    }

    /**
     * Bring the controls up now, as a pointer moving would.
     *
     * [holdChrome] pins them against the auto-hide and does NOT show them: a
     * consumer that called it on a chrome already faded out got a hold over
     * nothing and no controls.
     */
    public fun showChrome() {
        controller?.bumpActivity()
    }

    /** Take them away now, subject to any hold still outstanding. */
    public fun hideChrome() {
        controller?.maybeHide()
    }

    override fun dispose() {
        overlays.clear()
        controller = null
        super.dispose()
    }
}

// One row per button the chrome draws. The reader and the writer are explicit
// because a data class copy cannot be addressed by name without reflection.
private class ButtonField(
    val key: String,
    val label: String,
    val read: (ChromeButtons) -> Boolean,
    val write: (ChromeButtons, Boolean) -> ChromeButtons,
)

private val BUTTON_FIELDS: List<ButtonField> = listOf(
    ButtonField("playPause", "Play / pause", { it.playPause }, { b, on -> b.copy(playPause = on) }),
    ButtonField(
        "previousNext",
        "Previous and next",
        { it.previousNext },
        { b, on -> b.copy(previousNext = on) },
    ),
    ButtonField("volume", "Volume", { it.volume }, { b, on -> b.copy(volume = on) }),
    ButtonField("time", "Time", { it.time }, { b, on -> b.copy(time = on) }),
    ButtonField("chapters", "Chapters", { it.chapters }, { b, on -> b.copy(chapters = on) }),
    ButtonField("fullscreen", "Fullscreen", { it.fullscreen }, { b, on -> b.copy(fullscreen = on) }),
    ButtonField("settings", "Settings", { it.settings }, { b, on -> b.copy(settings = on) }),
    ButtonField("seekBack", "Seek back", { it.seekBack }, { b, on -> b.copy(seekBack = on) }),
    ButtonField("seekForward", "Seek forward", { it.seekForward }, { b, on -> b.copy(seekForward = on) }),
    ButtonField("subtitles", "Subtitles", { it.subtitles }, { b, on -> b.copy(subtitles = on) }),
    ButtonField("audio", "Audio tracks", { it.audio }, { b, on -> b.copy(audio = on) }),
    ButtonField("quality", "Quality", { it.quality }, { b, on -> b.copy(quality = on) }),
    ButtonField("speed", "Playback speed", { it.speed }, { b, on -> b.copy(speed = on) }),
    ButtonField("aspectRatio", "Aspect ratio", { it.aspectRatio }, { b, on -> b.copy(aspectRatio = on) }),
    ButtonField("playlist", "Playlist", { it.playlist }, { b, on -> b.copy(playlist = on) }),
    ButtonField("theater", "Theater", { it.theater }, { b, on -> b.copy(theater = on) }),
    ButtonField(
        "pictureInPicture",
        "Picture in picture",
        { it.pictureInPicture },
        { b, on -> b.copy(pictureInPicture = on) },
    ),
)
