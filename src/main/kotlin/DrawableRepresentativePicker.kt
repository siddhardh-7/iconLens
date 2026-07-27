package io.github.siddhardh7.iconlens

import com.intellij.openapi.vfs.VirtualFile

internal data class DrawableCandidate(
    val moduleName: String,
    val qualifierDirName: String,
    val baseName: String,
    val type: IconResourceType,
    val file: VirtualFile,
)

private const val PLAIN_DRAWABLE_DIR = "drawable"

internal fun pickRepresentatives(candidates: List<DrawableCandidate>): List<IconResource> {
    return candidates
        .groupBy { it.moduleName to it.baseName }
        .map { (_, group) ->
            val winner = group.find { it.qualifierDirName == PLAIN_DRAWABLE_DIR }
                ?: group.minByOrNull { it.qualifierDirName }!!
            IconResource(
                name = winner.baseName,
                type = winner.type,
                file = winner.file,
                moduleName = winner.moduleName,
            )
        }
}
