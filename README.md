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

## Status

Early. The event registry and its payload types are here and tested; the
concrete `NMVideoPlayer` and the ExoPlayer, AVPlayer and VLCJ backends are not
yet.

[core]: https://github.com/NoMercy-Entertainment/nomercy-player-core-kmp
