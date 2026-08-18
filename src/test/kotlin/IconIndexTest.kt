package io.github.siddhardh7.iconlens

import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import kotlin.system.measureTimeMillis

class IconIndexTest {

    /** Exposes the otherwise-protected modification stamp setter, for the "file changed" test. */
    private class MutableStampFile(name: String) : LightVirtualFile(name) {
        public override fun setModificationStamp(modificationStamp: Long) {
            super.setModificationStamp(modificationStamp)
        }
    }

    private fun resource(name: String, file: LightVirtualFile = LightVirtualFile(name), moduleName: String = "app") =
        IconResource(name, IconResourceType.PNG, file, moduleName)

    private class FakeIconSource(private val resources: List<IconResource>) : IconSource {
        override suspend fun discover() = resources
    }

    private class CountingRenderer : IconRenderer {
        val renderCounts = mutableMapOf<String, Int>()

        override suspend fun render(resource: IconResource): RenderedIcon {
            renderCounts[resource.name] = (renderCounts[resource.name] ?: 0) + 1
            return RenderedIcon.Rendered(resource, BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
        }
    }

    @Test
    fun `first refresh renders every discovered resource`() {
        val renderer = CountingRenderer()
        val resources = listOf(resource("ic_calendar"), resource("ic_close"))

        val result = runBlocking { IconIndex().refresh(FakeIconSource(resources), renderer) }

        assertEquals(2, result.size)
        assertEquals(setOf("ic_calendar", "ic_close"), result.map { it.resource.name }.toSet())
        assertEquals(1, renderer.renderCounts["ic_calendar"])
        assertEquals(1, renderer.renderCounts["ic_close"])
    }

    @Test
    fun `second refresh with unchanged files reuses cached icons without re-rendering`() {
        val renderer = CountingRenderer()
        val resources = listOf(resource("ic_calendar"))
        val index = IconIndex()

        val first = runBlocking { index.refresh(FakeIconSource(resources), renderer) }
        val second = runBlocking { index.refresh(FakeIconSource(resources), renderer) }

        assertSame(first[0], second[0])
        assertEquals(1, renderer.renderCounts["ic_calendar"])
    }

    @Test
    fun `a resource whose file was modified since the last refresh is re-rendered`() {
        val renderer = CountingRenderer()
        val file = MutableStampFile("ic_calendar")
        val resources = listOf(resource("ic_calendar", file))
        val index = IconIndex()

        runBlocking { index.refresh(FakeIconSource(resources), renderer) }
        file.setModificationStamp(file.modificationStamp + 1)
        runBlocking { index.refresh(FakeIconSource(resources), renderer) }

        assertEquals(2, renderer.renderCounts["ic_calendar"])
    }

    @Test
    fun `a resource no longer discovered is dropped from the result`() {
        val renderer = CountingRenderer()
        val index = IconIndex()

        val first = runBlocking {
            index.refresh(FakeIconSource(listOf(resource("ic_calendar"), resource("ic_close"))), renderer)
        }
        val second = runBlocking {
            index.refresh(FakeIconSource(listOf(resource("ic_calendar"))), renderer)
        }

        assertEquals(2, first.size)
        assertEquals(listOf("ic_calendar"), second.map { it.resource.name })
    }

    @Test
    fun `a newly discovered resource is rendered and added`() {
        val renderer = CountingRenderer()
        val index = IconIndex()

        runBlocking { index.refresh(FakeIconSource(listOf(resource("ic_calendar"))), renderer) }
        val second = runBlocking {
            index.refresh(FakeIconSource(listOf(resource("ic_calendar"), resource("ic_close"))), renderer)
        }

        assertEquals(setOf("ic_calendar", "ic_close"), second.map { it.resource.name }.toSet())
        assertEquals(1, renderer.renderCounts["ic_close"])
    }

    @Test
    fun `a representative-variant swap updates the same logical resource in place`() {
        val renderer = CountingRenderer()
        val originalFile = LightVirtualFile("ic_calendar_mdpi")
        val newRepresentativeFile = LightVirtualFile("ic_calendar_xhdpi")
        val index = IconIndex()

        val first = runBlocking {
            index.refresh(FakeIconSource(listOf(resource("ic_calendar", originalFile))), renderer)
        }
        val second = runBlocking {
            index.refresh(FakeIconSource(listOf(resource("ic_calendar", newRepresentativeFile))), renderer)
        }

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(2, renderer.renderCounts["ic_calendar"])
        assertEquals(newRepresentativeFile, second[0].resource.file)
    }

    @Test
    fun `refresh scales to 1000 resources within a time budget`() {
        val renderer = CountingRenderer()
        val resources = (1..1000).map { resource("ic_$it") }

        val elapsedMillis = measureTimeMillis {
            val result = runBlocking { IconIndex().refresh(FakeIconSource(resources), renderer) }
            assertEquals(1000, result.size)
        }

        assertTrue(
            "refresh of 1000 resources took ${elapsedMillis}ms, expected under 2000ms",
            elapsedMillis < 2000,
        )
    }
}
