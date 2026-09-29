// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Every 64-bit libass this aar ships must be linked for 16 KB pages.
//
// A library linked for 4 KB pages cannot be mapped on a 16 KB-page device, so
// libass fails to load there, and Google Play refuses the whole app bundle
// ("Validation of uploaded file failed"). The prebuilt in jniLibs once arrived
// with a 4096 alignment and nothing noticed until the Play upload failed.
// Play applies the rule to 64-bit ABIs only.
class NativeLibraryAlignmentTest {

    @Test
    fun everySixtyFourBitLibraryIsSixteenKilobyteAligned() {
        val jniLibs = File("src/androidMain/jniLibs")
        val libraries = listOf("arm64-v8a", "x86_64").map { File(jniLibs, "$it/libass.so") }
        for (library in libraries) {
            assertTrue(library.isFile, "missing ${library.path}")
            val alignment = smallestLoadAlignment(library.readBytes())
            assertTrue(alignment >= 16384, "${library.path} is aligned to $alignment, needs 16384")
        }
    }

    // The same prebuilt came from a local build rather than the nomercy-libass
    // release, which is how the 4 KB linking slipped in. Every file has to be the
    // exact release file libass-android.sha256 names.
    @Test
    fun everyLibraryIsTheReleaseFileTheManifestNames() {
        val expected = File("libass-android.sha256").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line -> line.split(Regex("\\s+")).let { it[1] to it[0] } }
        val shipped = File("src/androidMain/jniLibs").listFiles().orEmpty().filter { File(it, "libass.so").isFile }
        assertEquals(expected.keys, shipped.map { it.name }.toSet())
        for (abi in shipped) {
            val digest = MessageDigest.getInstance("SHA-256").digest(File(abi, "libass.so").readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected[abi.name], digest, "${abi.name}/libass.so is not the release file")
        }
    }

    // The smallest p_align over the PT_LOAD segments of a 64-bit little-endian ELF.
    private fun smallestLoadAlignment(bytes: ByteArray): Long {
        val elf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val headerOffset = elf.getLong(0x20)
        val headerSize = elf.getShort(0x36).toInt()
        val headerCount = elf.getShort(0x38).toInt()
        return (0 until headerCount)
            .map { headerOffset.toInt() + it * headerSize }
            .filter { elf.getInt(it) == PT_LOAD }
            .minOf { elf.getLong(it + 0x30) }
    }

    private companion object {
        const val PT_LOAD = 1
    }
}
