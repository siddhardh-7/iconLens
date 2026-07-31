package io.github.siddhardh7.iconlens

import org.junit.Assert.assertEquals
import org.junit.Test

class VectorDrawableRenderingTest {

    @Test
    fun `renders a non-square viewport centered instead of stretched to fill the square canvas`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="10" android:viewportHeight="20">
                <path android:fillColor="#000000" android:pathData="M0,0 L10,0 L10,20 L0,20 Z"/>
            </vector>
        """.trimIndent()

        val image = renderVectorDrawable(xml, 40)

        // Fit-to-square with a 10x20 viewport in a 40x40 canvas scales by 2 (limited by height),
        // producing a 20-wide, 40-tall opaque region centered horizontally with a 10px margin
        // on each side. A stretch-to-fill would leave no margin at all.
        assertEquals(0, alphaAt(image, 5, 20))
        assertEquals(0, alphaAt(image, 34, 20))
        assertEquals(255, alphaAt(image, 20, 20))
    }

    private fun alphaAt(image: java.awt.image.BufferedImage, x: Int, y: Int): Int =
        (image.getRGB(x, y) ushr 24) and 0xFF
}
