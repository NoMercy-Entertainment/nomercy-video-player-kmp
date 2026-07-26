// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.timing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// A script shaped like the ones this player actually gets: two styles, a sign
// overlapping dialogue, a karaoke line, and override tags full of commas.
private val SCRIPT = """
[Script Info]
Title: Fixture
PlayResX: 1920
PlayResY: 1080

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Skeleton Sans,48,&H00FFFFFF,2,10,10,20,1
Style: Sign,Skeleton Display,64,&H00FFCC00,8,10,10,20,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:05.00,0:00:09.00,Default,,0,0,0,,A plain line of dialogue.
Dialogue: 1,0:00:07.50,0:00:12.00,Sign,,0,0,0,,{\pos(960,120)}A sign on the wall
Dialogue: 0,0:00:11.00,0:00:15.00,Default,,0,0,0,,{\k30}Ka{\k25}ra{\k40}o{\k35}ke
Dialogue: 0,0:00:20.00,0:00:24.00,Default,,0,0,0,,{\fad(300,300)}A line that fades in.
""".trimIndent()

class AssTrackModelTest {

    private val model: AssTrackModel = AssTrackModel.parse(SCRIPT)

    @Test
    fun theScriptResolutionIsRead() {
        // Every position in the file is relative to this. Without it the layout
        // is against a guess.
        assertEquals(1920, model.playResX)
        assertEquals(1080, model.playResY)
    }

    @Test
    fun bothStylesAreReadWithTheirFonts() {
        // The font pipeline needs these before the track loads: it has to fetch
        // the faces before libass resolves the families.
        assertEquals(listOf("Default", "Sign"), model.styles.map { it.name })
        assertEquals(listOf("Skeleton Sans", "Skeleton Display"), model.styles.map { it.fontName })
    }

    @Test
    fun alignmentIsReadFromItsNamedColumnRatherThanAFixedPosition() {
        // Style rows are ordered by the Format line, and producers do vary it.
        // Reading by position gives a margin where the alignment should be.
        assertEquals(2, model.styles.first { it.name == "Default" }.alignment)
        assertEquals(8, model.styles.first { it.name == "Sign" }.alignment)
    }

    @Test
    fun textWithCommasSurvivesIntact() {
        // Override tags are full of commas. Splitting the whole row on them
        // truncates every styled line at its first tag.
        val sign: AssCue = model.cues.first { it.text.contains("sign") }

        assertEquals("""{\pos(960,120)}A sign on the wall""", sign.text)
    }

    @Test
    fun timesAreCentisecondsNotMilliseconds() {
        // Reading the fraction as milliseconds makes every cue a tenth of its
        // length, which shows up as subtitles that flash and vanish.
        assertEquals(5_000L, model.cues.first().startMs)
        assertEquals(9_000L, model.cues.first().endMs)
    }

    @Test
    fun karaokeAndFadesAreDynamicAndPlainLinesAreNot() {
        // A renderer can hold one bitmap for a static line and must not for
        // these — they change between boundaries.
        assertTrue(model.cues.first { it.text.contains("Ka") }.isDynamic)
        assertTrue(model.cues.first { it.text.contains("fades") }.isDynamic)
        assertTrue(!model.cues.first().isDynamic, "a plain line was treated as dynamic")
    }

    @Test
    fun aPositionTagAloneIsNotDynamic() {
        // \pos places a cue once. Treating it as moving would render every sign
        // in a film at 24fps for nothing.
        assertTrue(!model.cues.first { it.text.contains("sign") }.isDynamic)
    }

    @Test
    fun overlappingCuesAreBothActive() {
        // A sign at the top and dialogue at the bottom is the everyday case, and
        // returning only the latest would drop one of them.
        val active: List<AssCue> = model.activeCuesAt(8_000L)

        assertEquals(2, active.size, "overlapping cues were not both reported")
    }

    @Test
    fun aCueIsNotActiveOnTheMillisecondItEnds() {
        // Inclusive at the end leaves the last line overlapping the next one by
        // a frame, which reads as a flicker between them.
        assertTrue(model.activeCuesAt(9_000L).none { it.startMs == 5_000L })
        assertTrue(model.activeCuesAt(8_999L).any { it.startMs == 5_000L })
    }

    @Test
    fun aGapBetweenCuesHasNothingActive() {
        assertEquals(emptyList(), model.activeCuesAt(17_000L))
    }

    @Test
    fun cuesAreSortedByStartThenLayer() {
        // Sorting by end time instead is the bug that turns karaoke white: the
        // wrong cue wins the lookup and its style lands on the line being sung.
        assertEquals(model.cues.map { it.startMs }.sorted(), model.cues.map { it.startMs })
    }

    @Test
    fun theNextBoundaryCountsCuesEndingAsWellAsStarting() {
        // A line disappearing is as much a change as one appearing, and a
        // scheduler watching only starts leaves the last line of a scene up
        // until the next one arrives.
        assertEquals(7_500L, model.nextBoundaryAfter(5_000L))
        assertEquals(9_000L, model.nextBoundaryAfter(7_500L))
    }

    @Test
    fun thereIsNoBoundaryAfterTheLastCue() {
        assertNull(model.nextBoundaryAfter(60_000L))
    }

    @Test
    fun anEmptyScriptParsesToAnEmptyModelRatherThanThrowing() {
        // A subtitle file that arrived truncated is a real thing, and a parser
        // that threw would take the player down with it.
        val empty: AssTrackModel = AssTrackModel.parse("")

        assertEquals(emptyList(), empty.cues)
        assertEquals(0, empty.playResX)
    }
}
