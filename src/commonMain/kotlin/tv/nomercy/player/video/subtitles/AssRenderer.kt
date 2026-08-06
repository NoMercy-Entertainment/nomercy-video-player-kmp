// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

// Styled subtitles, rasterized by libass.
//
// ASS is not text with a colour. It is positioned, rotated, faded, karaoke-timed
// drawing with its own layout engine, and the only implementation anyone trusts
// is libass — which is why every surface here binds to the same C library rather
// than reimplementing a renderer three times and disagreeing three ways.
//
// The renderer is deliberately not a view. It turns a track and a timestamp into
// images with positions; where those are drawn is the surface's business, and
// keeping that out of here is what lets one contract serve a Compose overlay, a
// SurfaceView and a CALayer.
public interface AssRenderer {

    // Fonts must arrive before the track is drawn. libass resolves a font at the
    // moment it draws, and one added afterwards is a cue that rendered in the
    // wrong typeface with nothing reported.
    //
    // [name] labels the embedded blob; it is NOT what a cue matches on. libass
    // compares a style's Fontname against the family recorded inside the font
    // file, so attaching a font under an invented alias resolves nothing at all.
    // Whatever hands fonts to this has to read the family out of the TTF rather
    // than trust the manifest's filename.
    public fun addFont(name: String, data: ByteArray)

    // Drops every font this renderer was given.
    //
    // A queue advancing to the next title is the case. Attached fonts belong to
    // the item they came with, and a renderer that kept the previous one's would
    // resolve against a face the new item never shipped — which libass does
    // silently, because a font that exists is a font it can draw with. The cue
    // appears in the wrong typeface and nothing anywhere reports a problem.
    public fun clearFonts()

    public fun loadTrack(assContent: String)

    // The size of the surface the cues will be drawn onto. Positions come back
    // in these coordinates, so getting it wrong puts the subtitle in the wrong
    // place rather than failing.
    public fun frameSize(width: Int, height: Int)

    // The size of the VIDEO the cues were authored against, which is not the
    // size they are drawn at.
    //
    // Borders, shadows and blur are authored in the script's own coordinates,
    // and libass scales them by frame over storage. Leaving storage equal to the
    // frame makes that ratio one, so a 1080p track drawn into a 590-pixel-tall
    // overlay keeps its full-size outlines around half-size glyphs — every cue
    // comes out thick and blobby, and nothing reports a problem because the text
    // itself is the right size.
    //
    // A host that knows the decoded video size should say so. A track's own
    // PlayResX/PlayResY is the fallback the renderer primes itself with, since
    // that is the space the author was working in.
    public fun storageSize(width: Int, height: Int)

    // The space in effect: what a host set, or what the loaded track declares,
    // or null before either is known.
    //
    // A surface needs it to decide how big to rasterize. Drawing at the size of
    // the overlay looks right until the overlay is smaller than the video, and
    // then everything libass does not scale linearly — outline thickness,
    // shadow offset, blur radius — arrives proportionally too heavy. Every
    // other player rasterizes at the video's own resolution and lets the GPU
    // scale the result, which is why theirs stay crisp in a small window.
    public fun storageSize(): AssSize?

    // Null when nothing changed since the last call at a nearby timestamp, which
    // is most frames: redrawing an unchanged cue sixty times a second is how a
    // subtitle renderer becomes the reason a device gets hot.
    public fun render(timeMillis: Long): AssFrame?

    public fun release()
}

// A size in pixels, for the geometry the renderer and its surface have to agree
// on. Its own type rather than a pair, because a pair of ints at a call site
// says nothing about which one is the width.
public data class AssSize(
    val width: Int,
    val height: Int,
)

// One frame's worth of cues, as images the surface positions itself.
//
// Valid until the next call to render() on the same renderer, which then
// overwrites the coverage arrays in place. Draw the frame, or copy what is
// needed out of it, before asking for another — holding one across a render
// reads pixels from a later frame.
//
// A renderer allocating a fresh array per glyph run instead produced a million
// of them across a two-minute ending sequence, and the collector's pauses were
// what dropped frames rather than the rendering.
public data class AssFrame(
    val images: List<AssImage>,
    val changed: Boolean,
)

// A rasterized run of glyphs. [pixels] is 8-bit coverage, one byte per pixel,
// [stride] wide — libass's own alpha bitmap, tinted by [colour] at draw time.
//
// Coverage rather than RGBA because that is what libass produces, and expanding
// it to four channels here would cost four times the memory for a value the
// surface is about to multiply by a colour anyway.
public data class AssImage(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val stride: Int,
    val colour: Int,
    val pixels: ByteArray,
) {
    // Data classes compare arrays by identity, which would make two identical
    // frames unequal and any test asserting on one meaningless.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AssImage) return false
        return x == other.x && y == other.y && width == other.width && height == other.height &&
            stride == other.stride && colour == other.colour && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result: Int = x
        result = HASH_FACTOR * result + y
        result = HASH_FACTOR * result + width
        result = HASH_FACTOR * result + height
        result = HASH_FACTOR * result + stride
        result = HASH_FACTOR * result + colour
        result = HASH_FACTOR * result + pixels.contentHashCode()
        return result
    }

    private companion object {
        const val HASH_FACTOR = 31
    }
}
