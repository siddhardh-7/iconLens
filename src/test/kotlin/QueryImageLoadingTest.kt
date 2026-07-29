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
}
