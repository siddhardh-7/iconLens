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
    fun `different modules with genuinely different files stay separate`() {
        val appIcon = DrawableCandidate(
            moduleName = "app",
            qualifierDirName = "drawable",
            baseName = "ic_calendar",
            type = IconResourceType.PNG,
            file = LightVirtualFile("app/ic_calendar-drawable"),
        )
        val libIcon = DrawableCandidate(
            moduleName = "core",
            qualifierDirName = "drawable",
            baseName = "ic_calendar",
            type = IconResourceType.PNG,
            file = LightVirtualFile("core/ic_calendar-drawable"),
        )

        val result = pickRepresentatives(listOf(appIcon, libIcon))

        assertEquals(2, result.size)
        assertEquals(setOf("app", "core"), result.map { it.moduleName }.toSet())
    }

    @Test
    fun `same physical file discovered under nested module content roots collapses to one result`() {
        // Android Gradle sync represents one Gradle module as several IntelliJ modules
        // (root project, the app module, its main source set, etc). When those modules'
        // content roots nest/overlap, the same drawable file is discovered once per module
        // in the chain -- it must still collapse to a single IconResource.
        val sharedFile = LightVirtualFile("app/src/main/res/drawable/home_location.xml")
        val viaRoot = DrawableCandidate(
            moduleName = "KYN",
            qualifierDirName = "drawable",
            baseName = "home_location",
            type = IconResourceType.VECTOR_DRAWABLE,
            file = sharedFile,
        )
        val viaAppModule = DrawableCandidate(
            moduleName = "KYN.app",
            qualifierDirName = "drawable",
            baseName = "home_location",
            type = IconResourceType.VECTOR_DRAWABLE,
            file = sharedFile,
        )
        val viaMainSourceSet = DrawableCandidate(
            moduleName = "KYN.app.main",
            qualifierDirName = "drawable",
            baseName = "home_location",
            type = IconResourceType.VECTOR_DRAWABLE,
            file = sharedFile,
        )

        val result = pickRepresentatives(listOf(viaRoot, viaAppModule, viaMainSourceSet))

        assertEquals(1, result.size)
        assertEquals("KYN.app.main", result.single().moduleName)
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
