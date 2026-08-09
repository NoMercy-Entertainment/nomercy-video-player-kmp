# Fonts carried by this module

## Roboto — shipped

`src/jvmAndroidMain/resources/tv/nomercy/player/video/ass/NoMercyFallback.ttf`
is Roboto Regular, unmodified, from the Debian `fonts-roboto-unhinted` package.
Roboto is licensed under the Apache License 2.0, the same licence this library
ships under.

It is here because libass resolves a cue's font through a system provider, and a
machine whose provider answers nothing renders every cue as no glyphs at all:
the layout is done, the timing is right, the screen is empty and nothing in the
stack says so. `ass_set_fonts` takes a fallback as a path, so the face is
unpacked once at runtime and handed over as the default. A viewer on a container
image with no fonts installed gets subtitles instead of silence.

## DejaVu Sans and DejaVu Serif — tests only

`src/jvmTest/resources/tv/nomercy/player/video/ass/TestFaceSans.ttf` and
`TestFaceSerif.ttf` are DejaVu Sans and DejaVu Serif, unmodified, from the
Debian `fonts-dejavu-core` package, under the Bitstream Vera licence.

They are on the test classpath and in no published artifact. The render gate
needs two faces with visibly different shapes to prove a cue's font request
reaches libass rather than being answered by whatever the host happens to have
installed, and asking the machine for a second font is what made that gate pass
on a developer's laptop and fail in CI.
