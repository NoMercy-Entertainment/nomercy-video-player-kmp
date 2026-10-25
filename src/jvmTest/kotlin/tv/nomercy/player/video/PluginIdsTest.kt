// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A plugin id is a plain name. A vendor prefix (`vendor:name`) never appears on
// an id this repository declares, in a shipped plugin or in a test fixture.
//
// Reads every `val id: String = "..."` in the sources, so a plugin added later
// is covered without anyone remembering to list it here.
class PluginIdsTest {

    private val literalId = Regex("""\bval id: String = "([^"]*)"""")

    private val sourceRoots = listOf("src", "subtitles-libass/src", "ui-compose/src")

    private fun declaredIds(): List<Pair<String, String>> =
        sourceRoots.flatMap { path ->
            val root = File(path)
            assertTrue(root.isDirectory, "no sources at ${root.absolutePath}")

            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "PluginIdsTest.kt" }
                .flatMap { file -> literalId.findAll(file.readText()).map { match -> file.name to match.groupValues[1] } }
                .toList()
        }

    @Test
    fun theScanFindsTheShippedPlugins() {
        val ids = declaredIds().map { it.second }
        assertTrue("subtitles-ass" in ids || "nomercy:subtitles-ass" in ids, "SubtitlePlugin was not found: $ids")
        assertTrue(ids.size >= 15, "expected at least 15 declared ids, found ${ids.size}: $ids")
    }

    @Test
    fun noDeclaredPluginIdCarriesAVendorPrefix() {
        val prefixed = declaredIds()
            .filter { (_, id) -> ':' in id }
            .map { (fileName, id) -> "$fileName: $id" }
        assertEquals(emptyList(), prefixed)
    }
}
