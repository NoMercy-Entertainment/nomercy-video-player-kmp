// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Methods the web video player has because the web has a DOM, or because
// JavaScript needs a workaround Kotlin does not.
private val WEB_ONLY_METHODS = setOf(
    "addClasses",
    "removeClasses",
    "container",
    "createButton",
    "createElement",
    "createSVG",
    "audioContext",
    "selectAudioOutput",
    // The <video> element itself. There is no such handle off the web; the
    // equivalent here is backend(), which the player already exposes.
    "videoElement",
    // A runtime method-override table. The web needs one because a
    // mixin-composed player cannot be subclassed; every member here is already
    // open, so a consumer replacing one behaviour overrides one method.
    "experimental",
)

// Video-specific methods this library has not reached.
//
// Written out rather than derived, for the same reason core's and music's are:
// a set difference that quietly got smaller for the wrong reason looks like
// progress. The list shrinking is the measure of this library.
private val NOT_YET_PORTED_METHODS: Set<String> = emptySet()

// The video player's own surface against the contract's.
//
// Core proves the shared surface and this asks the narrower question: does the
// video player carry what the contract tags video, and does it invent anything
// the contract does not name. A method core provides counts — inheriting one is
// having it, and a consumer reading a name in the docs finds it on the object
// either way.
class VideoSurfaceConformanceTest {

    private fun contractMethods(player: String): Set<String> {
        val file = File("contract/contract.json")
        assertTrue(file.exists(), "no vendored contract at ${file.absolutePath}")

        return Json.parseToJsonElement(file.readText()).jsonObject
            .getValue("methods").jsonArray
            .map { it.jsonObject }
            .filter { it["player"]?.jsonPrimitive?.content == player }
            .map { it.getValue("name").jsonPrimitive.content }
            .toSet()
    }

    private fun videoMethods(): Set<String> = contractMethods(VIDEO)

    private fun musicMethods(): Set<String> = contractMethods(MUSIC)

    private fun playerSurface(): Set<String> {
        val functions: List<String> = NMVideoPlayer::class.memberFunctions
            .filter { it.visibility == KVisibility.PUBLIC }
            .map { it.name }
        val properties: List<String> = NMVideoPlayer::class.memberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .map { it.name }

        return (functions + properties).toSet() - OBJECT_METHODS
    }

    @Test
    fun theContractNamesAVideoSurfaceRatherThanNothing() {
        val video: Set<String> = videoMethods()

        assertTrue(video.size > MINIMUM_VIDEO_SURFACE, "only ${video.size} methods are tagged video")
        assertTrue(video.contains("fullscreen"), "the video surface has no fullscreen")
    }

    @Test
    fun theVideoPlayerInventsNoMethodTheContractDoesNotHave() {
        val invented: Set<String> = playerSurface() - videoMethods() - musicMethods() - NATIVE_ONLY

        assertEquals(emptySet(), invented, "the video player exposes methods the contract does not name")
    }

    @Test
    fun everyVideoMethodTheLibraryLacksHasAStatedReason() {
        val missing: Set<String> = videoMethods() - playerSurface()
        val accounted: Set<String> = WEB_ONLY_METHODS + NOT_YET_PORTED_METHODS

        assertEquals(
            emptySet(),
            missing - accounted,
            "contract video methods this library neither has nor accounts for: each needs a reason, not a blank",
        )
    }

    @Test
    fun theReasonListsDoNotOutliveTheMethodsTheyExcuse() {
        val accounted: Set<String> = WEB_ONLY_METHODS + NOT_YET_PORTED_METHODS

        assertEquals(
            emptySet(),
            accounted intersect playerSurface(),
            "these methods exist on the video player and are still excused as missing",
        )
        assertEquals(
            emptySet(),
            accounted - videoMethods(),
            "these methods are excused but are not in the contract's video surface",
        )
    }

    private companion object {
        const val VIDEO = "video"
        const val MUSIC = "music"
        const val MINIMUM_VIDEO_SURFACE = 100

        val OBJECT_METHODS = setOf("equals", "hashCode", "toString")

        val NATIVE_ONLY = setOf(
            // Core's seam for AutoAdvancePlugin.preloadNext, which IS on the
            // contract. LoadOptions carries no slot here because the engines
            // expose a real secondary rather than a browser cache to warm.
            "preloadNow",
            "context", "transport", "volume", "time", "state", "lifecycle", "bridge", "activity",
            "cueParsers", "streamFactories", "metrics", "bandwidth", "plugins", "queue",
            // The emitter methods. The web player extends EventEmitter, so these are
            // inherited and the generator, which reads the player class's own
            // declarations, never sees them. Same methods on both sides; only the
            // contract cannot say so.
            "stateFlow", "rootLogger", "rootStorage", "emit", "on", "onAll", "once", "off",
            "dispatchBefore", "fetch", "websocket", "report", "formatTitle",
            "pluginList", "contributions", "coreVersion", "playerId",
            // Video-shaped and native-only: the web reads these off the DOM.
            "videoRect", "cycleAspectRatio", "toggleTheater",
            // A transient on-screen message. The web writes one into the DOM it
            // owns; native has no view, so the chrome is told and draws it.
            "message", "clearMessage",
            // The video half of the engine bridge, which the web infers from
            // the media element's own events.
            "videoBridge",
        )
    }
}
