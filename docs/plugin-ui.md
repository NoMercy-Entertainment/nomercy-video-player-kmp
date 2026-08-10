# Shipping UI from a plugin

A third party can put a control in the chrome without touching this library —
no fork, no PR against the video library, no rebuild.

## Declaring where it goes

A plugin states which region it wants in its manifest:

```kotlin
class SkipIntroPlugin : Plugin<Unit>() {
    companion object Manifest : PluginManifest {
        override val id = "skip-intro"
        override val version = "1.0.0"
        override val contributions: List<ChromeContribution> = listOf(
            object : ChromeContribution {
                override val slot = ChromeSlot.Overlay
            },
        )
    }
    override val manifest: PluginManifest = Manifest
}
```

`ChromeSlot` is a closed set — `TopBar`, `Transport`, `Scrubber`,
`SettingsMenu`, `Overlay`, `SidePanel`, `Background` — declared up front so a
chrome knows what it will be asked to render before any plugin code runs.
Nothing here is Compose: `commonMain` has no UI toolkit, and this is the part
of the contract every platform reads regardless of which one draws it.

## Drawing it, on a Compose surface

The plugin implements a second interface on itself, the Compose half:

```kotlin
class SkipIntroPlugin : Plugin<Unit>(), ComposeChromeContribution {
    // ...manifest as above...

    @Composable
    override fun Render(player: ComposedPlayer) {
        SkipIntroButton(onClick = { player.seekToChapterEnd() })
    }
}
```

It takes the player itself, not a chrome's own read projection — the phone
chrome and the TV chrome each have a different one, and the player is the one
thing both already hold. A control that needs playback state collects the
player's own `StateFlow` the way any other composable would.

A plugin that is real on every other platform and simply has no Compose
renderer yet is skipped rather than crashing a chrome that reaches for it.

## Additive slots: `Overlay` and `SidePanel`

Everything a plugin contributes to `Overlay` draws, in the registry's own
order, alongside the chrome's own controls — a skip-intro button and a cast
banner are the host's features sitting over the picture, not a takeover of
it. This is wired on every chrome surface that renders one: `VideoChrome.kt`
(phone/desktop) and Android TV's `NMTvPlayerView.kt`.

## Replacing a slot: `TopBar`, `Transport`, `Scrubber`

A contribution can take a slot over instead of adding to it:

```kotlin
override val contributions: List<ChromeContribution> = listOf(
    object : ChromeContribution {
        override val slot = ChromeSlot.Transport
        override val replaces = true
    },
)
```

`replaces = true` is what lets a plugin swap in its own transport row
instead of drawing beside the built-in one. Two plugins both claiming the
same slot with `replaces = true` is a conflict the registry reports rather
than silently picking one.

### Resolution order

A slot with a host-override tier resolves in three steps, in this order:

1. **The application's own override** (`ChromeSlots`/`LocalChromeSlots`) —
   an explicit choice the app made about its own screen. A plugin the app
   also chose to install has no claim over that.
2. **A plugin's `replaces` contribution** — the built-in's opt-out, offered
   when no host override exists.
3. **The chrome's own built-in widget** — the fallback of last resort, so a
   slot is never blank.

A `replaces` contribution whose plugin has no `ComposeChromeContribution`
renderer on the current platform falls through to the built-in rather than
drawing nothing, the same "skip rather than crash" rule `Overlay` uses.

`SettingsMenu` has no host-override tier and renders a multi-pane submenu
router rather than one widget, so a plugin can claim it as a `replaces`
contribution but the semantics of replacing a whole panel versus adding one
more entry point are not yet decided — check `ChromeSlotResolution`'s own
call sites in `VideoChrome.kt` for the slots that are wired today (`TopBar`,
`Transport`, `Scrubber`) before relying on this for `SettingsMenu`.

## What's wired where

| | `Overlay`/`SidePanel` (additive) | `TopBar`/`Transport`/`Scrubber` (replaces-aware) | `SettingsMenu` |
| --- | --- | --- | --- |
| Phone / Desktop (`VideoChrome.kt`) | yes | yes | not yet |
| Android TV (`NMTvPlayerView.kt`) | yes | not yet | not yet |
| tvOS SwiftUI | not yet | not yet | not yet |
