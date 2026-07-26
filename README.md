# nomercy-video-player-kmp

The video half of the NoMercy player trio, for Kotlin Multiplatform.

Everything a player does — transport, queue, volume, time, state, plugins,
lifecycle — lives in [`nomercy-player-core-kmp`][core]. This library adds only
what makes a player a *video* player: the video-domain surface (fullscreen,
picture-in-picture, theater, aspect ratio, the video rect, segment skipping),
the video backends, and the nineteen events in `VideoEvents` that only a video
player has.

No core method is restated here. A listener for `play` uses `CoreEvents.Play`
even on a video player: there is one bus and one set of names, and the split is
about which library owns the declaration.

## Building it

```
./gradlew build
```

The build depends on core by its published coordinate and substitutes a sibling
`../nomercy-player-core-kmp` checkout when there is one, so a change to core is
picked up without publishing first. Without that, two libraries that ship
together would drift apart between releases.

## What is here

The library is three pieces, and a consumer takes only what it needs.

`nomercy-video-player-kmp` is the player itself: the video event registry and
its payload types, `NMVideoPlayer`, the backend bridge, and the subtitle
parsers. It has no UI and no native subtitle library.

`:ui-compose` is the drop-in view for Android and the desktop — a media surface
with one play/pause control bound to the player's state. Compose is a
dependency you should be able to decline, which is why it is a separate module.
Apple gets SwiftUI instead: `apple/NoMercyPlayer` is an SPM package with the
same view for iOS and tvOS. A Compose surface on iOS would fight the app it was
embedded in.

`:subtitles-libass` renders styled ASS subtitles through libass. Android works
today over the same JNI binding the NoMercy app ships. The desktop and Apple
report why they cannot rather than failing at the point of use — ask
`AssRenderers.whyUnavailable()` and you get a sentence, not a stack trace.

## What is proven, and where

Nothing below says "it compiles". Each row is a behaviour, the command that
measures it, and where that command can run.

| Behaviour | Android | Desktop | iOS | tvOS |
|---|---|---|---|---|
| The control toggles playback | `testAndroidHostTest` | `jvmTest` | `xcodebuild test` | `xcodebuild test` |
| The engine's own state redraws the control | `testAndroidHostTest` | `jvmTest` | `xcodebuild test` | `xcodebuild test` |
| The select key toggles playback with nothing to aim at | `testAndroidHostTest` | `jvmTest` | n/a | `xcodebuild test` |
| Video paints | `connectedAndroidDeviceTest` | `jvmTest` | device QA | device QA |
| The control draws over the video | — | `jvmTest` | device QA | device QA |
| One ASS cue rasterizes visible pixels | `connectedAndroidDeviceTest` | `jvmTest`, where libass is installed | not linked yet | not linked yet |
| Fonts are attached before the track loads | `jvmTest` | `jvmTest` | `jvmTest` | `jvmTest` |

CI runs everything except the device rows. Those need hardware attached, and
they run from a developer machine — the matrix does not pretend to cover them.
Video painting on Android is proven on a phone and on an Android TV box.

## What libass needs from the machine

On Linux and macOS it is a package away — `apt install libass9`,
`brew install libass` — and the renderer loads whichever is installed, including
from Homebrew's directories, which a JVM does not search by default.

On Windows there is no renderer yet. The only builds in circulation are the
copies statically linked inside VLC and mpv, which cannot be loaded from outside
them, so shipping one means vendoring a build. That is a distribution decision
rather than a rendering one, and until it is made a Windows caller gets a
sentence from `AssRenderers.whyUnavailable()` and can fall back to plain text.

libass for Apple is built and vendored in the NoMercy app but not linked here.
Wiring it means a cinterop definition against an artifact no CI runner produces,
which is a build-graph decision with the same shape.

[core]: https://github.com/NoMercy-Entertainment/nomercy-player-core-kmp
