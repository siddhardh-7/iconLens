package io.github.siddhardh7.iconlens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class DHashSimilarityEngineTest {

    private val engine = DHashSimilarityEngine()

    @Test
    fun `solid color image produces a zero hash`() {
        val solid = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        val g = solid.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 64, 64)
        g.dispose()

        val descriptor = engine.describe(NormalizedIcon(solid))

        assertEquals(0L, descriptor.hash)
    }

    @Test
    fun `alternating columns produce the expected exact bit pattern`() {
        // Built directly at 9x8 (the hash's own working resolution) so drawImage's
        // scale factor is exactly 1:1 and no interpolation blending occurs -- every
        // destination pixel is byte-for-byte the corresponding source pixel. This
        // avoids needing to hand-verify Java2D's bilinear resampling arithmetic for
        // a real 64x64 -> 9x8 downsample, while still exercising the real
        // grayscale + bit-packing logic exactly as describe() runs it.
        val alternating = BufferedImage(9, 8, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 8) {
            for (x in 0 until 9) {
                val gray = if (x % 2 == 0) 255 else 0
                alternating.setRGB(x, y, (gray shl 16) or (gray shl 8) or gray)
            }
        }

        val descriptor = engine.describe(NormalizedIcon(alternating))

        // Each row: columns 0,2,4,6,8 are white(255), columns 1,3,5,7 are black(0).
        // Column-pair comparisons x vs x+1 for x=0..7: white>black, black>white,
        // white>black, black>white, white>black, black>white, white>black, black>white
        // -> bits (x=0..7): 1,0,1,0,1,0,1,0 -> 0b01010101 = 0x55 per row.
        // All 8 rows are identical, so the full 64-bit hash is 0x55 repeated in
        // every byte: 0x5555555555555555.
        assertEquals(0x5555555555555555L, descriptor.hash)
    }

    @Test
    fun `a small shift produces a small but nonzero hamming distance`() {
        fun splitImage(splitAt: Int): BufferedImage {
            val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, splitAt, 64)
            g.color = Color.BLACK
            g.fillRect(splitAt, 0, 64 - splitAt, 64)
            g.dispose()
            return image
        }

        val a = engine.describe(NormalizedIcon(splitImage(32)))
        val b = engine.describe(NormalizedIcon(splitImage(30)))
        val distance = java.lang.Long.bitCount(a.hash xor b.hash)

        assertTrue("expected a small nonzero distance, got $distance", distance in 1..16)
    }

    @Test
    fun `identical descriptors score 1_0`() {
        val a = IconDescriptor(0x1234L)

        assertEquals(1.0, engine.score(a, a), 0.0001)
    }

    @Test
    fun `descriptor with every bit flipped scores 0_0`() {
        val a = IconDescriptor(0L)
        val b = IconDescriptor(a.hash.inv())

        assertEquals(0.0, engine.score(a, b), 0.0001)
    }

    @Test
    fun `descriptor differing in exactly 4 bits scores 1 minus 4 over 64`() {
        val a = IconDescriptor(0L)
        val b = IconDescriptor(0b1111L)

        assertEquals(1.0 - 4.0 / 64.0, engine.score(a, b), 0.0001)
    }
}
