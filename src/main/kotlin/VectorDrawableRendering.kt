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
            g.scale(size / shape.viewportWidth, size / shape.viewportHeight)
        }
        for (styledPath in shape.paths) {
            if (styledPath.fillColor != null) {
                g.color = styledPath.fillColor
                g.fill(styledPath.path)
            }
            if (styledPath.strokeColor != null && styledPath.strokeWidth > 0f) {
                g.color = styledPath.strokeColor
                g.stroke = BasicStroke(styledPath.strokeWidth)
                g.draw(styledPath.path)
            }
        }
    } finally {
        g.dispose()
    }
    return image
}
