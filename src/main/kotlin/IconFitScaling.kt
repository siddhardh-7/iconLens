package io.github.siddhardh7.iconlens

data class FitTransform(val scale: Double, val offsetX: Double, val offsetY: Double)

/**
 * A uniform scale factor (limited by the larger dimension, never stretching width
 * and height independently) plus the x/y offset needed to center a
 * `sourceWidth` x `sourceHeight` region within a `targetSize` x `targetSize` square.
 */
fun fitScaleAndOffset(sourceWidth: Double, sourceHeight: Double, targetSize: Int): FitTransform {
    if (sourceWidth <= 0 || sourceHeight <= 0) {
        return FitTransform(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
    }
    val scale = targetSize / maxOf(sourceWidth, sourceHeight)
    val offsetX = (targetSize - sourceWidth * scale) / 2
    val offsetY = (targetSize - sourceHeight * scale) / 2
    return FitTransform(scale, offsetX, offsetY)
}
