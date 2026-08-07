package io.github.siddhardh7.iconlens

import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryResourceActionsTest {

    private fun resource(name: String, type: IconResourceType) =
        IconResource(name, type, LightVirtualFile(name), "app")

    @Test
    fun `vector drawable reference is R_drawable dot name`() {
        val ref = androidResourceReference(resource("ic_calendar", IconResourceType.VECTOR_DRAWABLE))
        assertEquals("R.drawable.ic_calendar", ref)
    }

    @Test
    fun `png reference is R_drawable dot name`() {
        val ref = androidResourceReference(resource("ic_close", IconResourceType.PNG))
        assertEquals("R.drawable.ic_close", ref)
    }

    @Test
    fun `webp reference is R_drawable dot name`() {
        val ref = androidResourceReference(resource("ic_add", IconResourceType.WEBP))
        assertEquals("R.drawable.ic_add", ref)
    }

    @Test
    fun `jpeg reference is R_drawable dot name`() {
        val ref = androidResourceReference(resource("photo", IconResourceType.JPEG))
        assertEquals("R.drawable.photo", ref)
    }
}
