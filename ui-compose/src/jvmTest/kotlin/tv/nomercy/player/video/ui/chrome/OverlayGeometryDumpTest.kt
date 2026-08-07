// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsNode
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where every tagged overlay element actually lands, and a picture of them,
 * written where a diff can read both.
 *
 * This is the other half of the pixel comparison. `scripts/web-overlay-shot.mjs`
 * measures the web player on the running page and photographs it in the same
 * moment; this does the same to the composed native chrome, so the two files
 * subtract.
 *
 * A dump rather than a set of assertions, because the reference numbers live in
 * a JSON captured from a browser and belong beside it, not scattered through
 * Kotlin constants that two files can agree on while both are wrong. The
 * assertion here is only that the chrome composed at all — an empty dump that
 * silently compares as "no differences" is the failure mode this exists inside.
 *
 * It composes the WHOLE chrome, not the transport bar.
 *
 * The bar alone was 21 tags out of the 30 the counterpart map pairs, so the
 * comparison downstream covered the bar and read as if it covered the overlay:
 * the top bar, the scrubber, the buffering spinner and the subtitle safe zone
 * were never measured on this side at all. A subtitle drawn over the transport
 * bar in a real capture is the kind of thing that leaves — the web confines its
 * cues to a safe zone ending at 0.757 of the container, and nothing here was
 * looking at that number.
 *
 * The tags are ENUMERATED from the composed tree rather than listed. A list has
 * to be edited every time the chrome gains an element, and the failure mode of
 * forgetting is silence: the new element is simply not compared, and the report
 * still says everything matched.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayGeometryDumpTest {

    @Test
    fun theComposedChromeReportsItsGeometry() = runComposeUiTest {
        val player = NMVideoPlayer(RecordingVideoBackend())

        setContent {
            LaunchedEffect(player) {
                player.setup(PlayerConfig())
                player.queue(listOf(ChromeTestItem(), ChromeTestItem(id = "two")))
                player.load(ChromeTestItem())
                // Playing, because a paused chrome holds itself open and half
                // the overlay's behaviour is about what happens when it does
                // not.
                player.play()
            }
            Box(
                // Black behind it, because the chrome is white and it always
                // sits over video. Without a backdrop the capture is white on
                // white and the frame comes back holding nothing but the two
                // timestamps, which are the only grey things on the bar. An
                // overlay photographed against nothing is not the overlay.
                modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp).background(Color.Black),
            ) {
                VideoChrome(player, FormFactor.Desktop, buttons = EVERY_BUTTON)
            }
        }
        waitForIdle()

        // Woken, or there is nothing to measure.
        //
        // The chrome autohides, and a capture of a player nobody has touched is
        // a bare video surface — which reads exactly like a chrome that was
        // never ported, and has been reported as one.
        onNodeWithTag(DESKTOP_CHROME_TAG).performMouseInput { moveTo(center) }
        waitForIdle()

        // The rectangle everything below is normalised against, measured rather
        // than assumed. boundsInRoot is in PIXELS and WIDTH/HEIGHT are dp; at
        // this density they differ by a fifth, so dividing by the constants put
        // every position and size out by 0.8 — the transport bar, composed
        // fillMaxWidth, reported spanning 0.800 of its container against the
        // browser's 1.000.
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
            rows.any { it.contains("\"$TRANSPORT_BAR_TAG\"") } && rows.any { it.contains("\"$PLAY_PAUSE_TAG\"") },
            "the chrome did not compose — an empty dump compares clean against anything",
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
        // picture it describes have to come from one moment — four faults came
        // out of taking them from two — so this writes both or neither.
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/overlay-frame.png"))
    }

    /**
     * Every tagged node in the composed tree, normalised against the root.
     *
     * Walked rather than looked up by name. The list this replaced held 21 tags
     * and the chrome assigns more than that, so nine mapped elements were never
     * measured and the report counted the ones it had as the whole overlay.
     */
    private fun ComposeUiTest.measured(width: Float, height: Float): List<String> {
        val rows: MutableList<String> = mutableListOf()

        fun visit(node: SemanticsNode) {
            val tag: String? = node.config.getOrNull(SemanticsProperties.TestTag)
            if (tag != null && tag.startsWith("nm-")) {
                val bounds = node.boundsInRoot
                if (bounds.width >= 1f && bounds.height >= 1f) {
                    rows += """{"name":"$tag"""" +
                        ""","left":${bounds.left / width}""" +
                        ""","top":${bounds.top / height}""" +
                        ""","width":${bounds.width / width}""" +
                        ""","height":${bounds.height / height}""" +
                        ""","widthPx":${bounds.width.toInt()}""" +
                        ""","heightPx":${bounds.height.toInt()}}"""
                }
            }
            for (child in node.children) visit(child)
        }

        visit(onRoot().fetchSemanticsNode())
        return rows
    }

    private companion object {
        const val WIDTH = 1280
        const val HEIGHT = 720

        /**
         * Every optional control asked for.
         *
         * Nine of the sixteen default to OFF — a consumer opts in to subtitles,
         * quality, speed, playlist, theater and the rest — so a bar built from
         * the defaults draws about half the reference's controls and a geometry
         * comparison over it silently covers half the overlay.
         *
         * Seek buttons stay off, because the reference page does not draw them.
         * Turning them on packed two extra controls into the left group and
         * shifted every control after them — four findings about a fixture
         * asking for more than the thing it is measured against.
         */
        val EVERY_BUTTON = ChromeButtons(
            subtitles = true,
            audio = true,
            quality = true,
            speed = true,
            aspectRatio = true,
            playlist = true,
            theater = true,
            pictureInPicture = true,
        )
    }
}
