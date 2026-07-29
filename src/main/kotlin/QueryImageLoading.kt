package io.github.siddhardh7.iconlens

import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun loadQueryImageFromFile(file: File): QueryImage {
    return try {
        val image = ImageIO.read(file)
            ?: return QueryImage.Failed("No ImageIO reader for this file")
        QueryImage.Loaded(image, file.name)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        QueryImage.Failed(e.message ?: e.javaClass.simpleName)
    }
}

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
    else -> null
}

fun loadQueryImageFromClipboard(): QueryImage? {
    val transferable = Toolkit.getDefaultToolkit().systemClipboard.getContents(null) ?: return null
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
