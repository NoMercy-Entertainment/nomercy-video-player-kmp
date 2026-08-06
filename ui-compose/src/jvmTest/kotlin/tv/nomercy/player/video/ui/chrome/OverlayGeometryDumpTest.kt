// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where every tagged overlay element actually lands, written where a diff can
 * read it.
 *
 * This is the other half of the pixel comparison. `scripts/web-overlay-geometry.mjs`
 * measures the web player on the running page and normalises each box to the
 * container; this measures the composed native chrome the same way, so the two
 * files subtract. Comparing raw pixels instead would fail on nothing but the
 * two players being different sizes on screen, which has already manufactured
 * a divergence in this campaign.
 *
 * A dump rather than a set of assertions, because the reference numbers live in
 * a JSON captured from a browser and belong beside it, not scattered through
 * Kotlin constants that two files can agree on while both are wrong. The
 * assertion here is only that the chrome composed at all — an empty dump that
 * silently compares as "no differences" is the failure mode this exists inside.
 *
 * Written to `build/overlay-geometry.json` on every run of the suite.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayGeometryDumpTest {

    @Test
    fun theComposedChromeReportsItsGeometry() = runComposeUiTest {
        setContent {
            val player = NMVideoPlayer(RecordingVideoBackend())
            LaunchedEffect(player) { player.setup(PlayerConfig()) }

            Box(modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp)) {
                VideoChrome(player, FormFactor.Desktop)
            }
        }
        waitForIdle()

        val rows: MutableList<String> = mutableListOf()

        for (tag in TAGS) {
            // onAllNodesWithTag, not onNodeWithTag: a control the current
            // config does not draw is absent rather than failed, and a dump
            // that threw on the first one would report nothing about the
            // twenty that are there.
            val nodes = onAllNodesWithTag(tag).fetchSemanticsNodes()
            if (nodes.isEmpty()) continue

            val node = nodes.first()
            val bounds = node.boundsInRoot
            rows += """{"name":"$tag"""" +
                ""","left":${bounds.left / WIDTH}""" +
                ""","top":${bounds.top / HEIGHT}""" +
                ""","width":${bounds.width / WIDTH}""" +
                ""","height":${bounds.height / HEIGHT}""" +
                ""","widthPx":${bounds.width.toInt()}""" +
                ""","heightPx":${bounds.height.toInt()}}"""
        }

        // The bar and its play button, by name. A count was the first form of
        // this guard and it was a guess dressed as a threshold: the number that
        // composes depends on what tracks the fixture's item carries, so a
        // threshold either passes on a chrome that failed or fails on one that
        // worked. These two are drawn by every configuration there is, so their
        // absence means the chrome did not compose — which is the only thing
        // this test is entitled to claim. The parity NUMBER belongs to the
        // report, measured against the browser.
        assertTrue(
            rows.any { it.contains(TRANSPORT_BAR_TAG) } && rows.any { it.contains(PLAY_PAUSE_TAG) },
            "the transport bar did not compose — an empty dump compares clean against anything",
        )

        val out = File("build/overlay-geometry.json")
        out.parentFile?.mkdirs()
        out.writeText(
            """{"container":{"width":$WIDTH,"height":$HEIGHT},"elements":[${rows.joinToString(",")}]}""",
        )
    }

    private companion object {
        const val WIDTH = 1280
        const val HEIGHT = 720

        val TAGS: List<String> = listOf(
            TRANSPORT_BAR_TAG,
            PLAY_PAUSE_TAG,
            PREVIOUS_TAG,
            NEXT_TAG,
            SEEK_BACK_TAG,
            SEEK_FORWARD_TAG,
            CHAPTER_BACK_TAG,
            CHAPTER_FORWARD_TAG,
            VOLUME_TAG,
            VOLUME_CONTROL_TAG,
            VOLUME_TRACK_TAG,
            ASPECT_RATIO_TAG,
            THEATER_TAG,
            PIP_TAG,
            SPEED_TAG,
            SUBTITLES_TAG,
            AUDIO_TAG,
            QUALITY_TAG,
            PLAYLIST_TAG,
            SETTINGS_TAG,
            FULLSCREEN_TAG,
        )
    }
}
