package io.github.siddhardh7.iconlens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.geom.Path2D
import java.awt.geom.PathIterator

class PathDataInterpreterTest {

    private fun segments(path: Path2D): List<Pair<Int, DoubleArray>> {
        val result = mutableListOf<Pair<Int, DoubleArray>>()
        val iterator = path.getPathIterator(null)
        val coords = DoubleArray(6)
        while (!iterator.isDone) {
            val type = iterator.currentSegment(coords)
            result += type to coords.copyOf()
            iterator.next()
        }
        return result
    }

    @Test
    fun `absolute moveto lineto close forms expected bounds`() {
        val path = parsePathData("M0,0 L10,0 L10,10 L0,10 Z")
        val bounds = path.bounds2D
        assertEquals(0.0, bounds.minX, 0.0001)
        assertEquals(0.0, bounds.minY, 0.0001)
        assertEquals(10.0, bounds.maxX, 0.0001)
        assertEquals(10.0, bounds.maxY, 0.0001)
    }

    @Test
    fun `relative lineto matches equivalent absolute path`() {
        val path = parsePathData("M0,0 l10,0 l0,10 l-10,0 Z")
        val bounds = path.bounds2D
        assertEquals(0.0, bounds.minX, 0.0001)
        assertEquals(10.0, bounds.maxX, 0.0001)
        assertEquals(10.0, bounds.maxY, 0.0001)
    }

    @Test
    fun `moveto followed by extra coordinate pairs is treated as implicit lineto`() {
        val path = parsePathData("M0,0 10,0 10,10")
        assertEquals(10.0, path.currentPoint!!.x, 0.0001)
        assertEquals(10.0, path.currentPoint!!.y, 0.0001)
    }

    @Test
    fun `horizontal and vertical absolute and relative move to expected point`() {
        val absolute = parsePathData("M5,5 H20 V20")
        assertEquals(20.0, absolute.currentPoint!!.x, 0.0001)
        assertEquals(20.0, absolute.currentPoint!!.y, 0.0001)

        val relative = parsePathData("M5,5 h15 v15")
        assertEquals(20.0, relative.currentPoint!!.x, 0.0001)
        assertEquals(20.0, relative.currentPoint!!.y, 0.0001)
    }

    @Test
    fun `cubic curve absolute and relative end at expected point`() {
        val absolute = parsePathData("M0,0 C0,10 10,10 10,0")
        assertEquals(10.0, absolute.currentPoint!!.x, 0.0001)
        assertEquals(0.0, absolute.currentPoint!!.y, 0.0001)

        val relative = parsePathData("M0,0 c0,10 10,10 10,0")
        assertEquals(10.0, relative.currentPoint!!.x, 0.0001)
        assertEquals(0.0, relative.currentPoint!!.y, 0.0001)
    }

    @Test
    fun `smooth cubic reflects previous control point`() {
        val path = parsePathData("M0,0 C0,0 10,0 10,10 S15,5 20,10")
        val cubics = segments(path).filter { it.first == PathIterator.SEG_CUBICTO }
        assertEquals(2, cubics.size)
        val (_, secondCoords) = cubics[1]
        assertEquals(10.0, secondCoords[0], 0.0001)
        assertEquals(20.0, secondCoords[1], 0.0001)
        assertEquals(15.0, secondCoords[2], 0.0001)
        assertEquals(5.0, secondCoords[3], 0.0001)
        assertEquals(20.0, secondCoords[4], 0.0001)
        assertEquals(10.0, secondCoords[5], 0.0001)
    }

    @Test
    fun `smooth quadratic reflects previous control point`() {
        val path = parsePathData("M0,0 Q0,10 10,10 T20,10")
        val quads = segments(path).filter { it.first == PathIterator.SEG_QUADTO }
        assertEquals(2, quads.size)
        val (_, secondCoords) = quads[1]
        assertEquals(20.0, secondCoords[0], 0.0001)
        assertEquals(10.0, secondCoords[1], 0.0001)
        assertEquals(20.0, secondCoords[2], 0.0001)
        assertEquals(10.0, secondCoords[3], 0.0001)
    }

    @Test
    fun `elliptical arc ends at expected point and emits curve segments`() {
        val path = parsePathData("M0,0 A10,10 0 0,1 20,0")
        assertEquals(20.0, path.currentPoint!!.x, 0.0001)
        assertEquals(0.0, path.currentPoint!!.y, 0.0001)
        assertTrue(segments(path).any { it.first == PathIterator.SEG_CUBICTO })
    }

    @Test
    fun `degenerate arc with zero radius falls back to a straight line`() {
        val path = parsePathData("M0,0 A0,0 0 0,0 10,10")
        val types = segments(path).map { it.first }
        assertEquals(listOf(PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO), types)
    }

    @Test
    fun `unknown command throws`() {
        assertThrows(UnsupportedPathDataException::class.java) {
            parsePathData("M0,0 X10,10")
        }
    }

    @Test
    fun `missing coordinate throws instead of hanging`() {
        assertThrows(UnsupportedPathDataException::class.java) {
            parsePathData("M0,0 L10")
        }
    }

    @Test
    fun `argument after close path throws instead of hanging`() {
        assertThrows(UnsupportedPathDataException::class.java) {
            parsePathData("M0,0 L10,0 Z 5,5")
        }
    }

    @Test
    fun `path not starting with moveto throws`() {
        assertThrows(UnsupportedPathDataException::class.java) {
            parsePathData("L10,10")
        }
    }
}
