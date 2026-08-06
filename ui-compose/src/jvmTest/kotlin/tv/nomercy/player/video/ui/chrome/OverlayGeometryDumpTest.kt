// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.tv.TvChapter
import tv.nomercy.player.video.ui.chrome.menus.RecordingMenuCommands
import tv.nomercy.player.video.ui.tv.TvChromeStrings
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
 *
 * SIZES from this dump are comparable to the browser's; POSITIONS are not yet.
 * It composes the transport bar alone, and the reference's bar sits on top of a
 * scrubber row inside a padded stack — so every element comes back offset by the
 * same 0.02 of the container. A uniform offset across every element is the
 * signature of a fixture that places the thing differently, not of a layout that
 * is wrong, and the way to close it is to compose the whole bottom stack here
 * rather than to widen a tolerance until it passes.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayGeometryDumpTest {

    @Test
    fun theComposedChromeReportsItsGeometry() = runComposeUiTest {
        setContent {
            // Bottom-aligned, where the chrome puts it. Rendering the bar at
            // the top of the box was the first form and every `top` came back
            // as 0 against the reference's 0.92 — nineteen findings about a
            // fixture, not a layout. A geometry comparison has to place the
            // thing it measures where the real one sits.
            Box(
                modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                // The chrome's whole bottom stack, from the chrome's own
                // constants: the scrubber row's height, the gap, and the
                // padding under the bar. Composing the bar alone put every
                // element the same 0.02 of the container too low, and a uniform
                // offset across everything is the fixture placing things
                // differently rather than a layout that is wrong.
                //
                // The strip stands in as a spacer of its stated height. The
                // question here is where the BAR lands, and mounting the real
                // strip would need a scene and a host to answer a question
                // neither of them changes.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = BOTTOM_STACK_PADDING),
                    verticalArrangement = Arrangement.spacedBy(BOTTOM_STACK_GAP),
                ) {
                    Spacer(Modifier.height(STRIP_TOP_MARGIN + STRIP_ROW_HEIGHT))
                    TransportBar(
                        state = FULLY_STOCKED,
                        commands = RecordingMenuCommands(),
                        strings = TvChromeStrings(),
                        buttons = EVERY_BUTTON,
                    )
                }
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

        /**
         * An item that can offer every control.
         *
         * A default-configured player drew seven of them, and the seven were
         * the whole geometry comparison: chapter buttons, the quality menu,
         * the audio menu and the playlist are all withheld from a bar whose
         * item has no chapters, one rung, one audio track and a queue of one.
         * That is correct behaviour and it made the measurement describe the
         * fixture rather than the layout.
         */
        /**
         * Every optional control asked for.
         *
         * Nine of the sixteen default to OFF — a consumer opts in to subtitles,
         * quality, speed, playlist, theater and the rest — so a bar built from
         * the defaults draws about half the reference's controls and a geometry
         * comparison over it silently covers half the overlay.
         */
        val EVERY_BUTTON = ChromeButtons(
            // Seek buttons OFF, because the reference page does not draw them.
            // Turning them on packed two extra controls into the left group and
            // shifted every control after them — four findings about a fixture
            // asking for more than the thing it is measured against.
            subtitles = true,
            audio = true,
            quality = true,
            speed = true,
            aspectRatio = true,
            playlist = true,
            theater = true,
            pictureInPicture = true,
        )

        val FULLY_STOCKED = ChromeState(
            durationSeconds = 1_800.0,
            timeSeconds = 120.0,
            chapters = listOf(
                TvChapter(0.0, "Opening Credits"),
                TvChapter(90.0, "Episode"),
                TvChapter(1_400.0, "Ending"),
            ),
            qualityLevels = RecordingVideoBackend.LEVELS,
            activeQuality = RecordingVideoBackend.LEVELS.firstOrNull(),
            audioTracks = listOf(
                AudioTrack(id = "en", language = "en", label = "English"),
                AudioTrack(id = "nl", language = "nl", label = "Nederlands"),
            ),
            subtitleTracks = listOf(SubtitleTrack(id = "en", language = "en", label = "English")),
            queueSize = 3,
            queueIndex = 1,
        )

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
