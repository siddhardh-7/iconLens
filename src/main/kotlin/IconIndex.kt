package io.github.siddhardh7.iconlens

import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Service(Service.Level.PROJECT)
class IconIndex {
    private val mutex = Mutex()
    private var cache: Map<ResourceKey, CachedEntry> = emptyMap()

    private data class ResourceKey(val moduleName: String, val name: String)
    private data class CachedEntry(val icon: RenderedIcon, val file: VirtualFile, val modificationStamp: Long)

    suspend fun refresh(source: IconSource, renderer: IconRenderer): List<RenderedIcon> = mutex.withLock {
        val discovered = source.discover()
        val updated = LinkedHashMap<ResourceKey, CachedEntry>(discovered.size)
        for (resource in discovered) {
            val key = ResourceKey(resource.moduleName, resource.name)
            val existing = cache[key]
            val icon = if (existing != null && existing.file == resource.file &&
                existing.modificationStamp == resource.file.modificationStamp
            ) {
                existing.icon
            } else {
                renderer.render(resource)
            }
            updated[key] = CachedEntry(icon, resource.file, resource.file.modificationStamp)
        }
        cache = updated
        updated.values.map { it.icon }
    }
}
