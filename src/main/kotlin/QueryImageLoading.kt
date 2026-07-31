package io.github.siddhardh7.iconlens

import com.intellij.util.SVGLoader
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

private const val QUERY_VECTOR_RENDER_SIZE = 128
private val SVG_TAG = Regex("<svg[\\s>]", RegexOption.IGNORE_CASE)
private val VECTOR_TAG = Regex("<vector[\\s>]", RegexOption.IGNORE_CASE)

fun loadQueryImageFromFile(file: File): QueryImage {
    return try {
        val image: BufferedImage = when (file.extension.lowercase()) {
            "xml" -> renderVectorDrawable(file.readText(), QUERY_VECTOR_RENDER_SIZE)
            "svg" -> renderSvg(file.readBytes())
            else -> ImageIO.read(file) ?: return QueryImage.Failed("No ImageIO reader for this file")
        }
        QueryImage.Loaded(image, file.name)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        QueryImage.Failed(e.message ?: e.javaClass.simpleName)
    }
}

private fun renderSvg(bytes: ByteArray): BufferedImage {
    val base = SVGLoader.load(null, ByteArrayInputStream(bytes), 1f)
    val largestDimension = maxOf(base.width, base.height)
    if (largestDimension >= QUERY_VECTOR_RENDER_SIZE) return base
    val scale = QUERY_VECTOR_RENDER_SIZE.toFloat() / largestDimension
    return SVGLoader.load(null, ByteArrayInputStream(bytes), scale)
}

/** Recognizes raw SVG/VectorDrawable markup pasted as plain text (e.g. a "copy SVG code" clipboard action). */
private fun loadQueryImageFromMarkup(text: String, sourceDescription: String): QueryImage? {
    val head = text.take(500)
    val image = when {
        SVG_TAG.containsMatchIn(head) -> renderSvg(text.toByteArray())
        VECTOR_TAG.containsMatchIn(head) -> renderVectorDrawable(text, QUERY_VECTOR_RENDER_SIZE)
        else -> return null
    }
    return QueryImage.Loaded(image, sourceDescription)
}

val SUPPORTED_TRANSFERABLE_FLAVORS =
    listOf(DataFlavor.imageFlavor, DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor)

fun loadQueryImageFromTransferable(
    transferable: Transferable,
    imageSourceDescription: String = "Pasted image",
): QueryImage? = when {
    transferable.isDataFlavorSupported(DataFlavor.imageFlavor) -> try {
        val awtImage = transferable.getTransferData(DataFlavor.imageFlavor) as Image
        QueryImage.Loaded(awtImage.toBufferedImage(), imageSourceDescription)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        QueryImage.Failed(e.message ?: e.javaClass.simpleName)
    }
    transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> try {
        @Suppress("UNCHECKED_CAST")
        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
        val first = files.firstOrNull()
        if (first == null) QueryImage.Failed("No files in drop") else loadQueryImageFromFile(first)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        QueryImage.Failed(e.message ?: e.javaClass.simpleName)
    }
    transferable.isDataFlavorSupported(DataFlavor.stringFlavor) -> try {
        val text = transferable.getTransferData(DataFlavor.stringFlavor) as String
        loadQueryImageFromMarkup(text, imageSourceDescription)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        QueryImage.Failed(e.message ?: e.javaClass.simpleName)
    }
    else -> null
}

fun loadQueryImageFromClipboard(): QueryImage? {
    val transferable = try {
        Toolkit.getDefaultToolkit().systemClipboard.getContents(null) ?: return null
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        return QueryImage.Failed(e.message ?: e.javaClass.simpleName)
    }
    return loadQueryImageFromTransferable(transferable, "Pasted from clipboard")
}

private fun Image.toBufferedImage(): BufferedImage {
    if (this is BufferedImage) return this
    val buffered = BufferedImage(getWidth(null), getHeight(null), BufferedImage.TYPE_INT_ARGB)
    val g = buffered.createGraphics()
    try {
        g.drawImage(this, 0, 0, null)
    } finally {
        g.dispose()
    }
    return buffered
}
