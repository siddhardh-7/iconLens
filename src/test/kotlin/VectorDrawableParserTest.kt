package io.github.siddhardh7.iconlens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.LinearGradientPaint
import java.awt.geom.Path2D

class VectorDrawableParserTest {

    @Test
    fun `parses viewport and single path with fill color`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="24dp" android:height="24dp"
                android:viewportWidth="24" android:viewportHeight="24">
                <path android:fillColor="#FF0000" android:pathData="M0,0 L24,0 L24,24 L0,24 Z"/>
            </vector>
        """.trimIndent()

        val shape = parseVectorDrawable(xml)

        assertEquals(24.0, shape.viewportWidth, 0.0001)
        assertEquals(24.0, shape.viewportHeight, 0.0001)
        assertEquals(1, shape.paths.size)
        assertEquals(Color(0xFF, 0, 0), shape.paths.single().fillPaint)
    }

    @Test
    fun `applies group translate to child path`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="10" android:viewportHeight="10">
                <group android:translateX="5" android:translateY="0">
                    <path android:fillColor="#000000" android:pathData="M0,0 L1,0 L1,1 L0,1 Z"/>
                </group>
            </vector>
        """.trimIndent()

        val shape = parseVectorDrawable(xml)
        val bounds = shape.paths.single().path.bounds2D

        assertEquals(5.0, bounds.minX, 0.0001)
        assertEquals(6.0, bounds.maxX, 0.0001)
    }

    @Test
    fun `applies group rotation around pivot to child path`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="10" android:viewportHeight="10">
                <group android:rotation="90" android:pivotX="0" android:pivotY="0">
                    <path android:fillColor="#000000" android:pathData="M0,0 L1,0"/>
                </group>
            </vector>
        """.trimIndent()

        val shape = parseVectorDrawable(xml)
        val end = shape.paths.single().path.currentPoint!!

        assertEquals(0.0, end.x, 0.0001)
        assertEquals(1.0, end.y, 0.0001)
    }

    @Test
    fun `applies group scale around pivot to child path`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="10" android:viewportHeight="10">
                <group android:scaleX="2" android:scaleY="1" android:pivotX="2" android:pivotY="0">
                    <path android:fillColor="#000000" android:pathData="M0,0"/>
                </group>
            </vector>
        """.trimIndent()

        val shape = parseVectorDrawable(xml)
        val point = shape.paths.single().path.currentPoint!!

        assertEquals(-2.0, point.x, 0.0001)
        assertEquals(0.0, point.y, 0.0001)
    }

    @Test
    fun `missing viewport dimensions is unsupported`() {
        assertThrows(UnsupportedVectorDrawableException::class.java) {
            parseVectorDrawable("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"/>")
        }
    }

    @Test
    fun `animated-vector root is unsupported`() {
        assertThrows(UnsupportedVectorDrawableException::class.java) {
            parseVectorDrawable(
                """<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"/>""",
            )
        }
    }

    @Test
    fun `gradient without item stops is unsupported`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:aapt="http://schemas.android.com/aapt"
                android:viewportWidth="10" android:viewportHeight="10">
                <path android:pathData="M0,0 L10,0 L10,10 Z">
                    <aapt:attr name="android:fillColor">
                        <gradient android:startColor="#FF0000" android:endColor="#0000FF"/>
                    </aapt:attr>
                </path>
            </vector>
        """.trimIndent()

        assertThrows(UnsupportedVectorDrawableException::class.java) { parseVectorDrawable(xml) }
    }

    @Test
    fun `parses a linear gradient fill via aapt attr`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:aapt="http://schemas.android.com/aapt"
                android:viewportWidth="10" android:viewportHeight="10">
                <path android:pathData="M0,0 L10,0 L10,10 Z">
                    <aapt:attr name="android:fillColor">
                        <gradient
                            android:startX="0" android:startY="0"
                            android:endX="10" android:endY="0"
                            android:type="linear">
                            <item android:offset="0" android:color="#FF0000"/>
                            <item android:offset="1" android:color="#0000FF"/>
                        </gradient>
                    </aapt:attr>
                </path>
            </vector>
        """.trimIndent()

        val shape = parseVectorDrawable(xml)

        assertTrue(shape.paths.single().fillPaint is LinearGradientPaint)
    }

    @Test
    fun `radial gradient type is unsupported`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:aapt="http://schemas.android.com/aapt"
                android:viewportWidth="10" android:viewportHeight="10">
                <path android:pathData="M0,0 L10,0 L10,10 Z">
                    <aapt:attr name="android:fillColor">
                        <gradient android:type="radial" android:centerX="5" android:centerY="5" android:gradientRadius="5">
                            <item android:offset="0" android:color="#FF0000"/>
                            <item android:offset="1" android:color="#0000FF"/>
                        </gradient>
                    </aapt:attr>
                </path>
            </vector>
        """.trimIndent()

        assertThrows(UnsupportedVectorDrawableException::class.java) { parseVectorDrawable(xml) }
    }

    @Test
    fun `clip-path restricts subsequent paths in the same group`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="10" android:viewportHeight="10">
                <clip-path android:pathData="M2,2 L8,2 L8,8 L2,8 Z"/>
                <path android:fillColor="#000000" android:pathData="M0,0 L10,0 L10,10 L0,10 Z"/>
            </vector>
        """.trimIndent()

        val shape = parseVectorDrawable(xml)
        val clip = shape.paths.single().clip

        assertNotNull(clip)
        assertEquals(2.0, clip!!.bounds2D.minX, 0.0001)
        assertEquals(8.0, clip.bounds2D.maxX, 0.0001)
    }

    @Test
    fun `clip-path does not affect paths that came before it`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="10" android:viewportHeight="10">
                <path android:fillColor="#000000" android:pathData="M0,0 L10,0 L10,10 L0,10 Z"/>
                <clip-path android:pathData="M2,2 L8,2 L8,8 L2,8 Z"/>
            </vector>
        """.trimIndent()

        val shape = parseVectorDrawable(xml)

        assertNull(shape.paths.single().clip)
    }

    @Test
    fun `malformed xml is unsupported`() {
        assertThrows(UnsupportedVectorDrawableException::class.java) {
            parseVectorDrawable("<vector><path")
        }
    }

    @Test
    fun `parses 3-digit shorthand hex color`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="10" android:viewportHeight="10">
                <path android:fillColor="#fff" android:pathData="M0,0"/>
            </vector>
        """.trimIndent()

        val shape = parseVectorDrawable(xml)

        assertEquals(Color(0xFF, 0xFF, 0xFF), shape.paths.single().fillPaint)
    }

    @Test
    fun `parses 4-digit shorthand ARGB hex color`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="10" android:viewportHeight="10">
                <path android:fillColor="#8421" android:pathData="M0,0"/>
            </vector>
        """.trimIndent()

        val shape = parseVectorDrawable(xml)

        assertEquals(Color(0x44, 0x22, 0x11, 0x88), shape.paths.single().fillPaint)
    }

    @Test
    fun `evenOdd fillType sets the path winding rule`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="10" android:viewportHeight="10">
                <path android:fillColor="#000000" android:fillType="evenOdd" android:pathData="M0,0"/>
            </vector>
        """.trimIndent()

        val shape = parseVectorDrawable(xml)

        assertEquals(Path2D.WIND_EVEN_ODD, shape.paths.single().path.windingRule)
    }

    @Test
    fun `default fillType uses nonZero winding rule`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="10" android:viewportHeight="10">
                <path android:fillColor="#000000" android:pathData="M0,0"/>
            </vector>
        """.trimIndent()

        val shape = parseVectorDrawable(xml)

        assertEquals(Path2D.WIND_NON_ZERO, shape.paths.single().path.windingRule)
    }
}
