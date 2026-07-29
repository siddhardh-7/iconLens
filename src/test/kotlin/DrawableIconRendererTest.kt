package io.github.siddhardh7.iconlens

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class DrawableIconRendererTest : BasePlatformTestCase() {

    private fun writePngResource(relativePath: String): VirtualFile {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val file = myFixture.tempDirFixture.createFile(relativePath, "")
        ApplicationManager.getApplication().runWriteAction { file.setBinaryContent(bytes) }
        return file
    }

    fun testRendersValidPngResource() {
        val file = writePngResource("ic_test.png")
        val resource = IconResource("ic_test", IconResourceType.PNG, file, "app")

        val result = runBlocking { DrawableIconRenderer().render(resource) }

        assertTrue(result is RenderedIcon.Rendered)
    }

    fun testRendersValidVectorDrawableResource() {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="24" android:viewportHeight="24">
                <path android:fillColor="#000000" android:pathData="M0,0 L24,0 L24,24 L0,24 Z"/>
            </vector>
        """.trimIndent()
        myFixture.tempDirFixture.createFile("ic_vector.xml", xml)
        val file = myFixture.tempDirFixture.getFile("ic_vector.xml")!!
        val resource = IconResource("ic_vector", IconResourceType.VECTOR_DRAWABLE, file, "app")

        val result = runBlocking { DrawableIconRenderer().render(resource) }

        assertTrue(result is RenderedIcon.Rendered)
    }

    fun testFailsGracefullyOnMalformedVectorDrawable() {
        myFixture.tempDirFixture.createFile("ic_broken.xml", "<vector><path")
        val file = myFixture.tempDirFixture.getFile("ic_broken.xml")!!
        val resource = IconResource("ic_broken", IconResourceType.VECTOR_DRAWABLE, file, "app")

        val result = runBlocking { DrawableIconRenderer().render(resource) }

        assertTrue(result is RenderedIcon.Failed)
    }

    fun testFailsGracefullyOnCorruptRasterFile() {
        myFixture.tempDirFixture.createFile("ic_corrupt.png", "not a real png")
        val file = myFixture.tempDirFixture.getFile("ic_corrupt.png")!!
        val resource = IconResource("ic_corrupt", IconResourceType.PNG, file, "app")

        val result = runBlocking { DrawableIconRenderer().render(resource) }

        assertTrue(result is RenderedIcon.Failed)
    }
}
