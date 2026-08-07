// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

// A bounds-checked window onto font bytes.
//
// Every read here can fall off the end, because the bytes arrive as an
// attachment inside a media file and an attachment can be truncated, empty, or
// not a font at all. Threading that check through the parser made every
// function a chain of null tests; keeping it in one place lets the parser read
// like the format specification it implements.
private class FontBytes(private val bytes: ByteArray) {

    val size: Int get() = bytes.size

    fun uint16(at: Int): Int {
        if (at < 0 || at + UINT16_BYTES > bytes.size) return MISSING

        return ((bytes[at].toInt() and BYTE_MASK) shl BITS_PER_BYTE) or (bytes[at + 1].toInt() and BYTE_MASK)
    }

    fun int32(at: Int): Int {
        if (at < 0 || at + INT32_BYTES > bytes.size) return MISSING

        var value = 0
        for (index in 0 until INT32_BYTES) {
            value = (value shl BITS_PER_BYTE) or (bytes[at + index].toInt() and BYTE_MASK)
        }
        return value
    }

    fun slice(at: Int, length: Int): ByteArray? {
        if (!holds(at, length)) return null

        return bytes.copyOfRange(at, at + length)
    }

    private fun holds(at: Int, length: Int): Boolean = at >= 0 && length > 0 && at + length <= bytes.size
}

// One entry in the name table, already decided about.
private class NameRecord(
    val platform: Int,
    val encoding: Int,
    val language: Int,
    val nameId: Int,
    val length: Int,
    val offset: Int,
) {
    // Lower is better; MISSING means this record is not a candidate.
    //
    // Language is part of the test rather than a detail. Arial Bold carries a
    // Catalan Windows record reading "Arial Negreta", and a parser matching on
    // platform and encoding alone registers the font under that — so an English
    // script asking for "Arial Bold" finds nothing while the font sits loaded.
    // Which record within a name ID wins is a platform-and-language question;
    // which ID is wanted is the caller's, because a face is registered under
    // every one of them.
    fun scoreFor(wantedNameId: Int): Int = if (nameId != wantedNameId) MISSING else platformScore()

    private fun platformScore(): Int = when {
        platform == PLATFORM_WINDOWS && encoding == ENCODING_WINDOWS_UNICODE && language == LANGUAGE_US_ENGLISH -> 0
        platform == PLATFORM_UNICODE -> 1
        platform == PLATFORM_MAC && encoding == ENCODING_MAC_ROMAN -> 2
        else -> MISSING
    }

    // Windows and Unicode both store UTF-16 big-endian here, which is why a
    // name read as Latin-1 comes out with a null between every letter.
    fun decode(raw: ByteArray): String = when (platform) {
        PLATFORM_WINDOWS, PLATFORM_UNICODE -> utf16Be(raw)
        else -> raw.map { (it.toInt() and BYTE_MASK).toChar() }.joinToString("")
    }.trim()

    private fun utf16Be(raw: ByteArray): String {
        if (raw.size % 2 != 0) return ""

        val builder = StringBuilder(raw.size / 2)
        for (index in raw.indices step 2) {
            val high: Int = raw[index].toInt() and BYTE_MASK
            val low: Int = raw[index + 1].toInt() and BYTE_MASK
            builder.append(((high shl BITS_PER_BYTE) or low).toChar())
        }
        return builder.toString()
    }
}

// The name a font calls itself, read out of its own name table.
//
// libass matches the family an ASS script asks for against the name a font
// reports, and a filename is not that name. "SPLAT.TTF" is "Split splat
// splodge" inside; "arialbd.ttf" is "Arial Bold". Registering fonts under their
// filenames means every styled line falls back to a default and the subtitles
// render in the wrong typeface without anything failing.
//
// Pure Kotlin big-endian reads rather than a ByteBuffer, so the same parser runs
// on Android, Apple and the desktop. A font resolved differently per platform is
// the same subtitle rendering differently per platform.
public object TtfNameParser {

    // The full name, not the family, and the difference matters. A script
    // asking for "Arial Bold" gets nothing if the font registered itself as
    // "Arial" — libass matches the string. So name ID 4 wins over ID 1 whenever
    // both are present, which is the opposite of what most font tooling wants.
    public fun extractFontName(fontBytes: ByteArray, fallbackName: String): String =
        extractFontNames(fontBytes, fallbackName).first()

    /**
     * Every name a script might call this face by, best first.
     *
     * One name is not enough, and the cost of believing it was is a whole film
     * in the wrong typeface. `Fontin_Sans.otf` reports full name
     * "FontinSans-Bold", family "Fontin Sans Rg" and typographic family
     * "Fontin Sans"; No Game No Life's styles ask for "Fontin Sans Rg", so
     * registering only the winner registered the one name the script never
     * uses. libass then fell back to a wider face and the dialogue overran the
     * frame — read as a wrapping bug for as long as it went unmeasured.
     *
     * Registering them all costs nothing: the same bytes under several names is
     * how a font already answers to several names.
     */
    public fun extractFontNames(fontBytes: ByteArray, fallbackName: String): List<String> {
        val font = FontBytes(fontBytes)
        val table: IntRange = font.nameTable() ?: return listOf(fallbackName)

        val names: List<String> = NAME_IDS_WORTH_REGISTERING
            .mapNotNull { nameId -> font.bestNameFor(table, nameId) }
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        return names.ifEmpty { listOf(fallbackName) }
    }

    public fun fallbackNameFor(filename: String): String = filename.substringBeforeLast('.').trim()

    private fun FontBytes.nameTable(): IntRange? {
        if (size < HEADER_BYTES) return null

        val tableCount: Int = uint16(TABLE_COUNT_OFFSET)
        for (index in 0 until tableCount) {
            val entry: Int = TABLE_DIRECTORY_OFFSET + index * TABLE_ENTRY_BYTES
            if (int32(entry) != NAME_TABLE_TAG) continue

            return spanAt(int32(entry + OFFSET_FIELD), int32(entry + LENGTH_FIELD))
        }
        return null
    }

    // Null for a span that does not fit the file, which is what a truncated
    // attachment produces — the directory still claims a table that is no
    // longer there.
    private fun FontBytes.spanAt(offset: Int, length: Int): IntRange? {
        val fits: Boolean = offset > 0 && length > 0 && offset + length <= size

        return if (fits) offset until (offset + length) else null
    }

    private fun FontBytes.bestNameFor(table: IntRange, wantedNameId: Int): String? {
        val start: Int = table.first
        val recordCount: Int = uint16(start + RECORD_COUNT_FIELD)
        val stringsAt: Int = uint16(start + STRING_OFFSET_FIELD)

        return (0 until recordCount)
            .map { index -> recordAt(start + NAME_TABLE_HEADER_BYTES + index * NAME_RECORD_BYTES) }
            .filter { record -> record.scoreFor(wantedNameId) != MISSING }
            .sortedBy { record -> record.scoreFor(wantedNameId) }
            .firstNotNullOfOrNull { record -> record.textIn(this, start + stringsAt) }
    }

    private fun FontBytes.recordAt(at: Int): NameRecord = NameRecord(
        platform = uint16(at + RECORD_PLATFORM),
        encoding = uint16(at + RECORD_ENCODING),
        language = uint16(at + RECORD_LANGUAGE),
        nameId = uint16(at + RECORD_NAME_ID),
        length = uint16(at + RECORD_LENGTH),
        offset = uint16(at + RECORD_OFFSET),
    )

    private fun NameRecord.textIn(font: FontBytes, stringsAt: Int): String? {
        val raw: ByteArray = font.slice(stringsAt + offset, length) ?: return null

        return decode(raw).takeIf { it.isNotBlank() }
    }
}

// "name", as a big-endian tag.
private const val NAME_TABLE_TAG = 0x6E616D65

private const val NAME_ID_FAMILY = 1
private const val NAME_ID_FULL = 4

// The typographic family, which is the name a family with more than four
// weights reports and the one a style line for those weights asks for.
private const val NAME_ID_TYPOGRAPHIC_FAMILY = 16

// Best first, which is the order a caller registers them in and the order
// `extractFontName` picks its single answer from.
private val NAME_IDS_WORTH_REGISTERING = listOf(NAME_ID_FULL, NAME_ID_FAMILY, NAME_ID_TYPOGRAPHIC_FAMILY)

private const val PLATFORM_UNICODE = 0
private const val PLATFORM_MAC = 1
private const val PLATFORM_WINDOWS = 3

private const val ENCODING_MAC_ROMAN = 0
private const val ENCODING_WINDOWS_UNICODE = 1

// US English. Anything else is a translated name, which is not what a script
// written in English is asking for.
private const val LANGUAGE_US_ENGLISH = 0x0409

// Negative because every real offset, length and score is not. It reads as
// "there is nothing here" at each of the three call sites without three types.
private const val MISSING = -1

private const val HEADER_BYTES = 12
private const val TABLE_COUNT_OFFSET = 4
private const val TABLE_DIRECTORY_OFFSET = 12
private const val TABLE_ENTRY_BYTES = 16
private const val OFFSET_FIELD = 8
private const val LENGTH_FIELD = 12
private const val NAME_TABLE_HEADER_BYTES = 6
private const val NAME_RECORD_BYTES = 12
private const val RECORD_COUNT_FIELD = 2
private const val STRING_OFFSET_FIELD = 4

// The six fields of a name record, in the order the format lays them out.
private const val RECORD_PLATFORM = 0
private const val RECORD_ENCODING = 2
private const val RECORD_LANGUAGE = 4
private const val RECORD_NAME_ID = 6
private const val RECORD_LENGTH = 8
private const val RECORD_OFFSET = 10

private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8
private const val UINT16_BYTES = 2
private const val INT32_BYTES = 4
