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

`:subtitles-libass` renders styled ASS subtitles through libass, on all three
platforms and through three different bindings: the published JNI binding on
Android, JNA to a system library on the desktop, and Kotlin/Native cinterop to a
prebuilt static framework on Apple. One contract, three implementations
answering the same gates the same way.

Where a platform has no libass it says so rather than failing at the point of
use — ask `AssRenderers.whyUnavailable()` and you get a sentence, not a stack
trace.

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
| One ASS cue rasterizes visible pixels | `connectedAndroidDeviceTest` | `jvmTest`, where libass is installed | `iosSimulatorArm64Test` | `iosSimulatorArm64Test` |
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
them, so shipping one means vendoring a build. Until then a Windows caller gets
a sentence from `AssRenderers.whyUnavailable()` and can fall back to plain text.

On Apple nothing is needed. libass, freetype, fribidi and harfbuzz are built
once from upstream and published as a release asset, and the build fetches them
— cross-compiling them needs an autotools toolchain and half an hour, which is
not something a Gradle build should ask of a fresh machine. The archive is
pinned by tag and checked against a digest.

## What the subtitle gate proves, and where

The claim being made is not that libass initialises. It is that a real anime
track renders correctly: the Rail Wars! opening, with the Negotiate Free face it
attaches, fetched through the same authenticated path a player uses. The same
assertions run on every surface from one shared source file, so a binding that
drifts is a red test rather than a difference nobody looks for.

| surface | binding | where it runs | in CI |
| --- | --- | --- | --- |
| Android phone | JNI, `io.github.peerless2012:ass` | hardware | no |
| Android TV | JNI, same binding | hardware | no |
| iOS | cinterop, `ios-arm64` slice | simulator | yes |
| tvOS | cinterop, `tvos-arm64` slice | simulator | yes |
| Linux desktop | JNA, system `libass9` | runner | yes |
| macOS desktop | JNA, Homebrew libass | runner | yes |
| Windows desktop | none | runner | skips, loudly |

Two of those rows are worth reading twice.

**Android is not in CI and is not pretending to be.** The runners have no
device, and an emulator running a subtitle renderer under software rendering
measures the emulator. It runs on a Galaxy A13, a Nokia Streaming Box 8010 and
an 8000 before anything ships, and the memory tiers exist because of what the
8000 does to a 128MB libass cache.

**Windows skips, and a skip is not a pass.** A host that installs libass on
purpose sets `NOMERCY_REQUIRE_LIBASS=1`, and the gate then fails with the reason
libass gave rather than printing a line nobody reads — because the Linux job had
installed the package for a while and nothing would have noticed if the install
stopped working.

### What is still open

The font manifest is a single point of failure on the server side. A missing
`fonts.json` degrades to the system face and reports
`plugin:subtitle/fonts-manifest-failed`, which is the right behaviour and not a
substitute for the file being there; there is no client-side fallback that could
supply a face the track was authored against.

The Apple libass archive is built by us rather than shipped by upstream, from
pinned sources, by `subtitles-libass/apple/libass-build/build-apple.sh`. Nobody
upstream publishes tvOS slices, which is why this exists.

[core]: https://github.com/NoMercy-Entertainment/nomercy-player-core-kmp
