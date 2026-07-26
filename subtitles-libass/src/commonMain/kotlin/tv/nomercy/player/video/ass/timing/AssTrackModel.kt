// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.timing

// A style row, for the two fields anything outside libass needs.
//
// Not the whole style. libass renders it, and a model that duplicated colours
// and outlines would be a second renderer's worth of state that nothing reads.
// The font name is here because the font pipeline needs to know what to fetch
// before the track loads, and the alignment because a chrome deciding where to
// put its own controls needs to know where the subtitles are going.
public data class AssStyle(
    val name: String,
    val fontName: String,
    val alignment: Int,
)

public data class AssCue(
    val index: Int,
    val layer: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    // Karaoke, transforms, movement and fades all change what is on screen
    // between one boundary and the next, so a renderer cannot cache them for the
    // cue's duration the way it can a static line.
    val isDynamic: Boolean,
)

// The cue and timing brain, parsed from the script rather than from libass.
//
// The Android original read libass's own event array, which meant every question
// about timing crossed the JNI boundary and could only be asked on a platform
// with a renderer attached. Parsing the text gives the same answers on all four
// surfaces, needs no native round trip, and can be tested without libass at all.
public class AssTrackModel private constructor(
    public val playResX: Int,
    public val playResY: Int,
    public val styles: List<AssStyle>,
    public val cues: List<AssCue>,
) {

    // Every cue on screen at this moment.
    //
    // Sorted by start time and scanned from the first cue that could be open —
    // not filtered by end time alone. Cues overlap constantly in styled
    // subtitles: a sign at the top and dialogue at the bottom, karaoke on two
    // lines. Returning only the latest would drop one of them.
    public fun activeCuesAt(timeMs: Long): List<AssCue> =
        cues.filter { timeMs >= it.startMs && timeMs < it.endMs }

    // When what is on screen next changes.
    //
    // Both edges count. A cue ending is as much a change as one starting, and a
    // scheduler that only looked at starts would leave the last line of a scene
    // on screen until the next one arrived.
    public fun nextBoundaryAfter(timeMs: Long): Long? =
        cues.flatMap { listOf(it.startMs, it.endMs) }
            .filter { it > timeMs }
            .minOrNull()

    public companion object {

        public fun parse(assText: String): AssTrackModel {
            var playResX = 0
            var playResY = 0
            val styles: MutableList<AssStyle> = mutableListOf()
            val cues: MutableList<AssCue> = mutableListOf()
            var styleFormat: List<String> = emptyList()
            var eventFormat: List<String> = emptyList()

            for (raw in assText.lineSequence()) {
                val line: String = raw.trim()
                when {
                    line.startsWith("PlayResX:", ignoreCase = true) ->
                        playResX = line.substringAfter(':').trim().toIntOrNull() ?: 0

                    line.startsWith("PlayResY:", ignoreCase = true) ->
                        playResY = line.substringAfter(':').trim().toIntOrNull() ?: 0

                    line.startsWith("Format:", ignoreCase = true) -> {
                        val fields: List<String> = line.substringAfter(':').split(',').map { it.trim() }
                        // Which Format row this is depends on the section it is
                        // in, and the sections are told apart by what follows.
                        // Keeping both and choosing at use is simpler than
                        // tracking section state through a parser this small.
                        if (fields.any { it.equals("Fontname", ignoreCase = true) }) styleFormat = fields
                        if (fields.any { it.equals("Start", ignoreCase = true) }) eventFormat = fields
                    }

                    line.startsWith("Style:", ignoreCase = true) ->
                        parseStyle(line, styleFormat)?.let(styles::add)

                    line.startsWith("Dialogue:", ignoreCase = true) ->
                        parseDialogue(line, eventFormat, cues.size)?.let(cues::add)
                }
            }

            return AssTrackModel(
                playResX = playResX,
                playResY = playResY,
                styles = styles,
                // By start then layer, which is the order they are drawn in and
                // the order a scheduler walks. Sorting by end time instead is
                // the bug that turns karaoke white: the wrong cue wins the
                // lookup and its style is applied to the line being sung.
                cues = cues.sortedWith(compareBy({ it.startMs }, { it.layer })),
            )
        }

        // Karaoke, transform, movement, fade. Anything here changes between
        // boundaries, so a renderer cannot hold one frame for the cue.
        private val DYNAMIC = Regex("""\\(?:k[fo]?\d|t\(|move\(|fad[e]?\()""", RegexOption.IGNORE_CASE)

        private fun parseStyle(line: String, format: List<String>): AssStyle? {
            val values: List<String> = line.substringAfter(':').split(',').map { it.trim() }
            if (values.isEmpty()) return null

            fun at(field: String): String? =
                format.indexOfFirst { it.equals(field, ignoreCase = true) }
                    .takeIf { it >= 0 && it < values.size }
                    ?.let(values::get)

            return AssStyle(
                name = at("Name") ?: values.firstOrNull().orEmpty(),
                fontName = at("Fontname").orEmpty(),
                alignment = at("Alignment")?.toIntOrNull() ?: 0,
            )
        }

        private fun parseDialogue(line: String, format: List<String>, index: Int): AssCue? {
            val body: String = line.substringAfter(':')
            val textField: Int = format.indexOfFirst { it.equals("Text", ignoreCase = true) }
                .takeIf { it >= 0 } ?: LAST_FIELD

            // Split only up to the text field. The text itself contains commas
            // constantly — override tags are full of them — and splitting it
            // would truncate every styled line at its first tag.
            val values: List<String> = body.split(',', limit = textField + 1).map { it.trim() }
            if (values.size <= textField) return null

            fun at(field: String): String? =
                format.indexOfFirst { it.equals(field, ignoreCase = true) }
                    .takeIf { it >= 0 && it < values.size }
                    ?.let(values::get)

            val start: Long = parseTime(at("Start") ?: return null) ?: return null
            val end: Long = parseTime(at("End") ?: return null) ?: return null
            val text: String = values[textField]

            return AssCue(
                index = index,
                layer = at("Layer")?.toIntOrNull() ?: 0,
                startMs = start,
                endMs = end,
                text = text,
                isDynamic = DYNAMIC.containsMatchIn(text),
            )
        }

        // h:mm:ss.cc — hours unpadded, centiseconds not milliseconds. Reading
        // the fraction as milliseconds makes every cue a tenth of its length.
        internal fun parseTime(value: String): Long? {
            val parts: List<String> = value.split(':')
            if (parts.size != TIME_PARTS) return null

            val hours: Long = parts[0].trim().toLongOrNull() ?: return null
            val minutes: Long = parts[1].trim().toLongOrNull() ?: return null
            val seconds: Double = parts[2].trim().toDoubleOrNull() ?: return null

            return hours * MS_PER_HOUR + minutes * MS_PER_MINUTE + (seconds * MS_PER_SECOND).toLong()
        }

        private const val TIME_PARTS = 3
        private const val LAST_FIELD = 9
        private const val MS_PER_SECOND = 1_000
        private const val MS_PER_MINUTE = 60 * MS_PER_SECOND
        private const val MS_PER_HOUR = 60 * MS_PER_MINUTE
    }
}
