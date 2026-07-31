package io.github.siddhardh7.iconlens

import java.awt.BasicStroke
import java.awt.RenderingHints
import java.awt.image.BufferedImage

fun renderVectorDrawable(xml: String, size: Int): BufferedImage {
    val shape = parseVectorDrawable(xml)
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        if (shape.viewportWidth > 0 && shape.viewportHeight > 0) {
            val scale = size / maxOf(shape.viewportWidth, shape.viewportHeight)
            g.translate((size - shape.viewportWidth * scale) / 2, (size - shape.viewportHeight * scale) / 2)
            g.scale(scale, scale)
        }
        val defaultClip = g.clip
        for (styledPath in shape.paths) {
            g.clip = styledPath.clip ?: defaultClip
            if (styledPath.fillPaint != null) {
                g.paint = styledPath.fillPaint
                g.fill(styledPath.path)
            }
            if (styledPath.strokeColor != null && styledPath.strokeWidth > 0f) {
                g.paint = styledPath.strokeColor
                g.stroke = BasicStroke(styledPath.strokeWidth)
                g.draw(styledPath.path)
            }
        }
        g.clip = defaultClip
    } finally {
        g.dispose()
    }
    return image
}
