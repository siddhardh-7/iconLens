package io.github.siddhardh7.iconlens

import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.image.BufferedImage

class IconGalleryModelTest {

    private fun resource(name: String) =
        IconResource(name, IconResourceType.PNG, LightVirtualFile(name), "app")

    private fun rendered(name: String) =
        RenderedIcon.Rendered(resource(name), BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))

    private class FakeIconSource(private val resources: List<IconResource>) : IconSource {
        override suspend fun discover() = resources
    }

    private class FakeIconRenderer : IconRenderer {
        override suspend fun render(resource: IconResource) =
            RenderedIcon.Rendered(resource, BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
    }

    @Test
    fun `loadGallery renders every discovered resource`() {
        val resources = listOf(resource("ic_calendar"), resource("ic_close"))
        val result = runBlocking { loadGallery(FakeIconSource(resources), FakeIconRenderer()) }

        assertEquals(2, result.size)
        assertEquals(setOf("ic_calendar", "ic_close"), result.map { it.resource.name }.toSet())
    }

    @Test
    fun `filterByName matches case-insensitive substring`() {
        val icons = listOf(rendered("ic_calendar"), rendered("ic_close"))

        val result = filterByName(icons, "CAL")

        assertEquals(listOf("ic_calendar"), result.map { it.resource.name })
    }

    @Test
    fun `filterByName with blank query returns everything unchanged`() {
        val icons = listOf(rendered("ic_calendar"))

        val result = filterByName(icons, "")

        assertEquals(icons, result)
    }
}
