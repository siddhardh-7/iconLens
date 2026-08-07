package io.github.siddhardh7.iconlens

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

private val LOG = logger<DrawableIconRenderer>()
internal const val RENDER_SIZE = 72

class UnsupportedRasterException(message: String) : Exception(message)

class DrawableIconRenderer : IconRenderer {

    override suspend fun render(resource: IconResource): RenderedIcon {
        return try {
            val bytes = readAction { resource.file.contentsToByteArray() }
            val image = when (resource.type) {
                IconResourceType.PNG, IconResourceType.JPEG, IconResourceType.WEBP -> decodeRaster(bytes)
                IconResourceType.VECTOR_DRAWABLE -> decodeVector(bytes)
            }
            RenderedIcon.Rendered(resource, image)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Failed to render ${resource.name}", e)
            RenderedIcon.Failed(resource, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun decodeRaster(bytes: ByteArray): BufferedImage {
        val source = ImageIO.read(bytes.inputStream())
            ?: throw UnsupportedRasterException("No ImageIO reader for this format")
        return scaleToSquare(source.width, source.height) { g ->
            g.drawImage(source, 0, 0, source.width, source.height, null)
        }
    }

    private fun decodeVector(bytes: ByteArray): BufferedImage =
        renderVectorDrawable(bytes.toString(Charsets.UTF_8), RENDER_SIZE)

    private fun scaleToSquare(sourceWidth: Int, sourceHeight: Int, draw: (Graphics2D) -> Unit): BufferedImage {
        val image = BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val fit = fitScaleAndOffset(sourceWidth.toDouble(), sourceHeight.toDouble(), RENDER_SIZE)
            g.translate(fit.offsetX, fit.offsetY)
            g.scale(fit.scale, fit.scale)
            draw(g)
        } finally {
            g.dispose()
        }
        return image
    }
}
