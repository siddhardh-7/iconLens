package io.github.siddhardh7.iconlens

import java.awt.image.BufferedImage

sealed interface RenderedIcon {
    val resource: IconResource

    data class Rendered(override val resource: IconResource, val image: BufferedImage) : RenderedIcon
    data class Failed(override val resource: IconResource, val reason: String) : RenderedIcon
}
