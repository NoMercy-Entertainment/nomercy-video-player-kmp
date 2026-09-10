package tv.nomercy.player.video.ass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The length the renderer copies out of an `ASS_Image`.
 *
 * `stride * h` is the rectangle the struct describes, and up to `stride - w`
 * bytes more than libass allocated: every row but the last is padded to the
 * stride, the last one stops at the width. On a bitmap whose allocation ends on
 * a page boundary that difference is not a stale byte, it is a segfault —
 * SIGSEGV in `__memcpy_aarch64_simd` under `Native.read`, reported from a real
 * phone mid-episode on 2026-08-31.
 */
class ReadableBytesTest {

    @Test
    fun stopsWhereLibassStopsRatherThanAtTheRectangle() {
        // A padded bitmap: 100 wide, rows aligned to 128.
        assertEquals(128 * 9 + 100, readableBytes(stride = 128, height = 10, width = 100))
    }

    @Test
    fun neverAsksForMoreThanTheRectangleHolds() {
        for (stride in listOf(4, 16, 100, 128, 1920)) {
            for (height in 1..8) {
                for (width in 1..stride) {
                    val readable = readableBytes(stride, height, width)
                    assertTrue(
                        readable <= stride * height,
                        "stride=$stride h=$height w=$width read $readable of ${stride * height}",
                    )
                    assertTrue(
                        readable >= stride * (height - 1) + width,
                        "stride=$stride h=$height w=$width dropped pixels",
                    )
                }
            }
        }
    }

    // An unpadded bitmap has nothing to trim, and trimming it anyway would drop
    // the tail of the last row — the bug this guards against, backwards.
    @Test
    fun anUnpaddedBitmapIsReadWhole() {
        assertEquals(64 * 5, readableBytes(stride = 64, height = 5, width = 64))
    }

    @Test
    fun anEmptyImageAsksForNothing() {
        assertEquals(0, readableBytes(stride = 0, height = 10, width = 10))
        assertEquals(0, readableBytes(stride = 128, height = 0, width = 10))
        assertEquals(0, readableBytes(stride = 128, height = 10, width = 0))
    }
}
