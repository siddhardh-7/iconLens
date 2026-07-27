package io.github.siddhardh7.iconlens

import com.intellij.openapi.vfs.VirtualFile

enum class IconResourceType { VECTOR_DRAWABLE, PNG, WEBP, JPEG }

data class IconResource(
    val name: String,
    val type: IconResourceType,
    val file: VirtualFile,
    val moduleName: String,
)
