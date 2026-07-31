package io.github.siddhardh7.iconlens

import org.junit.Assert.assertEquals
import org.junit.Test

class IconFitScalingTest {

    @Test
    fun `square source fills target with no offset`() {
        val fit = fitScaleAndOffset(10.0, 10.0, 40)

        assertEquals(4.0, fit.scale, 0.0001)
        assertEquals(0.0, fit.offsetX, 0.0001)
        assertEquals(0.0, fit.offsetY, 0.0001)
    }

    @Test
    fun `wide source is limited by width and centered vertically`() {
        val fit = fitScaleAndOffset(20.0, 10.0, 40)

        assertEquals(2.0, fit.scale, 0.0001)
        assertEquals(0.0, fit.offsetX, 0.0001)
        assertEquals(10.0, fit.offsetY, 0.0001)
    }

    @Test
    fun `tall source is limited by height and centered horizontally`() {
        val fit = fitScaleAndOffset(10.0, 20.0, 40)

        assertEquals(2.0, fit.scale, 0.0001)
        assertEquals(10.0, fit.offsetX, 0.0001)
        assertEquals(0.0, fit.offsetY, 0.0001)
    }

    @Test
    fun `non-positive dimensions return an identity transform instead of dividing by zero`() {
        val fit = fitScaleAndOffset(0.0, 10.0, 40)

        assertEquals(1.0, fit.scale, 0.0001)
        assertEquals(0.0, fit.offsetX, 0.0001)
        assertEquals(0.0, fit.offsetY, 0.0001)
    }
}
