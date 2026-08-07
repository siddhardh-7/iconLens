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
    // Android Gradle sync represents one Gradle module as several IntelliJ modules (root
    // project, the app module, its main source set, ...). When those modules' content roots
    // nest/overlap, the same physical file is discovered once per module in the chain. Collapse
    // those first, keeping the most specific (deepest/longest) module name as the true owner,
    // before the per-module density-variant dedup below -- otherwise a single drawable shows up
    // once per module in the nesting chain instead of once.
    val dedupedAcrossNestedModules = candidates
        .groupBy { it.file.path }
        .map { (_, duplicates) -> duplicates.maxBy { it.moduleName.length } }

    return dedupedAcrossNestedModules
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
