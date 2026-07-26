// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MANIFEST_URL = "https://media.example.test/shows/1/fonts/fonts.json"

// Every shape here has been seen in a real manifest. The variation is not
// hypothetical and it is not under our control: it depends on who produced the
// file.
class FontManifestTest {

    @Test
    fun anArrayOfPathsResolvesAgainstTheManifestsOwnLocation() {
        val fonts = FontManifest.parse("""["Roboto-Regular.ttf", "BebasNeue.otf"]""", MANIFEST_URL)

        assertEquals(
            "https://media.example.test/shows/1/fonts/Roboto-Regular.ttf",
            fonts["roboto-regular.ttf"],
        )
        assertEquals(2, fonts.size)
    }

    @Test
    fun anAbsoluteUrlIsLeftAlone() {
        val fonts = FontManifest.parse("""["https://cdn.example.test/f/Roboto.ttf"]""", MANIFEST_URL)

        assertEquals("https://cdn.example.test/f/Roboto.ttf", fonts["roboto.ttf"])
    }

    @Test
    fun anArrayOfObjectsIsReadUnderAnyOfTheKeysThatCarryAPath() {
        val json = """
            [
              {"name": "Roboto", "url": "Roboto.ttf"},
              {"name": "Bebas", "file": "Bebas.otf"},
              {"name": "Noto",  "file_name": "Noto.ttf"},
              {"name": "Inter", "path": "Inter.woff2"},
              {"name": "Lato",  "href": "Lato.ttc"}
            ]
        """.trimIndent()

        val fonts = FontManifest.parse(json, MANIFEST_URL)

        assertEquals(5, fonts.size)
        assertTrue(fonts.containsKey("inter.woff2"))
        assertTrue(fonts.containsKey("lato.ttc"))
    }

    @Test
    fun aMapOfNameToPathIsReadToo() {
        val fonts = FontManifest.parse("""{"Roboto": "Roboto.ttf", "Bebas": "Bebas.otf"}""", MANIFEST_URL)

        assertEquals(2, fonts.size)
        assertEquals("https://media.example.test/shows/1/fonts/Bebas.otf", fonts["bebas.otf"])
    }

    @Test
    fun anObjectWithTheListNestedInsideIsFound() {
        val fonts = FontManifest.parse("""{"version": 2, "fonts": ["Roboto.ttf"]}""", MANIFEST_URL)

        assertEquals(1, fonts.size)
        assertTrue(fonts.containsKey("roboto.ttf"))
    }

    @Test
    fun anythingThatIsNotAFontIsLeftOut() {
        val json = """["Roboto.ttf", "poster.png", "subtitles.ass", "style.css"]"""

        // Handing libass a PNG fails in a way that surfaces as a missing glyph,
        // three layers away from the manifest that caused it.
        assertEquals(setOf("roboto.ttf"), FontManifest.parse(json, MANIFEST_URL).keys)
    }

    @Test
    fun aQueryStringDoesNotHideTheExtension() {
        val fonts = FontManifest.parse("""["Roboto.ttf?v=3&token=abc"]""", MANIFEST_URL)

        // Signed URLs carry one, and matching on the raw string would drop every
        // font on a server that signs.
        assertEquals(setOf("roboto.ttf"), fonts.keys)
        assertTrue(fonts.getValue("roboto.ttf").endsWith("Roboto.ttf?v=3&token=abc"))
    }

    @Test
    fun aLeadingSlashDoesNotProduceADoubleSlash() {
        val fonts = FontManifest.parse("""["/Roboto.ttf"]""", MANIFEST_URL)

        assertEquals("https://media.example.test/shows/1/fonts/Roboto.ttf", fonts["roboto.ttf"])
    }

    @Test
    fun malformedJsonIsEmptyRatherThanAnException() {
        // The manifest comes off whatever produced the media, which is not a
        // place with a schema. An empty result is the caller's cue to render
        // with fallbacks.
        assertEquals(emptyMap(), FontManifest.parse("not json at all", MANIFEST_URL))
        assertEquals(emptyMap(), FontManifest.parse("", MANIFEST_URL))
    }

    @Test
    fun anEmptyManifestIsEmpty() {
        assertEquals(emptyMap(), FontManifest.parse("[]", MANIFEST_URL))
        assertEquals(emptyMap(), FontManifest.parse("{}", MANIFEST_URL))
    }
}
