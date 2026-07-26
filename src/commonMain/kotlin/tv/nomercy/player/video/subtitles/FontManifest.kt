// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val FONT_EXTENSIONS = setOf(".ttf", ".otf", ".woff", ".woff2", ".ttc", ".eot")
private val PATH_KEYS = listOf("url", "file", "file_name", "path", "href")

// Where a font file lives, from a manifest whose shape is not guaranteed.
//
// A fonts.json in the wild is an array of paths, or an array of objects with the
// path under one of several key names, or a map of name to path, or an object
// with an array somewhere inside it. Every one of those has been seen. Reading
// them all is not indulgence: a manifest this cannot read is a subtitle that
// renders in the wrong typeface with nothing reported, and the shape varies by
// who produced the file rather than by anything under our control.
//
// A faithful port of the Android loader, which has been in front of real
// manifests.
public object FontManifest {

    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    // Font file name, lowercased, to the URL it resolves to.
    //
    // Keyed by file name rather than by the manifest's own key, because the name
    // is what an .ass file's font reference eventually matches against and the
    // manifest key is whatever the producer felt like.
    public fun parse(manifestJson: String, manifestUrl: String): Map<String, String> {
        val root: JsonElement = runCatching { lenient.parseToJsonElement(manifestJson) }.getOrNull()
            ?: return emptyMap()

        val base: String = manifestUrl.substringBeforeLast('/', "") + "/"
        return pathsIn(root)
            .filter(::looksLikeAFont)
            .associate { path -> fileNameOf(path).lowercase() to resolve(path, base) }
    }

    // Everything the manifest could reasonably be, flattened to paths.
    private fun pathsIn(element: JsonElement): List<String> = when (element) {
        is JsonPrimitive -> if (element.isString) listOf(element.content) else emptyList()
        is JsonArray -> element.flatMap(::pathsIn)
        is JsonObject -> pathsInObject(element)
    }

    private fun pathsInObject(obj: JsonObject): List<String> {
        // An object naming its path under one of the known keys is one entry.
        val direct: List<String> = PATH_KEYS.mapNotNull { key ->
            (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        }
        if (direct.isNotEmpty()) return listOf(direct.first())

        // Otherwise it is a container: a map of name to path, or an object with
        // the real list nested somewhere inside.
        return obj.values.flatMap(::pathsIn)
    }

    // Filtered by extension because a manifest carries more than fonts —
    // stylesheets, thumbnails, the subtitle itself — and handing libass a PNG
    // fails in a way that surfaces as a missing glyph.
    private fun looksLikeAFont(path: String): Boolean {
        val name: String = fileNameOf(path).lowercase()
        return FONT_EXTENSIONS.any { name.endsWith(it) }
    }

    private fun fileNameOf(path: String): String =
        path.substringBefore('?').substringBefore('#').substringAfterLast('/')

    // A manifest may list absolute URLs or paths relative to itself. Resolving
    // against the manifest's own location is what makes both work without the
    // caller having to know which it got.
    private fun resolve(path: String, base: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path else base + path.removePrefix("/")
}
