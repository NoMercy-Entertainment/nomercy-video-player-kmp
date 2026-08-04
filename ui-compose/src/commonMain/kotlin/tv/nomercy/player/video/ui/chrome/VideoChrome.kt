// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.CoroutineScope
import tv.nomercy.player.core.cues.SpriteCue
import androidx.compose.ui.platform.LocalDensity
import tv.nomercy.player.video.ui.thumbnails.PreviewSprite
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.errors.Severity
import tv.nomercy.player.core.events.SubtitleStyle
import tv.nomercy.player.core.events.Subscription
import tv.nomercy.player.video.subtitles.CueBox
import tv.nomercy.player.video.subtitles.SubtitleOverlayPlugin
import tv.nomercy.player.core.input.KeyCombo
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.VideoEvents
import tv.nomercy.player.video.input.VideoKeyHandlerPlugin
import tv.nomercy.player.video.input.playerCommandsOf
import tv.nomercy.player.video.ui.chrome.menus.MenuState
import tv.nomercy.player.video.ui.chrome.menus.MenuStrings
import tv.nomercy.player.video.ui.chrome.menus.SettingsMenu
import tv.nomercy.player.video.tv.Scheduler
import tv.nomercy.player.video.tv.TvChapter
import tv.nomercy.player.video.ui.tv.TvChromeStrings

// The whole chrome for a screen somebody touches or points at.
//
// One controller, one read model, one set of commands, and every widget given a
// slice of them. That is the shape the client this replaces did not have: there
// each control read the store directly and decided for itself whether it should
// be visible, which is how a menu closing hid the scrubber somebody was in the
// middle of dragging.
//
// Touch and desktop are the same assembly with different input attached. They
// draw the same bars in the same places and disagree about two things — a finger
// has tap zones and no pointer, a pointer has a keyboard and no zones — so a
// second composable for the second one would be the same layout written twice.
@Composable
public fun VideoChrome(
    player: NMVideoPlayer,
    formFactor: FormFactor,
    modifier: Modifier = Modifier,
    strings: TvChromeStrings = rememberChromeStrings(),
    buttons: ChromeButtons = ChromeButtons(),
    sprite: List<SpriteCue> = emptyList(),
    /**
     * The decoded sheet, when the host has one.
     *
     * Separate from [sprite] because the two answer different questions: the cue
     * table says a frame exists, and this one can draw it. loadPreviewSprite
     * builds it and had no caller, so the preview bubble showed a clock over an
     * empty box while the same drag in a browser showed the scene.
     */
    previewSprite: PreviewSprite? = null,
    onClose: (() -> Unit)? = null,
    // The web's other two top-bar events. Back and close are different exits —
    // one returns to where the viewer came from and one dismisses the player —
    // and a library that offered only the second would leave every consumer
    // rebuilding the first through the trailing slot.
    onBack: (() -> Unit)? = null,
    onCast: (() -> Unit)? = null,
    /**
     * The edge gestures his grid carries: brightness down the left, volume down
     * the right. Null leaves those rows unbound, which is what a build that
     * cannot reach the platform's brightness should do rather than drawing a
     * zone that swallows taps and does nothing.
     */
    gestures: ChromeGestures? = null,
    /**
     * Whether openings and endings are skipped or offered. The app owns the
     * setting; this is what it currently reads and where a change goes back.
     */
    autoSkip: AutoSkipPreference = AutoSkipPreference(),
    /**
     * Whether the right-hand clock reads what is left or how long the item is.
     * The app owns the setting, the same way it owns [autoSkip].
     */
    clock: ClockPreference = ClockPreference(),
    /**
     * How long the controls stay up with nothing happening.
     *
     * Four seconds is the web's own `inactivityMs`, and it is a parameter there
     * too. His mobile player uses three — a difference the controller could
     * express the whole time and no host could reach, because this assembled the
     * controller itself and never passed it on.
     */
    inactivityMs: Long = DEFAULT_INACTIVITY_MS,
    /** How the bar arranges itself: priority, portrait-hidden, and the title. */
    layout: ChromeLayout = ChromeLayout(),
    slots: ChromeSlots = LocalChromeSlots.current,
    surface: @Composable () -> Unit = {},
) {
    val scheduler: Scheduler = rememberChromeScheduler()

    var menu: MenuState by remember { mutableStateOf(MenuState.Hidden) }
    val panel: ShortcutsPanel = rememberShortcutsPanel()
    val message: String? = rememberChromeMessage(player, strings)?.text
    val error: ChromeError? = rememberPlayerError(player)
    // `menu` rides along so the bar's trigger buttons can carry the web's
    // aria-expanded state — see MenuTriggers.kt.
    val state: ChromeState = rememberChromeState(player, message, error)
        .copy(autoSkipChapters = autoSkip.enabled, showRemaining = clock.showRemaining, menu = menu)

    val controller: ChromeController = rememberChromeController(player, scheduler, inactivityMs)
    val commands: ChromeCommands = rememberVideoCommands(player, autoSkip, clock) { menu = it }

    ChromeBindings(controller, state.playing, menu)

    AutoSkipBinding(state, commands)

    val input = ChromeInput(
        rememberVideoKeys(player, formFactor),
        controller,
        TouchZoneInput(state, commands, player::now),
        gestures,
        panel,
    )

    // The picture and the words on it, as one layer.
    //
    // Together rather than as two arguments because they are one thing to a
    // viewer and because they have to stay in this order: the surface, then the
    // cues, then everything else. A chrome that took them separately would let
    // a caller supply a surface and no cues, which is the state this was in.
    val picture: @Composable BoxScope.() -> Unit = {
        surface()
        SubtitleCueLayer(rememberCueBoxes(player), rememberSubtitleStyle(player))
        slots.styledSubtitles?.invoke(state, commands)
    }

    ChromeFrame(input = input, modifier = modifier, picture = picture) {
        ChromeMenuScope(keyboard = input.pointerDriven) {
            ChromeLayers(
                scene = ChromeScene(
                    state, commands, controller, strings, rememberMenuStrings(), buttons, layout,
                ),
                host = ChromeHost(sprite, previewSprite, onClose, onBack, onCast, slots),
                menu = menu,
                onMenuChange = { menu = it },
            )

            ShortcutsLayer(panel)
        }
    }
}

// rememberVideoCommands lives in VideoChromeCommands.kt beside the class it
// builds; ChromeMenuScope in MenuTriggers.kt beside the locals it provides.

// The auto-hide state machine, kept across recompositions.
//
// Its own function so the assembly above reads as a list of the pieces it needs
// rather than as one of them spelled out and the rest named. Rebuilt only when the
// player, the clock or the timeout changes: rebuilding it on any other
// recomposition would reset the countdown and leave the controls up for ever.
@Composable
private fun rememberChromeController(
    player: NMVideoPlayer,
    scheduler: Scheduler,
    inactivityMs: Long,
): ChromeController = remember(player, scheduler, inactivityMs) {
    ChromeController(
        isPlaying = { player.playState() == PlayState.PLAYING },
        scheduler = scheduler,
        inactivityMs = inactivityMs,
    )
}

/**
 * How the bar arranges itself, as one value.
 *
 * `buttonPriority`, `portraitHidden` and `hideTitle` on DesktopUiOptions. They
 * travel together through four layers, and passed one at a time they pushed both
 * VideoChrome and its scene past what a function is allowed to take — which is
 * the threshold being right: they are one decision about layout.
 */
public data class ChromeLayout(
    val priority: List<ChromeControl> = CHROME_PRIORITY,
    val portraitHidden: Set<ChromeControl> = CHROME_PORTRAIT_HIDDEN,
    val hideTitle: Boolean = false,
    /**
     * `volumeSlider` on DesktopUiOptions: 'auto', 'horizontal' or 'vertical'.
     *
     * Auto by the web's reasoning — expand-on-hover needs a real pointer, so it
     * cannot be what a touch device is given.
     */
    val volumeSlider: VolumeSliderMode = VolumeSliderMode.Auto,
    /**
     * `buttonOrder` on DesktopUiOptions: controls named here are re-anchored to
     * the end of the row in this sequence, and anything unnamed keeps its
     * natural position.
     *
     * Empty by default, which is the web's own default — an absent buttonOrder
     * leaves the bar in the order the builder made it.
     */
    val buttonOrder: List<ChromeControl> = emptyList(),
)

// The root, and whichever input this form factor has.
//
// The zones and the pointer are mutually exclusive on purpose. A lifted finger
// reports an exit exactly like a pointer leaving the window, so wiring both
// would hide the controls every time somebody tapped to show them.
@Composable
private fun ChromeFrame(
    input: ChromeInput,
    modifier: Modifier,
    picture: @Composable BoxScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    var bounds: Rect by remember { mutableStateOf(Rect.Zero) }

    CompositionLocalProvider(LocalPlayerBounds provides bounds) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .testTag(if (input.pointerDriven) DESKTOP_CHROME_TAG else TOUCH_CHROME_TAG)
                // Keys before focus, deliberately. A handler placed after the focus
                // target sits inside it and never sees the press that reached it.
                .then(input.keyModifier())
                .then(input.pointerModifier()),
        ) {
            // The surface and the cues on it, under everything else — which is
            // where `.subtitle-overlay` sits at `z-index: 0`. Above the tap zones
            // would put a cue in front of the control a finger is reaching for;
            // inside the visibility gate below would fade the dialogue with the bar.
            picture()

            if (!input.pointerDriven) {
                TouchZonesOverlay(
                    state = input.zones.state,
                    controller = input.controller,
                    commands = input.zones.commands,
                    nowMs = input.zones.nowMs,
                    modifier = Modifier.fillMaxSize(),
                )

                // Above the three columns, and only the rows they do not cover.
                //
                // TouchZonesOverlay is his middle row — skip back, play, skip on —
                // and it stays as it is. What was missing is the other two: his grid
                // dims down the left edge and changes volume down the right, and
                // neither had anywhere to land here.
                //
                // Absent unless a host binds them, because brightness and volume are
                // platform calls this module cannot make. A consumer that binds
                // nothing gets the same four-corner behaviour as before: a tap
                // anywhere wakes the controls.
                input.gestures?.let { gestures ->
                    ChromeGestureGrid(
                        gestures = gestures.copy(
                            // The middle row belongs to the zones below. Binding it
                            // here too would fire both on one double tap.
                            onTogglePlay = null,
                            onSeekBack = null,
                            onSeekForward = null,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                PointerLayer(input)
            }

            content()
        }
    }
}

// How this build is driven.
//
// The key handler being null is the whole question: a build with a keyboard has
// one, a build without has tap zones instead. Asking it once here is what keeps
// three places from each deciding separately what a phone is.

// What the player says, as the widgets read it.
internal data class ChromeScene(
    val state: ChromeState,
    val commands: ChromeCommands,
    val controller: ChromeController,
    val strings: TvChromeStrings,
    val menuStrings: MenuStrings,
    val buttons: ChromeButtons,
    val layout: ChromeLayout,
)

// What the host supplied, which the player knows nothing about: the sprite sheet
// its server generated, and where "out" goes.
internal data class ChromeHost(
    val sprite: List<SpriteCue>,
    /**
     * The same sheet with its pixels reachable, when the host has decoded one.
     *
     * [sprite] is the cue table alone, which is enough to know a frame exists and
     * not enough to draw it. loadPreviewSprite builds this and had no callers, so
     * the whole apparatus sat one parameter away from working: the bubble showed a
     * clock and a chapter name over an empty box while a browser showed the scene.
     */
    val previewSprite: PreviewSprite?,
    val onClose: (() -> Unit)?,
    val onBack: (() -> Unit)?,
    val onCast: (() -> Unit)?,
    val slots: ChromeSlots,
)

@Composable
private fun ChromeLayers(
    scene: ChromeScene,
    host: ChromeHost,
    menu: MenuState,
    onMenuChange: (MenuState) -> Unit,
) {
    val ui: ChromeUi by scene.controller.ui.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        ChromeBackdropLayer(scene, host)

        // Under the bars, not over them.
        //
        // The web closes on a document click outside `.menu-frame`, and a
        // document listener does not consume the click — the button the pointer
        // was actually over still receives it, so pressing a second trigger
        // swaps panes in one press. Drawn last, this full-size box sat on top of
        // the transport bar and ate that press: the open menu closed and the
        // trigger never heard anything, which is one press wasted every time.
        MenuDismissLayer(menu, onMenuChange)

        ChromeStatusText(scene)

        AnimatedVisibility(visible = ui.active, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    host.slots.topBar?.invoke(scene.state, scene.commands) ?: ChromeTopBar(
                        item = scene.state.item,
                        strings = scene.strings,
                        buttons = scene.buttons,
                        exits = ChromeExits(host.onBack, host.onCast, host.onClose),
                        hideTitle = scene.layout.hideTitle,
                        pip = scene.state.pip,
                    )
                }

                ChromeBottom(scene, host, Modifier.align(Alignment.BottomCenter))
            }
        }

        SettingsMenu(
            scene.state,
            scene.commands,
            menu,
            onMenuChange,
            Modifier.align(Alignment.BottomEnd),
            scene.menuStrings,
            scene.buttons,
            // The host's own slots rather than the composition local, because a
            // caller that passed `slots` to this function without providing the
            // local would otherwise get the default and lose the one it supplied.
            host.slots,
        )

        ChromeOverlays(scene)

        // Additive rather than replacing. A skip-intro button and a cast banner
        // are the host's features, and a slot that swallowed the chrome to draw
        // one would be a host choosing between its feature and the controls.
        host.slots.overlays?.invoke(scene.state, scene.commands)
    }
}

// The scrubber sits above the buttons rather than inside the row, because a drag
// target the width of the picture is the one a finger actually hits.
// The bubble over the bar: the clock, the chapter, and the frame at that moment.
//
// Its own composable because the frame lookup is the part that was missing — the
// sprite was loaded, the cue was found, and the answer went into an onPreview
// nothing was listening to.
//
// Faded rather than mounted and unmounted. `.slider-pop` is always in the tree and
// carries `opacity: var(--visibility, 0)` over a `transition: opacity 0.12s ease`,
// so a pointer crossing the bar makes it appear rather than snap.
@Composable
internal fun ScrubBubble(scene: ChromeScene, host: ChromeHost, scrub: Double?, barWidth: Dp) {
    // The last position it was told, held so the fade OUT has somewhere to point.
    // The web's bubble keeps its place while its opacity runs down; recomputing
    // from a null would slide it to the playhead on the way out.
    val held: MutableState<Double> = remember { mutableStateOf(0.0) }
    if (scrub != null) held.value = scrub

    AnimatedVisibility(
        visible = scrub != null,
        enter = fadeIn(tween(POP_FADE_MS, easing = POP_EASE)),
        exit = fadeOut(tween(POP_FADE_MS, easing = POP_EASE)),
    ) {
        val seconds: Double = held.value
        val sprite: PreviewSprite? = host.previewSprite
        val density = LocalDensity.current

        ScrubPreview(
            seconds = seconds,
            fraction = scrubFraction(seconds, scene.state.durationSeconds),
            barWidth = barWidth,
            // Null while the band behind this frame is still being read, which is
            // why the box is sized from what the sheet DECLARES rather than from
            // the pixels: a box that grows when the image lands is a box that
            // jumps under the thumb dragging it.
            frame = sprite?.frameAt(seconds),
            // Converted through the display's density, not relabelled.
            //
            // This read `frameWidthPx.dp`, which takes a sprite sheet's PIXEL
            // dimensions and calls them density-independent. They are equal only
            // at 1.0, so on any scaled display — every phone, most televisions,
            // a Windows desktop at 125% — the preview box was sized by a number
            // that meant something else, and the comment above insists the box
            // is sized from what the sheet DECLARES, which makes the unit the
            // whole contract.
            //
            // Correct as a unit and still the wrong SIZE, which is the half this
            // missed: a sheet's native tile is 320x178, so the bubble came out
            // 113dp on a phone at 2.84 density and 320dp on a desktop at 1.0 —
            // fixed to the sheet's resolution and indifferent to how big the
            // player is. A browser's scales with the bar.
            //
            // A share of the bar, then, keeping the sheet's own aspect. Capped
            // at the native tile so it is never upscaled past the pixels that
            // exist, and floored so it stays readable on a narrow phone.
            frameSize = sprite?.let {
                with(density) {
                    previewFrameSize(
                        barWidth = barWidth,
                        share = PREVIEW_BAR_SHARE,
                        minWidth = PREVIEW_MIN_WIDTH,
                        tile = DpSize(it.frameWidthPx.toDp(), it.frameHeightPx.toDp()),
                    )
                }
            },
            chapterTitle = chapterTitleAt(scene.state, seconds),
        )
    }
}

@Composable
private fun ChromeBottom(scene: ChromeScene, host: ChromeHost, modifier: Modifier) {
    var scrub: Double? by remember { mutableStateOf(null) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val rowWidth: Dp = maxWidth

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bottomScrim()
                // The gesture bar, for the same reason the top bar clears the
                // cutout: the picture uses the whole screen and the controls
                // must stay reachable. Nothing on a desktop.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = BOTTOM_STACK_PADDING),
            verticalArrangement = Arrangement.spacedBy(BOTTOM_STACK_GAP),
        ) {
            ChromeStrip(scene, host, rowWidth) { scrub = it }

            host.slots.transport?.invoke(scene.state, scene.commands) ?: TransportBar(
                state = scene.state,
                commands = scene.commands,
                strings = scene.strings,
                buttons = scene.buttons,
                priority = scene.layout.priority,
                portraitHidden = scene.layout.portraitHidden,
                volumeSlider = scene.layout.volumeSlider,
                buttonOrder = scene.layout.buttonOrder,
            )
        }
    }
}

internal fun scrubFraction(seconds: Double, duration: Double): Float =
    if (duration <= 0.0) 0f else (seconds / duration).coerceIn(0.0, 1.0).toFloat()

// The chapter a position falls in, which is what `.chapter-text` names. The last
// one whose start is at or before the position, so a time inside a chapter reads
// that chapter and not the one after it.
private fun chapterTitleAt(state: ChromeState, seconds: Double): String? =
    state.chapters.lastOrNull { it.startSeconds <= seconds }?.title

// Everything that has to be told when something else changed.
//
// Gathered rather than scattered through the layout, because an effect written
// next to the widget it happens to concern is one that runs whenever that widget
// recomposes.
@Composable
private fun ChromeBindings(controller: ChromeController, playing: Boolean, menu: MenuState) {
    LaunchedEffect(controller, playing) { controller.setPlaying(playing) }

    LaunchedEffect(controller, menu) { controller.setMenuOpen(menu != MenuState.Hidden) }

    DisposableEffect(controller) { onDispose { controller.dispose() } }
}

// Superseded by rememberChromeMessage, which listens to all nine of the web's
// feedback events rather than these two. See ChromeFeedback.kt.

// The last failure the player reported, until an item change clears it.
//
// Held rather than shown-and-forgotten, which is what the message channel does.
// A fatal decode error is not an announcement: it is the reason the screen is
// black, and it has to stay up while the viewer reads it and decides what to do.
// Cleared on the next item, because that is the one event that means somebody is
// trying something new.
@Composable
private fun rememberPlayerError(player: NMVideoPlayer): ChromeError? {
    var error: ChromeError? by remember { mutableStateOf(null) }

    DisposableEffect(player) {
        val failed: Subscription = player.on(CoreEvents.Error) { event ->
            error = ChromeError(
                code = event.code,
                technicalMessage = event.message,
                fatal = event.severity == Severity.FATAL,
            )
        }
        // Cleared when the player moves on, and again when new media is ready.
        //
        // `item` alone was not enough: a consumer that swaps the queue rather
        // than stepping through it never fires one, so Sintel's decode failure
        // stayed on screen over Big Buck Bunny playing underneath it — the
        // metrics were ticking behind a card saying the item could not be
        // played. `mediaReady` is the engine saying it has something, which is
        // exactly when a previous item's failure stops being true.
        val moved: Subscription = player.on(CoreEvents.Item) { error = null }
        val ready: Subscription = player.on(CoreEvents.MediaReady) { error = null }

        onDispose {
            failed.dispose()
            moved.dispose()
            ready.dispose()
        }
    }

    return error
}

// The shipped bindings, for the surfaces that have a keyboard.
//
// The view's rather than the player's. A key handler registered as a plugin
// belongs to the player, and a second chrome on the same player would install a
// second copy of the same table; built here it goes when the screen goes.
@Composable
internal fun rememberVideoKeys(player: NMVideoPlayer, formFactor: FormFactor): VideoKeyHandlerPlugin? {
    if (formFactor != FormFactor.Desktop) return null

    val scope: CoroutineScope = rememberCoroutineScope()

    // Re-resolved when something installs a plugin, because a host registers in
    // an effect and effects run AFTER composition — a lookup done once while
    // composing would always miss the handler it is looking for and always build
    // the private one below.
    val installed: Int = rememberPluginRevision(player)

    return remember(player, scope, installed) {
        // The host's, when there is one.
        //
        // This built its own and called use() on it unconditionally. A host that
        // registers a key handler — which the plugin exists to be — then had TWO:
        // the registry's, which the plugin tree draws and a consumer's toggle
        // switches, and this one, which is what actually saw the key events. So
        // disabling the plugin changed nothing, enabling it did nothing, and the
        // row describing it was describing the wrong object.
        player.plugins.plugins()
            .filterIsInstance<VideoKeyHandlerPlugin>()
            .firstOrNull()
            ?: VideoKeyHandlerPlugin(
                commands = playerCommandsOf(player, scope),
                capabilities = ChromeCapabilities(formFactor),
                nowMs = player::now,
            ).also { it.use() }
    }
}

internal const val TOUCH_CHROME_TAG = "nm-touch-chrome"
internal const val DESKTOP_CHROME_TAG = "nm-desktop-chrome"
// How much of the bar the scrub bubble takes.
//
// A fraction rather than a size, so the preview grows with the player the way
// the browser's does instead of staying at whatever the sprite sheet happened
// to be encoded at.
private const val PREVIEW_BAR_SHARE = 0.22f

// Below this the frame stops being readable, which on a narrow phone is the
// binding constraint rather than the sheet's resolution.
private val PREVIEW_MIN_WIDTH: Dp = 128.dp

internal const val BUFFERING_TAG = "nm-chrome-buffering"
internal const val MESSAGE_TAG = "nm-chrome-message"

// The still behind the picture, gated and scrimmed.
//
// Its own function because the assembly is already at the length limit and this
// is a self-contained decision: whether there is an image, and whether now is a
// moment that needs one.
@Composable
private fun ChromeBackdropLayer(scene: ChromeScene, host: ChromeHost) {
        // Behind the picture, and only until there is one.
    //
    // The slot drew unconditionally before, which is a still sitting over
    // the film for as long as the host supplied one. His trailer plugin
    // gates it on `duration <= 0 || time == 0` and fades it out, and that
    // rule is the whole value of the layer — the image itself is the host's,
    // because there is no image loader in common code.
    host.slots.backdrop?.let { image ->
        ChromeBackdrop(
            visible = backdropIsVisible(scene.state.durationSeconds, scene.state.timeSeconds),
        ) {
            image(scene.state, scene.commands)
        }
    }
}

// Which skip, if any, the film is currently inside.
//
// Reads the chapter under the position and asks SkipPrompt whether it should be
// offered here — the two playlist guards are its business, not this file's.
internal fun skipOffer(state: ChromeState): SkipKind? {
    val chapter: TvChapter = SkipPrompt.chapterAt(state.chapters, state.timeSeconds) ?: return null
    val title: String = chapter.title ?: return null

    val position = SkipPosition(
        durationSeconds = state.durationSeconds,
        currentSeconds = state.timeSeconds,
        index = state.queueIndex,
        playlistSize = state.queueSize,
    )

    return if (SkipPrompt.shouldOffer(title, position)) SkipPrompt.typeOf(title) else null
}

// Where a skip lands: the start of the chapter after the one being skipped, or
// nothing when there is no chapter after it.
internal fun skipTargetOf(state: ChromeState): Double? =
    state.chapters
        .map { it.startSeconds }
        .filter { it > state.timeSeconds }
        .minOrNull()

// `linear-gradient(to top, rgba(0,0,0,0.85), rgba(0,0,0,0.4), rgba(0,0,0,0))`,
// listed the way Compose paints — top of the fade first. The same three stops
// the top bar uses, reversed, and the middle one is what keeps it from reading
// as a grey band laid over the picture.
internal val BOTTOM_SCRIM: List<Color> = listOf(
    Color.Transparent,
    Color.Black.copy(alpha = 0.40f),
    Color.Black.copy(alpha = 0.85f),
)

// `height: calc(100% + 24px)`. The fade begins above the stack, so the strip has
// something behind it before the controls do.
internal val SCRIM_OVERHANG: Dp = 24.dp

// `.bottom-bar { gap: 8px; padding-bottom: 8px }`.
internal val BOTTOM_STACK_GAP: Dp = 8.dp
internal val BOTTOM_STACK_PADDING: Dp = 8.dp

// `.top-row { margin-top: 16px; height: 8px }`.
// `.bottom-row { height: 40px }`, shared so the skip prompt can clear it.
internal val TRANSPORT_ROW_HEIGHT: Dp = 40.dp
internal val STRIP_TOP_MARGIN: Dp = 16.dp
internal val STRIP_ROW_HEIGHT: Dp = 8.dp

// How tall the whole bottom stack is: its own bottom padding, the transport row,
// the gap, and the strip with its top margin.
//
// Summed from the four constants that draw it rather than written as 80, so a
// change to any of them moves whatever clears the stack with it. The skip prompt
// is what needs clearing: it aligned to the chrome's bottom edge — the same edge
// the strip and the transport row sit on — so it was drawn ON the controls
// instead of above the time bar.
internal val BOTTOM_STACK_HEIGHT: Dp =
    BOTTOM_STACK_PADDING + TRANSPORT_ROW_HEIGHT + BOTTOM_STACK_GAP + STRIP_TOP_MARGIN + STRIP_ROW_HEIGHT
