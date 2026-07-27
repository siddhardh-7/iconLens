package io.github.siddhardh7.iconlens

internal fun resolveIconResourceType(fileName: String): IconResourceType? {
    if (fileName.endsWith(".9.png", ignoreCase = true)) return null
    return when (fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "xml" -> IconResourceType.VECTOR_DRAWABLE
        "png" -> IconResourceType.PNG
        "webp" -> IconResourceType.WEBP
        "jpg", "jpeg" -> IconResourceType.JPEG
        else -> null
    }
}
