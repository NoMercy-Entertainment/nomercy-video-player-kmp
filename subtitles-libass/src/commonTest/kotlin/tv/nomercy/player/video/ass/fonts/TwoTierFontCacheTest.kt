// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.fonts

import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.TtfNameParser
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// A renderer that remembers what it was told instead of drawing.
//
// The native calls are the part that needs a device; which families reach them,
// and how many times, is the part that decides whether subtitles come out in
// the right typeface.
private class RecordingRenderer : AssRenderer {
    val added: MutableList<String> = mutableListOf()

    override fun addFont(name: String, data: ByteArray) {
        added += name
    }

    override fun clearFonts(): Unit = Unit

    override fun loadTrack(assContent: String) = Unit
    override fun storageSize(width: Int, height: Int) = Unit

    override fun storageSize(): AssSize? = null
    override fun frameSize(width: Int, height: Int) = Unit
    override fun render(timeMillis: Long): AssFrame? = null
    override fun release() = Unit
}

// The cache, driven against a filesystem the test owns.
//
// A real directory would race whatever else is writing to temp and would make
// the eviction rules untestable — thirty days cannot be waited for. A fake
// filesystem lets the rules be driven to their edges, which is where they are
// wrong.
class TwoTierFontCacheTest {

    private val fs = FakeFileSystem()
    private val cache = TwoTierFontCache(fs, "/cache".toPath())

    // Not a real font. These tests are about the cache's bookkeeping — content
    // addressing, de-duplication, eviction, recovery — and the name parser has
    // its own gate against real fonts next door.
    private fun bytes(seed: Int, size: Int = 64): ByteArray = ByteArray(size) { (seed + it).toByte() }

    @Test
    fun aFontComesBackUnderTheNameAScriptWillAskFor() {
        cache.put("whatever-the-file-was-called.ttf", bytes(1), assName = "Gandhi Sans")

        assertEquals("Gandhi Sans", cache.get("whatever-the-file-was-called.ttf")?.registerName)
    }

    @Test
    fun theSameFontUnderTwoNamesIsStoredOnce() {
        // An anime series attaches the same three fonts to every episode, often
        // under slightly different filenames. Keyed by name that is twelve
        // copies on disk; keyed by content it is one.
        val shared: ByteArray = bytes(7)
        cache.put("ep01-font.ttf", shared, assName = "Shared")
        cache.put("ep02-font.ttf", shared, assName = "Shared")

        val files: Int = fs.list("/cache/fonts".toPath()).size

        assertEquals(1, files, "the same bytes were written twice")
    }

    @Test
    fun differentFontsAreStoredSeparately() {
        cache.put("a.ttf", bytes(1), assName = "A")
        cache.put("b.ttf", bytes(2), assName = "B")

        assertEquals(2, fs.list("/cache/fonts".toPath()).size)
    }

    @Test
    fun aFamilyIsRegisteredOnceHoweverManyFilesCarryIt() {
        // Two files can declare the same family, and libass takes the first.
        // Registering both wastes the work and makes which one wins depend on
        // map ordering.
        cache.put("regular.ttf", bytes(1), assName = "Gandhi Sans")
        cache.put("also-regular.ttf", bytes(2), assName = "Gandhi Sans")
        val renderer = RecordingRenderer()

        cache.registerInto(renderer, requiredFamilies = emptySet(), nowMs = 1_000)

        assertEquals(listOf("Gandhi Sans"), renderer.added)
    }

    @Test
    fun registeringTwiceIntoTheSameRendererIsFree() {
        cache.put("a.ttf", bytes(1), assName = "A")
        val renderer = RecordingRenderer()

        cache.registerInto(renderer, emptySet(), nowMs = 1_000)
        cache.registerInto(renderer, emptySet(), nowMs = 2_000)

        assertEquals(1, renderer.added.size, "the same font was pushed twice")
    }

    @Test
    fun aRebuiltRendererGetsEveryFontAgain() {
        // The failure this guards is a title whose first styled line renders in
        // the wrong typeface after the surface was recreated — a rotation, a
        // return from background — because the cache still believed the old
        // renderer's registrations counted.
        cache.put("a.ttf", bytes(1), assName = "A")
        cache.registerInto(RecordingRenderer(), emptySet(), nowMs = 1_000)

        cache.rendererChanged()
        val rebuilt = RecordingRenderer()
        cache.registerInto(rebuilt, emptySet(), nowMs = 2_000)

        assertEquals(listOf("A"), rebuilt.added, "a rebuilt renderer was left without its fonts")
    }

    @Test
    fun onlyTheFamiliesAScriptAsksForAreRegistered() {
        // A cache holding a season's worth of fonts should not push all of them
        // at an episode using two. Each one costs a parse and native memory.
        cache.put("used.ttf", bytes(1), assName = "Used")
        cache.put("unused.ttf", bytes(2), assName = "Unused")
        val renderer = RecordingRenderer()

        cache.registerInto(renderer, requiredFamilies = setOf("Used"), nowMs = 1_000)

        assertEquals(listOf("Used"), renderer.added)
    }

    @Test
    fun aFontNobodyHasNeededForAMonthIsDropped() {
        cache.put("old.ttf", bytes(1), assName = "Old")
        cache.registerInto(RecordingRenderer(), emptySet(), nowMs = 0)

        cache.evictStale(nowMs = THIRTY_ONE_DAYS_MS)

        assertNull(cache.get("old.ttf"))
        assertTrue(fs.list("/cache/fonts".toPath()).isEmpty(), "the file outlived its index entry")
    }

    @Test
    fun aFontUsedYesterdayIsKept() {
        cache.put("recent.ttf", bytes(1), assName = "Recent")
        cache.registerInto(RecordingRenderer(), emptySet(), nowMs = THIRTY_ONE_DAYS_MS)

        cache.evictStale(nowMs = THIRTY_ONE_DAYS_MS + ONE_DAY_MS)

        assertEquals("Recent", cache.get("recent.ttf")?.registerName)
    }

    @Test
    fun aFileLeftBehindByAForceKillIsAdoptedRatherThanDeleted() {
        // The font was written and the process died before the index reached
        // disk. Deleting it throws away something already downloaded and makes
        // the next play fetch it again; adopting it costs one name parse.
        val orphan = "/cache/fonts/${"a".repeat(SHA256_HEX)}.ttf".toPath()
        fs.createDirectories(orphan.parent!!)
        fs.write(orphan) { write(bytes(3)) }

        cache.adoptOrphans()

        assertTrue(cache.families().isNotEmpty(), "the orphan was not adopted")
        assertTrue(fs.exists(orphan), "the orphan was deleted rather than adopted")
    }

    @Test
    fun aFileThatIsNotContentAddressedIsLeftAlone() {
        // The cache directory is not necessarily only ours, and a file that does
        // not look like something this class wrote is not this class's to adopt
        // or to delete.
        val stranger = "/cache/fonts/notes.txt".toPath()
        fs.createDirectories(stranger.parent!!)
        fs.write(stranger) { write(bytes(4)) }

        cache.adoptOrphans()

        assertTrue(cache.families().isEmpty(), "a stray file was adopted as a font")
        assertTrue(fs.exists(stranger))
    }

    @Test
    fun aFontDeletedUnderneathUsIsAMissRatherThanACrash() {
        // A disk cleaner, a sync, a user clearing storage. Reading a path that
        // is gone would throw on the next episode of whatever they were
        // watching.
        cache.put("gone.ttf", bytes(1), assName = "Gone")
        val path = fs.list("/cache/fonts".toPath()).first()
        fs.delete(path)

        // The memory tier still holds it, so this proves the disk path — the
        // second lookup after a cache that has been rebuilt from its index.
        val cold = TwoTierFontCache(fs, "/cache".toPath())
        cold.adoptOrphans()

        assertNull(cold.get("gone.ttf"))
    }

    @Test
    fun anEmptyFontIsRefused() {
        // A zero-byte attachment is a download that failed. Storing it means a
        // hash of nothing in the index and a font that registers as empty.
        cache.put("empty.ttf", ByteArray(0), assName = "Empty")

        assertNull(cache.get("empty.ttf"))
        assertTrue(cache.families().isEmpty())
    }
}

private const val SHA256_HEX = 64
private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
private const val THIRTY_ONE_DAYS_MS = 31L * ONE_DAY_MS
