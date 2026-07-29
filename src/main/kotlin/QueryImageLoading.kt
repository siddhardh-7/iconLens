package io.github.siddhardh7.iconlens

import java.io.File
import javax.imageio.ImageIO

fun loadQueryImageFromFile(file: File): QueryImage {
    return try {
        val image = ImageIO.read(file)
            ?: return QueryImage.Failed("No ImageIO reader for this file")
        QueryImage.Loaded(image, file.name)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        QueryImage.Failed(e.message ?: e.javaClass.simpleName)
    }
}
