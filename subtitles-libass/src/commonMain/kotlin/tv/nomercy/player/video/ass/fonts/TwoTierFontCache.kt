// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.fonts

import tv.nomercy.player.video.subtitles.AssRenderer
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import tv.nomercy.player.video.subtitles.TtfNameParser

// A font, and every name libass should be told to call it.
//
// Names rather than a name. A face answers to its full name and to its family,
// a style line names whichever the typesetter preferred, and a cache holding
// one of the two silently loses every line that asked for the other.
public data class CachedFont(
    val registerNames: List<String>,
    val bytes: ByteArray,
) {
    /** The best of them, for a caller that can only pass one. */
    public val registerName: String get() = registerNames.first()

    // ByteArray compares by identity, so the generated equals would call two
    // entries holding the same font different — and a cache whose equality lies
    // is a cache that cannot be reasoned about.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedFont) return false

        return registerNames == other.registerNames && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = HASH_FACTOR * registerNames.hashCode() + bytes.contentHashCode()
}

// Fonts, kept in memory and on disk, keyed by what is in them.
//
// An anime series ships the same three fonts attached to every episode. Keyed by
// filename that is twelve copies of the same bytes; keyed by content hash it is
// one, and the second episode registers instantly because the file is already
// there.
//
// Two tiers because the two costs are different. Reading a font off disk is
// milliseconds and parsing its name table is more; holding the parsed result in
// memory turns the second episode of a binge into no work at all, and the disk
// tier is what survives the app being killed.
//
// okio rather than per-platform file APIs, so the eviction and recovery rules
// are written once and can be driven against a filesystem the tests own.
public class TwoTierFontCache(
    private val fileSystem: FileSystem,
    private val cacheDir: Path,
) {

    private val fontsDir: Path = cacheDir / FONTS_DIR

    // L1: the resolved family for a font already looked at this session.
    private val memory: MutableMap<String, CachedFont> = mutableMapOf()

    // What is on disk, by normalised display name.
    private val index: MutableMap<String, IndexEntry> = mutableMapOf()

    // Families already handed to the current renderer, so re-registering costs
    // nothing.
    //
    // One mechanism, not two. This first carried a generation counter as well,
    // and planting a bug showed the counter alone already handled a rebuilt
    // renderer — the clear was doing nothing for correctness. Two mechanisms
    // where one suffices is two things to keep in step.
    private val registered: MutableSet<String> = mutableSetOf()

    private class IndexEntry(
        val path: Path,
        val assName: String?,
        var lastUsedMs: Long,
    )

    public fun put(displayName: String, bytes: ByteArray, assName: String? = null) {
        if (bytes.isEmpty()) return

        // The content, not the name. A font attached to twelve episodes under
        // twelve slightly different filenames is one file here.
        val digest: String = bytes.toByteString().sha256().hex()
        val path: Path = fontsDir / (digest + extensionOf(displayName))

        fileSystem.createDirectories(fontsDir)
        if (!fileSystem.exists(path)) {
            fileSystem.write(path) { write(bytes) }
        }

        val key: String = normalise(displayName)
        index[key] = IndexEntry(path, assName, lastUsedMs = 0L)
        memory[key] = CachedFont(canonicalFamilies(displayName, bytes, assName), bytes)
    }

    public fun get(displayName: String): CachedFont? {
        val key: String = normalise(displayName)
        memory[key]?.let { return it }

        val entry: IndexEntry = index[key] ?: return null
        if (!fileSystem.exists(entry.path)) {
            // The file went away underneath us — a cache clear, a sync, a user
            // with a disk cleaner. Dropping the index entry is what stops the
            // same miss being paid on every episode afterwards.
            index.remove(key)
            return null
        }

        val bytes: ByteArray = fileSystem.read(entry.path) { readByteArray() }
        val font = CachedFont(canonicalFamilies(displayName, bytes, entry.assName), bytes)
        memory[key] = font
        return font
    }

    // Hands the renderer every font it needs, once each, under the name a script
    // will ask for.
    //
    // De-duped by family rather than by file, because two files can carry the
    // same family and libass takes the first — registering both wastes the parse
    // and makes which one wins depend on map ordering.
    //
    // The time is passed in rather than read here. This class owns no clock, so
    // the code that evicts and the code that marks a font used agree about now
    // by construction instead of by both reading a wall clock moments apart.
    public fun registerInto(renderer: AssRenderer, requiredFamilies: Set<String>, nowMs: Long) {
        for (name in index.keys.toList()) {
            val font: CachedFont = get(name) ?: continue

            // Wanted if ANY of the names the face answers to was asked for. The
            // test used to run against one name, so a script asking for the
            // family of a font whose full name differs skipped the font it was
            // asking for.
            val wanted: Boolean = requiredFamilies.isEmpty() || font.registerNames.any { candidate ->
                requiredFamilies.any { normalise(it) == normalise(candidate) }
            }
            if (!wanted) continue

            val fresh: List<String> = font.registerNames.filter { registered.add(normalise(it)) }
            if (fresh.isEmpty()) continue

            for (family in fresh) renderer.addFont(family, font.bytes)
            index[name]?.lastUsedMs = nowMs
        }
    }

    // A renderer that was torn down needs every font again, so the record of
    // what it already has must not outlive it. Forgetting this is a title whose
    // first styled line renders unstyled after a rotation.
    public fun rendererChanged() {
        registered.clear()
    }

    // Thirty days since last use. A font nobody has needed in a month is a show
    // that was watched and finished, and keeping it costs the disk of a series
    // nobody is returning to.
    public fun evictStale(nowMs: Long, maxAgeMs: Long = THIRTY_DAYS_MS) {
        for ((key, entry) in index.entries.toList()) {
            if (nowMs - entry.lastUsedMs < maxAgeMs) continue

            if (fileSystem.exists(entry.path)) fileSystem.delete(entry.path)
            index.remove(key)
            memory.remove(key)
        }
    }

    // Files with a hash name and no index entry are adopted rather than deleted.
    //
    // That is what a force-kill leaves behind: the font was written and the
    // process died before the index reached disk. Deleting it throws away a font
    // that is already there and makes the next play re-download it; adopting it
    // costs a name parse and gets the cache back.
    public fun adoptOrphans() {
        if (!fileSystem.exists(fontsDir)) return

        val known: Set<Path> = index.values.map { it.path }.toSet()
        for (path in fileSystem.list(fontsDir)) {
            if (path in known || !isContentAddressed(path.name)) continue

            val bytes: ByteArray = fileSystem.read(path) { readByteArray() }
            if (bytes.isEmpty()) continue

            val family: String = TtfNameParser.extractFontName(bytes, path.name.substringBefore('.'))
            index[normalise(family)] = IndexEntry(path, assName = null, lastUsedMs = 0L)
        }
    }

    public fun families(): Set<String> = index.keys.toSet()

    // The ASS-declared name first when there is one, then the names the font
    // reports about itself.
    //
    // A script says which family it wants, and that string is what libass will
    // look for — so it leads. It no longer REPLACES the font's own names: a
    // declaration answers the line that carried it and says nothing about the
    // other lines in the same script, which is how one declared name used to
    // hide the family every other style asked for.
    private fun canonicalFamilies(displayName: String, bytes: ByteArray, assName: String?): List<String> {
        val declared: String? = assName?.takeIf { it.isNotBlank() }
        val fallback: String = TtfNameParser.fallbackNameFor(displayName)
        val reported: List<String> = TtfNameParser.extractFontNames(bytes, fallback)

        // The filename is a last resort, not an alias. It is what the parser
        // hands back when the bytes carry no name table at all, and registering
        // a face under it as WELL as under a declared name means two files
        // declaring one family register four names — three of which resolve to
        // nothing.
        val real: List<String> = if (reported == listOf(fallback)) emptyList() else reported

        return (listOfNotNull(declared) + real).distinct().ifEmpty { listOf(fallback) }
    }

    private fun extensionOf(displayName: String): String {
        val dot: Int = displayName.lastIndexOf('.')

        return if (dot in 0 until displayName.length - 1) displayName.substring(dot).lowercase() else DEFAULT_EXT
    }

    // Case and surrounding space only. Anything cleverer — stripping weights,
    // collapsing hyphens — would merge "Roboto Light" into "Roboto" and render
    // half a show in the wrong weight.
    private fun normalise(name: String): String = name.trim().lowercase()

    private fun isContentAddressed(name: String): Boolean {
        val digest: String = name.substringBefore('.')

        return digest.length == SHA256_HEX_LENGTH && digest.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

private const val FONTS_DIR = "fonts"
private const val DEFAULT_EXT = ".ttf"
private const val SHA256_HEX_LENGTH = 64
private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
private const val HASH_FACTOR = 31
