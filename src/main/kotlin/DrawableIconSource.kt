package io.github.siddhardh7.iconlens

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor

private val LOG = logger<DrawableIconSource>()
private val EXCLUDED_DIR_NAMES = setOf("build", ".git", ".gradle", ".idea")
private val DRAWABLE_DIR_REGEX = Regex("^drawable(-.+)?$")

class DrawableIconSource(private val project: Project) : IconSource {

    override suspend fun discover(): List<IconResource> {
        val candidates = readAction {
            ModuleManager.getInstance(project).modules.flatMap(::collectCandidatesForModule)
        }
        return pickRepresentatives(candidates)
    }

    private fun collectCandidatesForModule(module: Module): List<DrawableCandidate> {
        val candidates = mutableListOf<DrawableCandidate>()
        for (contentRoot in ModuleRootManager.getInstance(module).contentRoots) {
            VfsUtilCore.visitChildrenRecursively(contentRoot, object : VirtualFileVisitor<Unit>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory) return true
                    if (file.name in EXCLUDED_DIR_NAMES) return false
                    if (file.name == "res") {
                        collectDrawableDirs(file, module.name, candidates)
                        return false
                    }
                    return true
                }
            })
        }
        return candidates
    }

    private fun collectDrawableDirs(resDir: VirtualFile, moduleName: String, out: MutableList<DrawableCandidate>) {
        for (qualifierDir in resDir.children) {
            try {
                if (!qualifierDir.isDirectory || !DRAWABLE_DIR_REGEX.matches(qualifierDir.name)) continue
                for (file in qualifierDir.children) {
                    try {
                        if (file.isDirectory) continue
                        val type = resolveIconResourceType(file.name) ?: continue
                        out += DrawableCandidate(
                            moduleName = moduleName,
                            qualifierDirName = qualifierDir.name,
                            baseName = file.name.substringBeforeLast('.'),
                            type = type,
                            file = file,
                        )
                    } catch (e: Exception) {
                        LOG.warn("Skipping unreadable drawable candidate: ${file.path}", e)
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Skipping unreadable drawable directory: ${qualifierDir.path}", e)
            }
        }
    }
}
