package io.github.siddhardh7.iconlens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.awt.Color

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
        assertEquals(Color(0xFF, 0, 0), shape.paths.single().fillColor)
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
    fun `gradient fill via aapt attr is unsupported`() {
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
    fun `clip-path element is unsupported`() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="10" android:viewportHeight="10">
                <clip-path android:pathData="M0,0 L10,0 L10,10 Z"/>
            </vector>
        """.trimIndent()

        assertThrows(UnsupportedVectorDrawableException::class.java) { parseVectorDrawable(xml) }
    }

    @Test
    fun `malformed xml is unsupported`() {
        assertThrows(UnsupportedVectorDrawableException::class.java) {
            parseVectorDrawable("<vector><path")
        }
    }
}
