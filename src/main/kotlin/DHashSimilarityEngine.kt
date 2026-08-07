package io.github.siddhardh7.iconlens

import java.awt.RenderingHints
import java.awt.image.BufferedImage

// Two directional grids, each contributing 16*16=256 bits (512 total): comparing adjacent
// pixels row-wise (horizontal grid) and column-wise (vertical grid). A single row-wise-only
// dHash over a small grid collapses most simple/flat icon shapes into near-identical hashes;
// doubling the bit budget and comparing both directions gives much finer-grained, more
// discriminative scores without introducing any ML/cloud dependency.
private const val HORIZONTAL_GRID_WIDTH = 17
private const val HORIZONTAL_GRID_HEIGHT = 16
private const val VERTICAL_GRID_WIDTH = 16
private const val VERTICAL_GRID_HEIGHT = 17
private const val BITS_PER_LONG = 64

class DHashSimilarityEngine : SimilarityEngine {

    override fun describe(icon: NormalizedIcon): IconDescriptor {
        val horizontalBits = directionalBits(icon.image, HORIZONTAL_GRID_WIDTH, HORIZONTAL_GRID_HEIGHT, horizontal = true)
        val verticalBits = directionalBits(icon.image, VERTICAL_GRID_WIDTH, VERTICAL_GRID_HEIGHT, horizontal = false)
        return IconDescriptor(packBits(horizontalBits + verticalBits))
    }

    override fun score(a: IconDescriptor, b: IconDescriptor): Double {
        val totalBits = a.hash.size * BITS_PER_LONG
        val hammingDistance = a.hash.indices.sumOf { java.lang.Long.bitCount(a.hash[it] xor b.hash[it]) }
        return 1.0 - hammingDistance.toDouble() / totalBits
    }

    private fun directionalBits(
        source: BufferedImage,
        gridWidth: Int,
        gridHeight: Int,
        horizontal: Boolean,
    ): List<Boolean> {
        val small = BufferedImage(gridWidth, gridHeight, BufferedImage.TYPE_INT_RGB)
        val g = small.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(source, 0, 0, gridWidth, gridHeight, null)
        } finally {
            g.dispose()
        }

        val bits = mutableListOf<Boolean>()
        if (horizontal) {
            for (y in 0 until gridHeight) {
                for (x in 0 until gridWidth - 1) {
                    bits += grayscale(small.getRGB(x, y)) > grayscale(small.getRGB(x + 1, y))
                }
            }
        } else {
            for (x in 0 until gridWidth) {
                for (y in 0 until gridHeight - 1) {
                    bits += grayscale(small.getRGB(x, y)) > grayscale(small.getRGB(x, y + 1))
                }
            }
        }
        return bits
    }

    private fun packBits(bits: List<Boolean>): List<Long> {
        val longs = LongArray((bits.size + BITS_PER_LONG - 1) / BITS_PER_LONG)
        for (i in bits.indices) {
            if (bits[i]) longs[i / BITS_PER_LONG] = longs[i / BITS_PER_LONG] or (1L shl (i % BITS_PER_LONG))
        }
        return longs.toList()
    }

    private fun grayscale(rgb: Int): Int {
        val r = (rgb ushr 16) and 0xFF
        val g = (rgb ushr 8) and 0xFF
        val b = rgb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
