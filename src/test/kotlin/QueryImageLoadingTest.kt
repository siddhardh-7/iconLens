package io.github.siddhardh7.iconlens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class QueryImageLoadingTest {

    @Test
    fun `loadQueryImageFromFile decodes a valid PNG`() {
        val file = File.createTempFile("query", ".png")
        file.deleteOnExit()
        ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", file)

        val result = loadQueryImageFromFile(file)

        assertTrue(result is QueryImage.Loaded)
        assertEquals(file.name, (result as QueryImage.Loaded).sourceDescription)
    }

    @Test
    fun `loadQueryImageFromFile fails gracefully on corrupt file`() {
        val file = File.createTempFile("query", ".png")
        file.deleteOnExit()
        file.writeText("not a real png")

        val result = loadQueryImageFromFile(file)

        assertTrue(result is QueryImage.Failed)
    }

    private class FakeTransferable(
        private val flavors: List<java.awt.datatransfer.DataFlavor>,
        private val data: Map<java.awt.datatransfer.DataFlavor, Any>,
        private val throwOn: java.awt.datatransfer.DataFlavor? = null,
    ) : java.awt.datatransfer.Transferable {
        override fun getTransferDataFlavors() = flavors.toTypedArray()
        override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor) = flavor in flavors
        override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor): Any {
            if (flavor == throwOn) throw java.io.IOException("simulated failure")
            return data[flavor] ?: throw java.awt.datatransfer.UnsupportedFlavorException(flavor)
        }
    }

    @Test
    fun `loadQueryImageFromTransferable decodes supported image flavor`() {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        val transferable = FakeTransferable(
            listOf(java.awt.datatransfer.DataFlavor.imageFlavor),
            mapOf(java.awt.datatransfer.DataFlavor.imageFlavor to image),
        )

        val result = loadQueryImageFromTransferable(transferable, "Dropped image")

        assertTrue(result is QueryImage.Loaded)
        assertEquals("Dropped image", (result as QueryImage.Loaded).sourceDescription)
    }

    @Test
    fun `loadQueryImageFromTransferable fails gracefully when image flavor transfer throws`() {
        val transferable = FakeTransferable(
            listOf(java.awt.datatransfer.DataFlavor.imageFlavor),
            emptyMap(),
            throwOn = java.awt.datatransfer.DataFlavor.imageFlavor,
        )

        val result = loadQueryImageFromTransferable(transferable, "Dropped image")

        assertTrue(result is QueryImage.Failed)
    }

    @Test
    fun `loadQueryImageFromTransferable delegates file list flavor to file loading`() {
        val file = File.createTempFile("query", ".png")
        file.deleteOnExit()
        ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", file)
        val transferable = FakeTransferable(
            listOf(java.awt.datatransfer.DataFlavor.javaFileListFlavor),
            mapOf(java.awt.datatransfer.DataFlavor.javaFileListFlavor to listOf(file)),
        )

        val result = loadQueryImageFromTransferable(transferable, "Dropped image")

        assertTrue(result is QueryImage.Loaded)
        assertEquals(file.name, (result as QueryImage.Loaded).sourceDescription)
    }

    @Test
    fun `loadQueryImageFromTransferable returns null when neither flavor is supported`() {
        val transferable = FakeTransferable(emptyList(), emptyMap())

        val result = loadQueryImageFromTransferable(transferable, "Dropped image")

        assertEquals(null, result)
    }
}
