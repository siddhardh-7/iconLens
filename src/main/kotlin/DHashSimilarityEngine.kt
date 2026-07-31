package io.github.siddhardh7.iconlens

import java.awt.RenderingHints
import java.awt.image.BufferedImage

private const val HASH_WIDTH = 9
private const val HASH_HEIGHT = 8

class DHashSimilarityEngine : SimilarityEngine {

    override fun describe(icon: NormalizedIcon): IconDescriptor {
        val small = BufferedImage(HASH_WIDTH, HASH_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = small.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(icon.image, 0, 0, HASH_WIDTH, HASH_HEIGHT, null)
        } finally {
            g.dispose()
        }

        var hash = 0L
        for (y in 0 until HASH_HEIGHT) {
            for (x in 0 until HASH_WIDTH - 1) {
                val left = grayscale(small.getRGB(x, y))
                val right = grayscale(small.getRGB(x + 1, y))
                if (left > right) {
                    hash = hash or (1L shl (y * (HASH_WIDTH - 1) + x))
                }
            }
        }
        return IconDescriptor(hash)
    }

    override fun score(a: IconDescriptor, b: IconDescriptor): Double {
        val hammingDistance = java.lang.Long.bitCount(a.hash xor b.hash)
        return 1.0 - hammingDistance / 64.0
    }

    private fun grayscale(rgb: Int): Int {
        val r = (rgb ushr 16) and 0xFF
        val g = (rgb ushr 8) and 0xFF
        val b = rgb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
