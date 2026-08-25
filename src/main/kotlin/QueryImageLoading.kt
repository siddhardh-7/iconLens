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

/** The flavor-specific payload pulled out of a [Transferable], before any decoding. */
sealed interface QueryTransferPayload {
    data class RawImage(val image: Image) : QueryTransferPayload
    data class Files(val files: List<File>) : QueryTransferPayload
    data class Text(val text: String) : QueryTransferPayload
}

/**
 * Reads a [Transferable]'s flavor payload. For a drag-and-drop `TransferHandler.TransferSupport`,
 * this MUST be called synchronously inside `importData` — a drop's `Transferable` is only valid
 * for that call's duration (Swing calls `dropComplete()` right after `importData` returns), so
 * deferring this to a coroutine throws `java.awt.dnd.InvalidDnDOperationException`. The heavier
 * decode step ([queryImageFromTransferPayload]) has no such constraint and can run async.
 */
fun readQueryTransferPayload(transferable: Transferable): QueryTransferPayload? = when {
    transferable.isDataFlavorSupported(DataFlavor.imageFlavor) ->
        QueryTransferPayload.RawImage(transferable.getTransferData(DataFlavor.imageFlavor) as Image)
    transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
        @Suppress("UNCHECKED_CAST")
        QueryTransferPayload.Files(transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>)
    }
    transferable.isDataFlavorSupported(DataFlavor.stringFlavor) ->
        QueryTransferPayload.Text(transferable.getTransferData(DataFlavor.stringFlavor) as String)
    else -> null
}

fun queryImageFromTransferPayload(payload: QueryTransferPayload, imageSourceDescription: String): QueryImage? =
    try {
        when (payload) {
            is QueryTransferPayload.RawImage ->
                QueryImage.Loaded(payload.image.toBufferedImage(), imageSourceDescription)
            is QueryTransferPayload.Files -> {
                val first = payload.files.firstOrNull()
                if (first == null) QueryImage.Failed("No files in drop") else loadQueryImageFromFile(first)
            }
            is QueryTransferPayload.Text -> loadQueryImageFromMarkup(payload.text, imageSourceDescription)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        QueryImage.Failed(e.message ?: e.javaClass.simpleName)
    }

fun loadQueryImageFromTransferable(
    transferable: Transferable,
    imageSourceDescription: String = "Pasted image",
): QueryImage? {
    val payload = try {
        readQueryTransferPayload(transferable)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        return QueryImage.Failed(e.message ?: e.javaClass.simpleName)
    } ?: return null
    return queryImageFromTransferPayload(payload, imageSourceDescription)
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
