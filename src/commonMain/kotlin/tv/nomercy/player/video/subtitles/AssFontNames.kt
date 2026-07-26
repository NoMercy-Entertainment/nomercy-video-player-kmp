// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

private const val STYLE_PREFIX_LENGTH = 6
private val FONT_OVERRIDE = Regex("""\\fn([^\\}]+)""")

// Which fonts an .ass file actually asks for.
//
// This has to happen before the track is handed to the renderer: libass resolves
// a font at the moment it draws, and a font added afterwards is a cue that
// rendered in the wrong typeface with nothing reported. So the subtitle is read
// first, its fonts fetched, and only then does the track load.
//
// A faithful port of the Android extractor rather than a fresh reading of the
// spec, because the Android one has been in front of real files for a year and
// this must not disagree with it.
public object AssFontNames {

    // Fonts are named in two places and both matter. A style names one for every
    // line that uses it; an inline \fn override names one for a single line, and
    // a file whose styles all say Arial can still need three others.
    public fun parse(content: String): List<String> {
        val fonts: MutableSet<String> = linkedSetOf()
        var section: Section = Section.Other

        for (raw in content.lines()) {
            val line: String = raw.trim()
            val heading: Section? = sectionOf(line)
            if (heading != null) {
                section = heading
                continue
            }
            fonts.addAll(fontsIn(line, section))
        }

        return fonts.toList()
    }

    private enum class Section { Styles, Events, Other }

    private fun sectionOf(line: String): Section? = when {
        line.equals("[V4+ Styles]", ignoreCase = true) -> Section.Styles
        line.equals("[V4 Styles]", ignoreCase = true) -> Section.Styles
        line.equals("[Events]", ignoreCase = true) -> Section.Events
        line.startsWith("[") && line.endsWith("]") -> Section.Other
        else -> null
    }

    private fun fontsIn(line: String, section: Section): List<String> = when {
        section == Section.Styles && line.startsWith("Style:", ignoreCase = true) ->
            listOfNotNull(styleFont(line))

        section == Section.Events && isEventLine(line) ->
            FONT_OVERRIDE.findAll(line).mapNotNull { keep(it.groupValues[1]) }.toList()

        else -> emptyList()
    }

    // Style: Name,Fontname,Fontsize,... — the font is the second field, and a
    // style line with fewer is malformed rather than fontless.
    private fun styleFont(line: String): String? {
        val fields: List<String> = line.substring(STYLE_PREFIX_LENGTH).split(',')
        if (fields.size < 2) return null
        return keep(fields[1])
    }

    private fun isEventLine(line: String): Boolean =
        line.startsWith("Dialogue:", ignoreCase = true) || line.startsWith("Comment:", ignoreCase = true)

    // Arial is skipped on purpose. It is libass's own fallback and every player
    // already has it, so fetching it costs a request that can only return what
    // was going to be used anyway.
    private fun keep(candidate: String): String? {
        val name: String = candidate.trim()
        return if (name.isEmpty() || name == "Arial") null else name
    }
}
