package io.github.siddhardh7.iconlens

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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

    fun testRendersNonSquareRasterCenteredInsteadOfStretched() {
        val image = BufferedImage(4, 8, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = java.awt.Color.BLACK
        g.fillRect(0, 0, 4, 8)
        g.dispose()
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val file = myFixture.tempDirFixture.createFile("ic_tall.png", "")
        ApplicationManager.getApplication().runWriteAction { file.setBinaryContent(bytes) }
        val resource = IconResource("ic_tall", IconResourceType.PNG, file, "app")

        val result = runBlocking { DrawableIconRenderer().render(resource) } as RenderedIcon.Rendered

        // RENDER_SIZE is 48; a 4x8 source scaled to fit (limited by height) becomes 24 wide, 48
        // tall, centered horizontally with a 12px transparent margin on each side. Stretching to
        // fill the square would leave no margin at all.
        val alphaAtMargin = (result.image.getRGB(3, 24) ushr 24) and 0xFF
        val alphaAtCenter = (result.image.getRGB(24, 24) ushr 24) and 0xFF
        assertEquals(0, alphaAtMargin)
        assertEquals(255, alphaAtCenter)
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

    fun testPropagatesCancellationInsteadOfConvertingToFailed() {
        val file = writePngResource("ic_cancel_test.png")
        val resource = IconResource("ic_cancel_test", IconResourceType.PNG, file, "app")
        val renderer = DrawableIconRenderer()

        var threw = false
        runBlocking {
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    renderer.render(resource)
                } catch (e: CancellationException) {
                    threw = true
                    throw e
                }
            }
            job.cancelAndJoin()
        }
        assertTrue(threw)
    }
}
