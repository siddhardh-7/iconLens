package io.github.siddhardh7.iconlens

import java.awt.image.BufferedImage

sealed interface QueryImage {
    data class Loaded(val image: BufferedImage, val sourceDescription: String) : QueryImage
    data class Failed(val reason: String) : QueryImage
}
