package io.github.siddhardh7.iconlens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class CenteredImageNormalizerTest {

    @Test
    fun `already square content-filling input is scaled to fill the canvas with no white margin`() {
        val source = BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)
        val g = source.createGraphics()
        g.color = Color.BLACK
        g.fillRect(0, 0, 20, 20)
        g.dispose()

        val result = CenteredImageNormalizer().normalize(source).image

        assertEquals(NORMALIZED_SIZE, result.width)
        assertEquals(NORMALIZED_SIZE, result.height)
        assertEquals(Color.BLACK.rgb, result.getRGB(0, 0))
        assertEquals(Color.BLACK.rgb, result.getRGB(NORMALIZED_SIZE - 1, NORMALIZED_SIZE - 1))
    }

    @Test
    fun `non-square input is fit-centered instead of stretched`() {
        val source = BufferedImage(10, 20, BufferedImage.TYPE_INT_ARGB)
        val g = source.createGraphics()
        g.color = Color.BLACK
        g.fillRect(0, 0, 10, 20)
        g.dispose()

        val result = CenteredImageNormalizer().normalize(source).image

        assertEquals(Color.WHITE.rgb, result.getRGB(5, NORMALIZED_SIZE / 2))
        assertEquals(Color.BLACK.rgb, result.getRGB(NORMALIZED_SIZE / 2, NORMALIZED_SIZE / 2))
        assertEquals(Color.WHITE.rgb, result.getRGB(NORMALIZED_SIZE - 5, NORMALIZED_SIZE / 2))
    }

    @Test
    fun `crops large transparent padding so content fills the canvas the same as an already-tight crop`() {
        val padded = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val paddedGraphics = padded.createGraphics()
        paddedGraphics.color = Color.BLACK
        paddedGraphics.fillRect(27, 27, 10, 10)
        paddedGraphics.dispose()

        val tight = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
        val tightGraphics = tight.createGraphics()
        tightGraphics.color = Color.BLACK
        tightGraphics.fillRect(0, 0, 10, 10)
        tightGraphics.dispose()

        val normalizer = CenteredImageNormalizer()
        val paddedResult = normalizer.normalize(padded).image
        val tightResult = normalizer.normalize(tight).image

        assertEquals(Color.BLACK.rgb, paddedResult.getRGB(0, 0))
        assertEquals(Color.BLACK.rgb, tightResult.getRGB(0, 0))
    }

    @Test
    fun `fully transparent input produces a plain white image without throwing`() {
        val blank = BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)

        val result = CenteredImageNormalizer().normalize(blank).image

        assertEquals(NORMALIZED_SIZE, result.width)
        assertEquals(NORMALIZED_SIZE, result.height)
        assertEquals(Color.WHITE.rgb, result.getRGB(NORMALIZED_SIZE / 2, NORMALIZED_SIZE / 2))
    }

    @Test
    fun `semi-transparent pixels blend with the white background instead of a hard cutoff`() {
        val source = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        val halfAlphaRed = (0x80 shl 24) or (0xFF shl 16)
        for (x in 0 until 2) {
            for (y in 0 until 2) {
                source.setRGB(x, y, halfAlphaRed)
            }
        }

        val result = CenteredImageNormalizer().normalize(source).image
        val blended = Color(result.getRGB(NORMALIZED_SIZE / 2, NORMALIZED_SIZE / 2))

        assertEquals(255, blended.red)
        assertTrue(blended.green in 100..200)
        assertTrue(blended.blue in 100..200)
    }
}
