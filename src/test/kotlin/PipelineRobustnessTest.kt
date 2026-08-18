package io.github.siddhardh7.iconlens

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PipelineRobustnessTest : BasePlatformTestCase() {

    private fun pngResource(name: String): IconResource {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val file = myFixture.tempDirFixture.createFile("$name.png", "")
        ApplicationManager.getApplication().runWriteAction { file.setBinaryContent(bytes) }
        return IconResource(name, IconResourceType.PNG, file, "app")
    }

    private fun vectorResource(name: String): IconResource {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="24" android:viewportHeight="24">
                <path android:fillColor="#000000" android:pathData="M0,0 L24,0 L24,24 L0,24 Z"/>
            </vector>
        """.trimIndent()
        myFixture.tempDirFixture.createFile("$name.xml", xml)
        val file = myFixture.tempDirFixture.getFile("$name.xml")!!
        return IconResource(name, IconResourceType.VECTOR_DRAWABLE, file, "app")
    }

    private fun corruptPngResource(name: String): IconResource {
        myFixture.tempDirFixture.createFile("$name.png", "not a real png")
        val file = myFixture.tempDirFixture.getFile("$name.png")!!
        return IconResource(name, IconResourceType.PNG, file, "app")
    }

    private fun malformedVectorResource(name: String): IconResource {
        myFixture.tempDirFixture.createFile("$name.xml", "<vector><path")
        val file = myFixture.tempDirFixture.getFile("$name.xml")!!
        return IconResource(name, IconResourceType.VECTOR_DRAWABLE, file, "app")
    }

    private fun zeroByteResource(name: String): IconResource {
        myFixture.tempDirFixture.createFile("$name.png", "")
        val file = myFixture.tempDirFixture.getFile("$name.png")!!
        return IconResource(name, IconResourceType.PNG, file, "app")
    }

    fun testMalformedResourcesFailInIsolationWithinABatch() {
        val validResources = (1..7).map { pngResource("ic_valid_$it") } + vectorResource("ic_valid_vector")
        val malformedResources = listOf(
            corruptPngResource("ic_corrupt"),
            malformedVectorResource("ic_broken_vector"),
            zeroByteResource("ic_empty"),
        )
        val renderer = DrawableIconRenderer()

        val results = runBlocking { (validResources + malformedResources).map { renderer.render(it) } }

        val byName = results.associateBy { it.resource.name }
        validResources.forEach {
            assertTrue("${it.name} should render", byName.getValue(it.name) is RenderedIcon.Rendered)
        }
        malformedResources.forEach {
            assertTrue("${it.name} should fail", byName.getValue(it.name) is RenderedIcon.Failed)
        }
    }
}
