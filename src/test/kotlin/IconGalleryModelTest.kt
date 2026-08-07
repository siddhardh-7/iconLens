package io.github.siddhardh7.iconlens

import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class IconGalleryModelTest {

    private fun resource(name: String) =
        IconResource(name, IconResourceType.PNG, LightVirtualFile(name), "app")

    private fun rendered(name: String, image: BufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)) =
        RenderedIcon.Rendered(resource(name), image)

    private fun solidImage(color: Color): BufferedImage {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, 64, 64)
        g.dispose()
        return image
    }

    private fun splitImage(splitAt: Int): BufferedImage {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, splitAt, 64)
        g.color = Color.BLACK
        g.fillRect(splitAt, 0, 64 - splitAt, 64)
        g.dispose()
        return image
    }

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

    @Test
    fun `rankRenderedIcons orders candidates by descending similarity to the query`() {
        val white = solidImage(Color.WHITE)
        val halfBlack = splitImage(32)
        val icons = listOf(rendered("ic_half_black", halfBlack), rendered("ic_white", white))

        val ranked = rankRenderedIcons(icons, white, CenteredImageNormalizer(), DHashSimilarityEngine())

        assertEquals(listOf("ic_white", "ic_half_black"), ranked.map { it.candidate.resource.name })
        assertEquals(1.0, ranked[0].score, 0.0001)
        assertTrue("expected the half-black candidate to score below a perfect match", ranked[1].score < 1.0)
    }

    @Test
    fun `rankRenderedIcons on an empty icon list returns an empty result`() {
        val ranked = rankRenderedIcons(emptyList(), solidImage(Color.WHITE), CenteredImageNormalizer(), DHashSimilarityEngine())

        assertEquals(emptyList<ScoredMatch<RenderedIcon.Rendered>>(), ranked)
    }
}
