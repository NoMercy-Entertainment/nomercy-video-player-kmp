// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.conformance

import tv.nomercy.player.conformance.ContractFixture
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertTrue

// What this player exposes, against what the contract says it should.
//
// The gate core has, over the concrete class. A method the ecosystem documents
// and this port never implemented is a consumer following the docs into
// nothing, and neither a compiler nor a test of the methods that do exist would
// say so.
class MethodSurfaceConformanceTest {

    // Everything a caller can reach, by name. Properties count: the contract
    // does not distinguish `player.plugins` from `player.plugins()`, and neither
    // does somebody reading a name in a document.
    private fun playerSurface(): Set<String> {
        val functions: List<String> = NMVideoPlayer::class.memberFunctions
            .filter { it.visibility == KVisibility.PUBLIC }
            .map { it.name }

        val properties: List<String> = NMVideoPlayer::class.memberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .map { it.name }

        return (functions + properties).toSet()
    }

    // What the web exposes and no native player can.
    private val webOnly: Set<String> = setOf(
        // Browser affordances with no native counterpart. A Kotlin player has no
        // document to make an element in and no video element to hand back; the
        // web player exposes these because a consumer there styles the container
        // it was mounted into.
        //
        // Separate from notYetPorted on purpose: that set can only shrink and
        // shrinking it is the work, while this one never will. Merged, a real
        // gap could hide behind a permanent one forever.
        "addClasses",
        "removeClasses",
        "container",
        "videoElement",
        "createElement",
        "createButton",
        "createSVG",
        "audioContext",
        "selectAudioOutput",
        "experimental",
    )

    // Named rather than counted. A port in progress will not have every method,
    // and a threshold lets the list grow back silently; a set can only be
    // shortened, and shortening it is the work.
    private val notYetPorted: Set<String> = setOf()

    @Test
    fun everyContractedMethodExistsOnThePlayer() {
        val missing: Set<String> = ContractFixture.methods("video") - playerSurface() - notYetPorted - webOnly

        assertTrue(
            missing.isEmpty(),
            "the contract documents ${missing.size} video method(s) this player does not have: " +
                missing.sorted().joinToString(", "),
        )
    }

    @Test
    fun theContractIsActuallyBeingRead() {
        // A gate comparing against an empty set passes forever. Without this a
        // missing fixture reads as a player with nothing left to implement.
        assertTrue(
            ContractFixture.methods("video").size > 50,
            "the video contract surface is implausibly small — is the fixture vendored?",
        )
    }

    @Test
    fun theAllowanceIsHonest() {
        // Every name in the allowance has to still be missing. One that has
        // since been implemented is an allowance nobody removed, and the next
        // person reads it as work outstanding.
        val stale: Set<String> = (notYetPorted + webOnly) intersect playerSurface()

        assertTrue(stale.isEmpty(), "implemented and still listed as unported: ${stale.sorted()}")
    }
}
