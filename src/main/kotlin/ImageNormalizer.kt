package io.github.siddhardh7.iconlens

import java.awt.image.BufferedImage

interface ImageNormalizer {
    fun normalize(image: BufferedImage): NormalizedIcon
}
