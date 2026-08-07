// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import javax.imageio.ImageIO
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
 * Written to `build/overlay-geometry.json` and `build/overlay-frame.png` on
 * every run of the suite — the numbers and the frame they describe, from one
 * composition.
 *
 * Sizes AND positions are comparable to the browser's. Positions were not, for
 * as long as this composed the transport bar alone while the reference's bar
 * sits on top of a scrubber row inside a padded stack: every element came back
 * offset by the same 0.02 of the container. A uniform offset across every
 * element is a fixture placing the thing differently, never a layout that is
 * wrong, and it was closed by composing the whole bottom stack here rather than
 * by widening a tolerance until it passed.
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
                // Black behind it, because the chrome is white and it always
                // sits over video. Without a backdrop the capture is white on
                // white and eighteen of the twenty controls vanish — the frame
                // came back holding nothing but the two timestamps, which are
                // the only grey things on the bar. An overlay photographed
                // against nothing is not the overlay.
                modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp).background(Color.Black),
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

        // The rectangle everything below is normalised against, measured rather
        // than assumed. See the note in `measured`.
        val frame = onRoot().fetchSemanticsNode().boundsInRoot
        val rows: List<String> = measured(frame.width, frame.height)

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
            """{"container":{"width":${frame.width},"height":${frame.height}},"elements":[${rows.joinToString(",")}]}""",
        )

        // The frame the numbers above describe, from the same composition.
        //
        // Geometry says the boxes are in the same places and cannot say what is
        // drawn inside them, which is the last question in the overlay
        // comparison. Its web half already learned that a measurement and the
        // frame it describes have to come from one moment — four faults came
        // out of taking them from two — so this writes both or neither.
        //
        // The root, not the bar, so image coordinates and the normalised boxes
        // share an origin and the reader does not have to add an offset back.
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/overlay-frame.png"))
    }

    // The measuring half, lifted out so the test reads as what it asserts. A
    // dump that had grown past forty lines was a dump nobody could see the
    // claim inside.
    /**
     * The measured boxes, normalised against the root's own bounds.
     *
     * The denominator is passed in rather than taken from WIDTH and HEIGHT,
     * because boundsInRoot is in PIXELS and those two constants are dp. At the
     * density this runs under they differ by a fifth, so every position and
     * size in this file came out multiplied by 0.8 — the transport bar,
     * composed fillMaxWidth, reported spanning 0.800 of its container against
     * the browser's 1.000, and every control read a fifth too far left. A
     * normalised value has to be divided by the thing it was measured in.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.measured(width: Float, height: Float): List<String> {
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
                    ""","left":${bounds.left / width}""" +
                    ""","top":${bounds.top / height}""" +
                    ""","width":${bounds.width / width}""" +
                    ""","height":${bounds.height / height}""" +
                    ""","widthPx":${bounds.width.toInt()}""" +
                    ""","heightPx":${bounds.height.toInt()}}"""
            }
        return rows
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
            // No chapters, because the reference item has none.
            //
            // Three of them put chapter-back and chapter-forward into the left
            // group and pushed every control after them along — visible as a
            // native bar two controls longer than the web one, and as a whole
            // run of positions disagreeing from that point rightward. A run
            // that starts at one control and continues to the end is the
            // control, not the layout.
            //
            // The same call as the seek buttons above: the fixture matches what
            // the reference draws, and a control the reference cannot show is
            // measured where it is shown, not here.
            chapters = emptyList(),
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
