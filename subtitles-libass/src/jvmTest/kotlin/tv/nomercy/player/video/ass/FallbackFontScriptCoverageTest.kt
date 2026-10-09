package tv.nomercy.player.video.ass

import java.awt.Font
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The bundled fallback face must carry a glyph for every script the app's
 * subtitles are shown in.
 *
 * Roboto alone has no Arabic, Hebrew or Thai. libass then draws the missing
 * glyph, an empty box, for every letter of those cues.
 */
class FallbackFontScriptCoverageTest {

    private val face: Font by lazy {
        val path = assertNotNull(FallbackFont.path, "the fallback face must unpack")
        Font.createFont(Font.TRUETYPE_FONT, File(path))
    }

    @Test
    fun mapsArabicAlef() = assertMapped(0x0627, "Arabic alef")

    @Test
    fun mapsHebrewAlef() = assertMapped(0x05D0, "Hebrew alef")

    @Test
    fun mapsThaiKoKai() = assertMapped(0x0E01, "Thai ko kai")

    private fun assertMapped(codePoint: Int, name: String) {
        assertTrue(face.canDisplay(codePoint), "fallback font has no glyph for $name (U+%04X)".format(codePoint))
    }
}
