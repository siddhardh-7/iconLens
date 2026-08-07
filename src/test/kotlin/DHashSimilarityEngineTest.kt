package io.github.siddhardh7.iconlens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class DHashSimilarityEngineTest {

    private val engine = DHashSimilarityEngine()

    private fun distance(a: IconDescriptor, b: IconDescriptor) =
        a.hash.indices.sumOf { java.lang.Long.bitCount(a.hash[it] xor b.hash[it]) }

    @Test
    fun `solid color image produces an all-zero hash`() {
        val solid = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        val g = solid.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 64, 64)
        g.dispose()

        val descriptor = engine.describe(NormalizedIcon(solid))

        assertEquals(List(8) { 0L }, descriptor.hash)
    }

    @Test
    fun `alternating columns produce the expected exact pattern in the horizontal component`() {
        // Built directly at 17x16 (the horizontal grid's own working resolution) so
        // drawImage's width scale factor is exactly 1:1 for that grid -- every destination
        // pixel in that resize is byte-for-byte the corresponding source pixel. This avoids
        // needing to hand-verify Java2D's bilinear resampling arithmetic while still
        // exercising the real grayscale + bit-packing logic exactly as describe() runs it.
        // The image has no row-to-row (y) variation, so the vertical component (comparing
        // same-column pixels across rows) is exactly zero regardless of how the 16x17
        // vertical-grid resize blends columns.
        val alternating = BufferedImage(17, 16, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 16) {
            for (x in 0 until 17) {
                val gray = if (x % 2 == 0) 255 else 0
                alternating.setRGB(x, y, (gray shl 16) or (gray shl 8) or gray)
            }
        }

        val descriptor = engine.describe(NormalizedIcon(alternating))

        // Each row: 16 column-pair comparisons x vs x+1 for x=0..15 alternate
        // white>black, black>white, ... -> bits 1,0,1,0,...,1,0 -> 0x5555 per row (16 bits).
        // All 16 rows are identical, so each 64-bit long (4 rows) is 0x5555555555555555;
        // the horizontal component is 4 such longs, the vertical component is all zero.
        val horizontalLong = 0x5555555555555555L
        assertEquals(List(4) { horizontalLong } + List(4) { 0L }, descriptor.hash)
    }

    @Test
    fun `alternating rows produce the expected exact pattern in the vertical component`() {
        // Mirror image of the horizontal test above, built at 16x17 (the vertical grid's
        // own working resolution). No column-to-column (x) variation, so the horizontal
        // component is exactly zero regardless of how the 17x16 horizontal-grid resize
        // blends rows.
        val alternating = BufferedImage(16, 17, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 17) {
            for (x in 0 until 16) {
                val gray = if (y % 2 == 0) 255 else 0
                alternating.setRGB(x, y, (gray shl 16) or (gray shl 8) or gray)
            }
        }

        val descriptor = engine.describe(NormalizedIcon(alternating))

        val verticalLong = 0x5555555555555555L
        assertEquals(List(4) { 0L } + List(4) { verticalLong }, descriptor.hash)
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

        assertTrue("expected a small nonzero distance, got ${distance(a, b)}", distance(a, b) in 1..24)
    }

    @Test
    fun `identical descriptors score 1_0`() {
        val a = IconDescriptor(List(8) { 0x1234L })

        assertEquals(1.0, engine.score(a, a), 0.0001)
    }

    @Test
    fun `descriptor with every bit flipped scores 0_0`() {
        val a = IconDescriptor(List(8) { 0L })
        val b = IconDescriptor(a.hash.map { it.inv() })

        assertEquals(0.0, engine.score(a, b), 0.0001)
    }

    @Test
    fun `descriptor differing in exactly 4 bits scores 1 minus 4 over 512`() {
        val a = IconDescriptor(List(8) { 0L })
        val b = IconDescriptor(listOf(0b1111L) + List(7) { 0L })

        assertEquals(1.0 - 4.0 / 512.0, engine.score(a, b), 0.0001)
    }
}
