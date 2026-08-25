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

    @Test
    fun `loadQueryImageFromFile decodes a VectorDrawable XML file`() {
        val file = File.createTempFile("query", ".xml")
        file.deleteOnExit()
        file.writeText(
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="24" android:viewportHeight="24">
                <path android:fillColor="#FF0000" android:pathData="M0,0 L24,0 L24,24 L0,24 Z"/>
            </vector>
            """.trimIndent(),
        )

        val result = loadQueryImageFromFile(file)

        assertTrue(result is QueryImage.Loaded)
        assertEquals(file.name, (result as QueryImage.Loaded).sourceDescription)
    }

    @Test
    fun `loadQueryImageFromFile fails gracefully on malformed VectorDrawable XML`() {
        val file = File.createTempFile("query", ".xml")
        file.deleteOnExit()
        file.writeText("<not-a-vector/>")

        val result = loadQueryImageFromFile(file)

        assertTrue(result is QueryImage.Failed)
    }

    @Test
    fun `loadQueryImageFromFile decodes an SVG file`() {
        val file = File.createTempFile("query", ".svg")
        file.deleteOnExit()
        file.writeText(
            """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                <circle cx="12" cy="12" r="10" fill="#00A0FF"/>
            </svg>
            """.trimIndent(),
        )

        val result = loadQueryImageFromFile(file)

        assertTrue(result is QueryImage.Loaded)
        assertEquals(file.name, (result as QueryImage.Loaded).sourceDescription)
    }

    @Test
    fun `loadQueryImageFromFile upscales an SVG with no explicit width or height`() {
        val file = File.createTempFile("query", ".svg")
        file.deleteOnExit()
        file.writeText(
            """<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M20 12L4 12M20 12L14 18M20 12L14 6" stroke="#000000" stroke-width="1.5"/>
            </svg>
            """.trimIndent(),
        )

        val result = loadQueryImageFromFile(file)

        assertTrue(result is QueryImage.Loaded)
        val image = (result as QueryImage.Loaded).image
        assertTrue(image.width >= 128 && image.height >= 128)
    }

    @Test
    fun `loadQueryImageFromFile fails gracefully on malformed SVG`() {
        val file = File.createTempFile("query", ".svg")
        file.deleteOnExit()
        file.writeText("not real svg content")

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

    @Test
    fun `loadQueryImageFromTransferable decodes SVG markup pasted as plain text`() {
        val svgText = """<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <circle cx="12" cy="12" r="10" fill="#00A0FF"/>
        </svg>
        """.trimIndent()
        val transferable = FakeTransferable(
            listOf(java.awt.datatransfer.DataFlavor.stringFlavor),
            mapOf(java.awt.datatransfer.DataFlavor.stringFlavor to svgText),
        )

        val result = loadQueryImageFromTransferable(transferable, "Pasted from clipboard")

        assertTrue(result is QueryImage.Loaded)
    }

    @Test
    fun `loadQueryImageFromTransferable decodes VectorDrawable XML markup pasted as plain text`() {
        val xmlText = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:viewportWidth="24" android:viewportHeight="24">
            <path android:fillColor="#FF0000" android:pathData="M0,0 L24,0 L24,24 L0,24 Z"/>
        </vector>
        """.trimIndent()
        val transferable = FakeTransferable(
            listOf(java.awt.datatransfer.DataFlavor.stringFlavor),
            mapOf(java.awt.datatransfer.DataFlavor.stringFlavor to xmlText),
        )

        val result = loadQueryImageFromTransferable(transferable, "Pasted from clipboard")

        assertTrue(result is QueryImage.Loaded)
    }

    @Test
    fun `loadQueryImageFromTransferable returns null for plain text that is not image markup`() {
        val transferable = FakeTransferable(
            listOf(java.awt.datatransfer.DataFlavor.stringFlavor),
            mapOf(java.awt.datatransfer.DataFlavor.stringFlavor to "just some copied text"),
        )

        val result = loadQueryImageFromTransferable(transferable, "Pasted from clipboard")

        assertEquals(null, result)
    }

    /** Throws like a real drop's Transferable does once the drop callback has returned. */
    private class ExpiringTransferable(
        private val flavor: java.awt.datatransfer.DataFlavor,
        private val data: Any,
    ) : java.awt.datatransfer.Transferable {
        private var reads = 0
        override fun getTransferDataFlavors() = arrayOf(flavor)
        override fun isDataFlavorSupported(f: java.awt.datatransfer.DataFlavor) = f == flavor
        override fun getTransferData(f: java.awt.datatransfer.DataFlavor): Any {
            reads++
            if (reads > 1) throw java.awt.dnd.InvalidDnDOperationException("transferable expired")
            return data
        }
    }

    @Test
    fun `queryImageFromTransferPayload decodes without reading an already-consumed transferable again`() {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        val transferable = ExpiringTransferable(java.awt.datatransfer.DataFlavor.imageFlavor, image)

        // The synchronous read a drop's TransferHandler.importData must do immediately.
        val payload = readQueryTransferPayload(transferable)
        assertTrue(payload is QueryTransferPayload.RawImage)

        // Decoding runs later (e.g. on a background coroutine); by then a real drop's
        // Transferable would throw InvalidDnDOperationException on any further read. Decoding
        // must not need one.
        val result = queryImageFromTransferPayload(payload!!, "Dropped image")

        assertTrue(result is QueryImage.Loaded)
    }
}
