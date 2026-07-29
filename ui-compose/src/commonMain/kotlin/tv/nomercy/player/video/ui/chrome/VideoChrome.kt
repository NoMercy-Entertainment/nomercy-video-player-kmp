// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.events.Subscription
import tv.nomercy.player.core.input.KeyCombo
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.VideoEvents
import tv.nomercy.player.video.input.VideoKeyHandlerPlugin
import tv.nomercy.player.video.input.playerCommandsOf
import tv.nomercy.player.video.ui.chrome.menus.MenuState
import tv.nomercy.player.video.ui.chrome.menus.SettingsMenu
import tv.nomercy.player.video.tv.Scheduler
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
    strings: TvChromeStrings = TvChromeStrings(),
    buttons: ChromeButtons = ChromeButtons(),
    sprite: List<SpriteCue> = emptyList(),
    onClose: (() -> Unit)? = null,
    // The web's other two top-bar events. Back and close are different exits —
    // one returns to where the viewer came from and one dismisses the player —
    // and a library that offered only the second would leave every consumer
    // rebuilding the first through the trailing slot.
    onBack: (() -> Unit)? = null,
    onCast: (() -> Unit)? = null,
    slots: ChromeSlots = LocalChromeSlots.current,
    surface: @Composable () -> Unit = {},
) {
    val scope: CoroutineScope = rememberCoroutineScope()
    val scheduler: Scheduler = rememberChromeScheduler()

    var menu: MenuState by remember { mutableStateOf(MenuState.Hidden) }
    val message: String? = rememberPlayerMessage(player)
    val state: ChromeState = rememberChromeState(player, message)

    val controller: ChromeController = remember(player, scheduler) {
        ChromeController(isPlaying = { player.playState() == PlayState.PLAYING }, scheduler = scheduler)
    }
    val commands: ChromeCommands = remember(player, scope) {
        VideoChromeCommands(player, scope) { menu = it }
    }

    ChromeBindings(controller, state.playing, menu)

    ChromeFrame(
        input = ChromeInput(rememberVideoKeys(player, formFactor), controller, commands, player::now),
        modifier = modifier,
        surface = surface,
    ) {
        ChromeLayers(
            scene = ChromeScene(state, commands, controller, strings, buttons),
            host = ChromeHost(sprite, onClose, onBack, onCast, slots),
            menu = menu,
            onMenuChange = { menu = it },
        )
    }
}

// The root, and whichever input this form factor has.
//
// The zones and the pointer are mutually exclusive on purpose. A lifted finger
// reports an exit exactly like a pointer leaving the window, so wiring both
// would hide the controls every time somebody tapped to show them.
@Composable
private fun ChromeFrame(
    input: ChromeInput,
    modifier: Modifier,
    surface: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(if (input.pointerDriven) DESKTOP_CHROME_TAG else TOUCH_CHROME_TAG)
            // Keys before focus, deliberately. A handler placed after the focus
            // target sits inside it and never sees the press that reached it.
            .then(input.keyModifier())
            .then(input.pointerModifier()),
    ) {
        surface()

        if (!input.pointerDriven) {
            TouchZonesOverlay(
                controller = input.controller,
                commands = input.commands,
                nowMs = input.nowMs,
                modifier = Modifier.fillMaxSize(),
            )
        }

        content()
    }
}

// How this build is driven.
//
// The key handler being null is the whole question: a build with a keyboard has
// one, a build without has tap zones instead. Asking it once here is what keeps
// three places from each deciding separately what a phone is.
private class ChromeInput(
    val keys: VideoKeyHandlerPlugin?,
    val controller: ChromeController,
    val commands: ChromeCommands,
    val nowMs: () -> Long,
) {

    val pointerDriven: Boolean get() = keys != null

    fun pointerModifier(): Modifier =
        if (!pointerDriven) {
            Modifier
        } else {
            Modifier
                .focusable()
                .pointerActivity(controller::bumpActivity, controller::onPointerExit)
        }

    // The chrome wakes on any press it recognises and then the key does whatever
    // it is bound to. A press neither of them wants stays with the platform,
    // which is what leaves the window's own shortcuts working.
    fun keyModifier(): Modifier {
        val handler: VideoKeyHandlerPlugin = keys ?: return Modifier

        return Modifier.onKeyEvent { event -> handlePress(handler, keyComboOf(event)) }
    }

    private fun handlePress(handler: VideoKeyHandlerPlugin, combo: KeyCombo?): Boolean {
        if (combo == null) return false

        controller.bumpActivity()
        return handler.handle(combo)
    }
}

// What the player says, as the widgets read it.
private data class ChromeScene(
    val state: ChromeState,
    val commands: ChromeCommands,
    val controller: ChromeController,
    val strings: TvChromeStrings,
    val buttons: ChromeButtons,
)

// What the host supplied, which the player knows nothing about: the sprite sheet
// its server generated, and where "out" goes.
private data class ChromeHost(
    val sprite: List<SpriteCue>,
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
        host.slots.backdrop?.invoke(scene.state, scene.commands)

        // Outside the visibility gate. A film that is still buffering has to say
        // so whether or not the controls are up, and a viewer looking at a
        // frozen picture with nothing on it cannot tell it from a crash.
        if (scene.state.buffering) {
            BasicText(
                text = scene.strings.loading,
                style = TextStyle(color = Color.White),
                modifier = Modifier.align(Alignment.Center).testTag(BUFFERING_TAG),
            )
        }

        scene.state.message?.let { text ->
            BasicText(
                text = text,
                style = TextStyle(color = Color.White),
                modifier = Modifier.align(Alignment.Center).testTag(MESSAGE_TAG),
            )
        }

        AnimatedVisibility(visible = ui.active, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    host.slots.topBar?.invoke(scene.state, scene.commands) ?: ChromeTopBar(
                        item = scene.state.item,
                        strings = scene.strings,
                        buttons = scene.buttons,
                        exits = ChromeExits(host.onBack, host.onCast, host.onClose),
                    )
                }

                ChromeBottom(scene, host, Modifier.align(Alignment.BottomCenter))
            }
        }

        SettingsMenu(scene.state, scene.commands, menu, onMenuChange, Modifier.align(Alignment.BottomCenter))

        // Additive rather than replacing. A skip-intro button and a cast banner
        // are the host's features, and a slot that swallowed the chrome to draw
        // one would be a host choosing between its feature and the controls.
        host.slots.overlays?.invoke(scene.state, scene.commands)
    }
}

// The scrubber sits above the buttons rather than inside the row, because a drag
// target the width of the picture is the one a finger actually hits.
@Composable
private fun ChromeBottom(scene: ChromeScene, host: ChromeHost, modifier: Modifier) {
    var scrub: Double? by remember { mutableStateOf(null) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val barWidth: Dp = maxWidth

        Column(modifier = Modifier.fillMaxWidth()) {
            // Above the bar, as `.slider-pop`'s `bottom: 24px` puts it, and only
            // while a drag is happening: the web's is `display: none` until then
            // and a bubble sitting over the picture at rest answers a question
            // nobody asked.
            //
            // No frame yet, so the clock and the chapter name without a picture.
            // Drawing one needs decoded pixels, which needs a tile source, which
            // is the host's to supply — the sheet says where each frame lives
            // and something has to read the bytes. That is the next thing this
            // takes, and it is a parameter rather than a rewrite.
            scrub?.let { seconds ->
                ScrubPreview(
                    seconds = seconds,
                    fraction = scrubFraction(seconds, scene.state.durationSeconds),
                    barWidth = barWidth,
                    chapterTitle = chapterTitleAt(scene.state, seconds),
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                host.slots.scrubber?.invoke(scene.state, scene.commands) ?: ChapterScrubber(
                    state = scene.state,
                    commands = scene.commands,
                    sprite = host.sprite,
                    onScrubbing = scene.controller::setScrubbing,
                    onScrub = { scrub = it },
                )

                // The dot follows the drag while one is happening and the film
                // otherwise, which is what makes it read as the same handle
                // rather than two things that swap places when a finger lands.
                ScrubNipple(
                    fraction = scrubFraction(scrub ?: scene.state.timeSeconds, scene.state.durationSeconds),
                    barWidth = barWidth,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }

            host.slots.transport?.invoke(scene.state, scene.commands) ?: TransportBar(
                state = scene.state,
                commands = scene.commands,
                strings = scene.strings,
                buttons = scene.buttons,
            )
        }
    }
}

private fun scrubFraction(seconds: Double, duration: Double): Float =
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

// What the player last told the viewer.
//
// Held here rather than on the player's per-frame snapshot, because a message is
// shown for a moment and a field on that value would make every frame carry it.
// Cleared by the player, which is who showed it.
@Composable
private fun rememberPlayerMessage(player: NMVideoPlayer): String? {
    var message: String? by remember { mutableStateOf(null) }

    DisposableEffect(player) {
        val shown: Subscription = player.on(VideoEvents.DisplayMessage) { message = it.text }
        val cleared: Subscription = player.on(VideoEvents.RemoveMessage) { message = null }

        onDispose {
            shown.dispose()
            cleared.dispose()
        }
    }

    return message
}

// The shipped bindings, for the surfaces that have a keyboard.
//
// The view's rather than the player's. A key handler registered as a plugin
// belongs to the player, and a second chrome on the same player would install a
// second copy of the same table; built here it goes when the screen goes.
@Composable
private fun rememberVideoKeys(player: NMVideoPlayer, formFactor: FormFactor): VideoKeyHandlerPlugin? {
    if (formFactor != FormFactor.Desktop) return null

    val scope: CoroutineScope = rememberCoroutineScope()

    return remember(player, scope) {
        VideoKeyHandlerPlugin(
            commands = playerCommandsOf(player, scope),
            capabilities = ChromeCapabilities(formFactor),
            nowMs = player::now,
        ).also { it.use() }
    }
}

internal const val TOUCH_CHROME_TAG = "nm-touch-chrome"
internal const val DESKTOP_CHROME_TAG = "nm-desktop-chrome"
internal const val BUFFERING_TAG = "nm-chrome-buffering"
internal const val MESSAGE_TAG = "nm-chrome-message"
