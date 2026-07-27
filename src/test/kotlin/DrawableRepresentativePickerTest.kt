package io.github.siddhardh7.iconlens

import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawableRepresentativePickerTest {

    private fun candidate(
        module: String = "app",
        qualifier: String,
        baseName: String = "ic_calendar",
        type: IconResourceType = IconResourceType.PNG,
    ) = DrawableCandidate(
        moduleName = module,
        qualifierDirName = qualifier,
        baseName = baseName,
        type = type,
        file = LightVirtualFile("$baseName-$qualifier"),
    )

    @Test
    fun `single candidate passes through unchanged`() {
        val only = candidate(qualifier = "drawable")
        val result = pickRepresentatives(listOf(only))
        assertEquals(1, result.size)
        assertEquals(IconResource(only.baseName, only.type, only.file, only.moduleName), result.single())
    }

    @Test
    fun `plain drawable directory wins over density variants regardless of order`() {
        val plain = candidate(qualifier = "drawable")
        val hdpi = candidate(qualifier = "drawable-hdpi")
        val xhdpi = candidate(qualifier = "drawable-xhdpi")

        val result = pickRepresentatives(listOf(hdpi, xhdpi, plain))

        assertEquals(1, result.size)
        assertEquals(plain.file, result.single().file)
    }

    @Test
    fun `without a plain directory the alphabetically first qualifier wins`() {
        val hdpi = candidate(qualifier = "drawable-hdpi")
        val xhdpi = candidate(qualifier = "drawable-xhdpi")

        val result = pickRepresentatives(listOf(xhdpi, hdpi))

        assertEquals(1, result.size)
        assertEquals(hdpi.file, result.single().file)
    }

    @Test
    fun `different modules with the same resource name stay separate`() {
        val appIcon = candidate(module = "app", qualifier = "drawable")
        val libIcon = candidate(module = "core", qualifier = "drawable")

        val result = pickRepresentatives(listOf(appIcon, libIcon))

        assertEquals(2, result.size)
        assertEquals(setOf("app", "core"), result.map { it.moduleName }.toSet())
    }

    @Test
    fun `different resource names in the same module stay separate`() {
        val calendar = candidate(baseName = "ic_calendar", qualifier = "drawable")
        val close = candidate(baseName = "ic_close", qualifier = "drawable")

        val result = pickRepresentatives(listOf(calendar, close))

        assertEquals(2, result.size)
        assertEquals(setOf("ic_calendar", "ic_close"), result.map { it.name }.toSet())
    }
}
