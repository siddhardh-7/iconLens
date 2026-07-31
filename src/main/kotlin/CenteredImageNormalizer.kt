package io.github.siddhardh7.iconlens

import java.awt.Color
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage

internal const val NORMALIZED_SIZE = 64

class CenteredImageNormalizer : ImageNormalizer {

    override fun normalize(image: BufferedImage): NormalizedIcon {
        val bounds = contentBounds(image)
        val cropped = image.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height)

        val result = BufferedImage(NORMALIZED_SIZE, NORMALIZED_SIZE, BufferedImage.TYPE_INT_RGB)
        val g = result.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, NORMALIZED_SIZE, NORMALIZED_SIZE)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val fit = fitScaleAndOffset(cropped.width.toDouble(), cropped.height.toDouble(), NORMALIZED_SIZE)
            g.translate(fit.offsetX, fit.offsetY)
            g.scale(fit.scale, fit.scale)
            g.drawImage(cropped, 0, 0, null)
        } finally {
            g.dispose()
        }
        return NormalizedIcon(result)
    }

    private fun contentBounds(image: BufferedImage): Rectangle {
        var minX = image.width
        var minY = image.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val alpha = (image.getRGB(x, y) ushr 24) and 0xFF
                if (alpha != 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < minX || maxY < minY) {
            Rectangle(0, 0, image.width, image.height)
        } else {
            Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
        }
    }
}
