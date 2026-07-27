// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

// A player that only remembers what it was asked to do.
//
// The bindings are what is under test, so the player has to be the part that
// does nothing: a real one would answer with its own behaviour and the test
// would be measuring both at once.
//
// One recorder implementing all three seams, because a case reads better
// asserting one list of calls than three.
internal class RecordingPlayerCommands(
    private var playing: Boolean = false,
    private var currentRate: Float = 1f,
    private var position: Double = 0.0,
    private var length: Double = 0.0,
) : VideoTransport, VideoPresentation, VideoVolume, VideoState {

    val calls: MutableList<String> = mutableListOf()

    val seeks: MutableList<Float> = mutableListOf()

    val messages: MutableList<String> = mutableListOf()

    val emitted: MutableList<String> = mutableListOf()

    val commands: PlayerCommands = PlayerCommands(this, this, this, this)

    override fun togglePlay() { calls += "togglePlay" }

    override fun play() { calls += "play" }

    override fun pause() { calls += "pause" }

    override fun stop() { calls += "stop" }

    override fun seekBy(deltaSeconds: Float) {
        calls += "seekBy"
        seeks += deltaSeconds
    }

    override fun next() { calls += "next" }

    override fun previous() { calls += "previous" }

    override fun nextChapter() { calls += "nextChapter" }

    override fun previousChapter() { calls += "previousChapter" }

    override fun cycleSubtitles() { calls += "cycleSubtitles" }

    override fun cycleAudioTracks() { calls += "cycleAudioTracks" }

    override fun volumeUp() { calls += "volumeUp" }

    override fun volumeDown() { calls += "volumeDown" }

    override fun toggleMute() { calls += "toggleMute" }

    override fun toggleFullscreen() { calls += "toggleFullscreen" }

    override fun cycleAspectRatio() { calls += "cycleAspectRatio" }

    override fun rate(): Float = currentRate

    override fun rate(value: Float) {
        calls += "rate"
        currentRate = value
    }

    override fun rates(): List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    override fun time(): Double = position

    override fun duration(): Double = length

    override fun isPlaying(): Boolean = playing

    override fun emit(name: String) {
        emitted += name
    }

    override fun message(text: String) {
        messages += text
    }

    fun at(seconds: Double, of: Double) {
        position = seconds
        length = of
    }
}
