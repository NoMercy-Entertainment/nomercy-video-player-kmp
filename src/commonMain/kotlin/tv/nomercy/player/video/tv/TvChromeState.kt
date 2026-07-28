// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.tv

// What is on screen over the picture.
//
// One value rather than six separate flags, because the interesting bugs are all
// combinations: controls showing over a dialog, a scrub bar left up after the
// dialog that opened over it closed, a pre-screen and a seek preview at once.
// Held together, an impossible pair is visible in one place.
public data class TvChromeUi(
    val preScreenVisible: Boolean = false,
    val controlsVisible: Boolean = false,
    val seekMode: Boolean = false,
    val dialog: TvDialog = TvDialog.None,
    val topBarHasFocus: Boolean = false,
    val volumeIndicatorVisible: Boolean = false,
)

// Only one at a time, which is why this is an enum and not a set of booleans.
// Two dialogs open at once is not a state anybody designed; it is what happens
// when six flags are set independently.
public enum class TvDialog { None, PreScreen, Episodes, Language, Subtitle, SubtitleSearch }

// What the chrome asks the player to do.
//
// Severed from every store and every navigator on purpose. The implementation
// this is extracted from reached a playback store, a navigation controller and a
// system audio service directly, which is why none of it could be tested and why
// none of it could be reused on a platform without those three.
public interface TvChromeCallbacks {

    public fun play()

    public fun pause()

    public fun togglePlay()

    public fun seek(seconds: Float)

    // What the scrub bar shows while a viewer is still moving it, before they
    // commit. Null puts the display back on the real position.
    public fun overrideTime(seconds: Float?)

    public fun restart()

    public fun next()

    // Leaving the player entirely. A back press that reaches the bottom of the
    // chrome is a navigation decision, and a library that popped a stack itself
    // would be a library that knows how the application is put together.
    public fun exitPlayer()
}

// Something that will happen later, and can be told not to.
//
// A seam rather than a coroutine scope because the auto-hide is the one piece of
// this with a clock in it, and a state machine that owned a scope could not be
// driven by a test at its own pace.
public fun interface Scheduler {

    public fun schedule(delayMs: Long, action: () -> Unit): Cancellable
}

public fun interface Cancellable {

    public fun cancel()
}
