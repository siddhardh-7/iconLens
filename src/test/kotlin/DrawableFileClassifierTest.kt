package io.github.siddhardh7.iconlens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawableFileClassifierTest {

    @Test
    fun `maps xml to vector drawable`() {
        assertEquals(IconResourceType.VECTOR_DRAWABLE, resolveIconResourceType("ic_calendar.xml"))
    }

    @Test
    fun `maps png to png`() {
        assertEquals(IconResourceType.PNG, resolveIconResourceType("ic_calendar.png"))
    }

    @Test
    fun `maps webp to webp`() {
        assertEquals(IconResourceType.WEBP, resolveIconResourceType("ic_calendar.webp"))
    }

    @Test
    fun `maps jpg and jpeg to jpeg`() {
        assertEquals(IconResourceType.JPEG, resolveIconResourceType("ic_calendar.jpg"))
        assertEquals(IconResourceType.JPEG, resolveIconResourceType("ic_calendar.jpeg"))
    }

    @Test
    fun `is case insensitive on extension`() {
        assertEquals(IconResourceType.PNG, resolveIconResourceType("IC_CALENDAR.PNG"))
    }

    @Test
    fun `rejects nine-patch png`() {
        assertNull(resolveIconResourceType("ic_calendar.9.png"))
    }

    @Test
    fun `rejects unsupported extensions`() {
        assertNull(resolveIconResourceType("ic_calendar.svg"))
    }

    @Test
    fun `rejects files with no extension`() {
        assertNull(resolveIconResourceType("ic_calendar"))
    }
}
