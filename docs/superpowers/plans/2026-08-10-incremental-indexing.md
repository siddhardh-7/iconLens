# M9 Incremental Indexing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the gallery's refresh incremental — only re-render drawables that were actually added, changed, or removed since the last refresh, instead of re-discovering and re-rendering every resource in the project every time, per `ROADMAP.md`'s M9 bullets.

**Architecture:** A new project-level service `IconIndex` caches the last-rendered `RenderedIcon` per resource, keyed by `(moduleName, name)` (not by `VirtualFile`, since the representative file for a given logical resource can change between refreshes). On each `refresh(source, renderer)` call it re-runs `source.discover()` (cheap VFS walk — this is how additions/deletions are noticed) and, for each discovered resource, reuses the cached `RenderedIcon` if the resource's file and `VirtualFile.modificationStamp` are unchanged, otherwise renders it fresh. `IconLensToolWindowFactory.kt`'s `refresh()` swaps its one call to the now-dead `loadGallery()` for `project.service<IconIndex>().refresh(...)` — everything downstream (EDT hop, re-ranking, `listModel` update) is untouched.

**Tech Stack:** Kotlin/JVM, IntelliJ Platform SDK (`@Service(Service.Level.PROJECT)`, `com.intellij.openapi.components.service`), `kotlinx.coroutines.sync.Mutex`, JUnit 4 with the existing `LightVirtualFile`/fake-source/fake-renderer test-double pattern.

## Global Constraints

- Package: everything lives in `io.github.siddhardh7.iconlens` (single package).
- Out of scope for this milestone (do not implement): VFS/file-change listeners or any other automatic-refresh trigger; caching `IconDescriptor`s or changing anything in `SimilarityRanking.kt`; persisting the index to disk. Refresh stays manual (toolbar button + the one startup call) — only the work `refresh()` does becomes incremental.
- Cache key is `(moduleName: String, name: String)` — never the `VirtualFile` alone. This is what makes a representative-variant swap (same logical resource, new backing file) register as an *update* rather than a delete-then-add.
- Change detection compares both `VirtualFile` equality and `VirtualFile.modificationStamp` against what's cached. If either differs, re-render.
- `IconIndex` must be safe against overlapping `refresh()` calls (e.g. a fast double-click on the refresh button): guard the whole discover-diff-update sequence with a `kotlinx.coroutines.sync.Mutex`, so a second concurrent call suspends rather than racing.
- Verified platform API signatures (confirmed against this project's actual target-version platform jar — do not deviate):
  - `@com.intellij.openapi.components.Service(com.intellij.openapi.components.Service.Level.PROJECT)` on a class with a no-arg constructor registers a project-scoped light service.
  - `com.intellij.openapi.components.service<T>()` — Kotlin extension on `ComponentManager` (which `Project` implements); call as `project.service<IconIndex>()`.
  - `com.intellij.openapi.vfs.VirtualFile.getModificationStamp(): Long` (Kotlin property `modificationStamp`) — already indirectly available on `IconResource.file`.
  - `com.intellij.testFramework.LightVirtualFile` does **not** expose a public `setModificationStamp` (it's `protected` on `LightVirtualFileBase`) — tests that need to bump a stamp on an existing fake file must use a small subclass that overrides it as `public`, shown in Task 1.
- Before calling the milestone done: `./gradlew build` and `./gradlew test` must both pass, and `ROADMAP.md`/`ARCHITECTURE.md` must be updated (per `CLAUDE.md`).
- Full spec: `docs/superpowers/specs/2026-08-10-incremental-indexing-design.md`.

---

### Task 1: `IconIndex` — incremental cache, fully unit-tested

**Files:**
- Create: `src/main/kotlin/IconIndex.kt`
- Create: `src/test/kotlin/IconIndexTest.kt`

**Interfaces:**
- Consumes: `IconSource` (`suspend fun discover(): List<IconResource>`), `IconRenderer` (`suspend fun render(resource: IconResource): RenderedIcon`), `IconResource(name, type, file, moduleName)`, `RenderedIcon` (existing, all unchanged).
- Produces: `@Service(Service.Level.PROJECT) class IconIndex { suspend fun refresh(source: IconSource, renderer: IconRenderer): List<RenderedIcon> }` — consumed by Task 2's `IconLensToolWindowFactory.kt`.

This task is fully self-contained and independently testable: no wiring into the UI yet.

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/IconIndexTest.kt`:

```kotlin
package io.github.siddhardh7.iconlens

import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.awt.image.BufferedImage

class IconIndexTest {

    /** Exposes the otherwise-protected modification stamp setter, for the "file changed" test. */
    private class MutableStampFile(name: String) : LightVirtualFile(name) {
        override fun setModificationStamp(modificationStamp: Long) {
            super.setModificationStamp(modificationStamp)
        }
    }

    private fun resource(name: String, file: LightVirtualFile = LightVirtualFile(name), moduleName: String = "app") =
        IconResource(name, IconResourceType.PNG, file, moduleName)

    private class FakeIconSource(private val resources: List<IconResource>) : IconSource {
        override suspend fun discover() = resources
    }

    private class CountingRenderer : IconRenderer {
        val renderCounts = mutableMapOf<String, Int>()

        override suspend fun render(resource: IconResource): RenderedIcon {
            renderCounts[resource.name] = (renderCounts[resource.name] ?: 0) + 1
            return RenderedIcon.Rendered(resource, BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
        }
    }

    @Test
    fun `first refresh renders every discovered resource`() {
        val renderer = CountingRenderer()
        val resources = listOf(resource("ic_calendar"), resource("ic_close"))

        val result = runBlocking { IconIndex().refresh(FakeIconSource(resources), renderer) }

        assertEquals(2, result.size)
        assertEquals(setOf("ic_calendar", "ic_close"), result.map { it.resource.name }.toSet())
        assertEquals(1, renderer.renderCounts["ic_calendar"])
        assertEquals(1, renderer.renderCounts["ic_close"])
    }

    @Test
    fun `second refresh with unchanged files reuses cached icons without re-rendering`() {
        val renderer = CountingRenderer()
        val resources = listOf(resource("ic_calendar"))
        val index = IconIndex()

        val first = runBlocking { index.refresh(FakeIconSource(resources), renderer) }
        val second = runBlocking { index.refresh(FakeIconSource(resources), renderer) }

        assertSame(first[0], second[0])
        assertEquals(1, renderer.renderCounts["ic_calendar"])
    }

    @Test
    fun `a resource whose file was modified since the last refresh is re-rendered`() {
        val renderer = CountingRenderer()
        val file = MutableStampFile("ic_calendar")
        val resources = listOf(resource("ic_calendar", file))
        val index = IconIndex()

        runBlocking { index.refresh(FakeIconSource(resources), renderer) }
        file.setModificationStamp(file.modificationStamp + 1)
        runBlocking { index.refresh(FakeIconSource(resources), renderer) }

        assertEquals(2, renderer.renderCounts["ic_calendar"])
    }

    @Test
    fun `a resource no longer discovered is dropped from the result`() {
        val renderer = CountingRenderer()
        val index = IconIndex()

        val first = runBlocking {
            index.refresh(FakeIconSource(listOf(resource("ic_calendar"), resource("ic_close"))), renderer)
        }
        val second = runBlocking {
            index.refresh(FakeIconSource(listOf(resource("ic_calendar"))), renderer)
        }

        assertEquals(2, first.size)
        assertEquals(listOf("ic_calendar"), second.map { it.resource.name })
    }

    @Test
    fun `a newly discovered resource is rendered and added`() {
        val renderer = CountingRenderer()
        val index = IconIndex()

        runBlocking { index.refresh(FakeIconSource(listOf(resource("ic_calendar"))), renderer) }
        val second = runBlocking {
            index.refresh(FakeIconSource(listOf(resource("ic_calendar"), resource("ic_close"))), renderer)
        }

        assertEquals(setOf("ic_calendar", "ic_close"), second.map { it.resource.name }.toSet())
        assertEquals(1, renderer.renderCounts["ic_close"])
    }

    @Test
    fun `a representative-variant swap updates the same logical resource in place`() {
        val renderer = CountingRenderer()
        val originalFile = LightVirtualFile("ic_calendar_mdpi")
        val newRepresentativeFile = LightVirtualFile("ic_calendar_xhdpi")
        val index = IconIndex()

        val first = runBlocking {
            index.refresh(FakeIconSource(listOf(resource("ic_calendar", originalFile))), renderer)
        }
        val second = runBlocking {
            index.refresh(FakeIconSource(listOf(resource("ic_calendar", newRepresentativeFile))), renderer)
        }

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(2, renderer.renderCounts["ic_calendar"])
        assertEquals(newRepresentativeFile, second[0].resource.file)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "io.github.siddhardh7.iconlens.IconIndexTest"`
Expected: FAIL — compilation error, `IconIndex` doesn't exist yet.

- [ ] **Step 3: Implement `IconIndex`**

Create `src/main/kotlin/IconIndex.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests "io.github.siddhardh7.iconlens.IconIndexTest"`
Expected: PASS (6/6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/IconIndex.kt src/test/kotlin/IconIndexTest.kt
git commit -m "Add IconIndex incremental cache for M9"
```

---

### Task 2: Wire `IconIndex` into the tool window, retire `loadGallery`

**Files:**
- Modify: `src/main/kotlin/IconLensToolWindowFactory.kt`
- Modify: `src/main/kotlin/IconGalleryModel.kt`
- Modify: `src/test/kotlin/IconGalleryModelTest.kt`

**Interfaces:**
- Consumes: `IconIndex.refresh(source: IconSource, renderer: IconRenderer): List<RenderedIcon>` (Task 1).
- Produces: nothing new — this task retires `loadGallery` and switches the one caller over.

- [ ] **Step 1: Switch the refresh call site**

In `src/main/kotlin/IconLensToolWindowFactory.kt`, add this import alongside the existing `com.intellij.openapi.project.Project` import:

```kotlin
import com.intellij.openapi.components.service
```

Then change, inside `refresh()`:

```kotlin
                val rendered = loadGallery(DrawableIconSource(project), DrawableIconRenderer())
```

to:

```kotlin
                val rendered = project.service<IconIndex>().refresh(DrawableIconSource(project), DrawableIconRenderer())
```

- [ ] **Step 2: Delete `loadGallery`**

In `src/main/kotlin/IconGalleryModel.kt`, remove the now-unused function:

```kotlin
suspend fun loadGallery(source: IconSource, renderer: IconRenderer): List<RenderedIcon> =
    source.discover().map { renderer.render(it) }
```

The file keeps `filterByName` and `rankRenderedIcons` — only `loadGallery` goes.

- [ ] **Step 3: Delete `loadGallery`'s test case**

In `src/test/kotlin/IconGalleryModelTest.kt`, remove:

```kotlin
    @Test
    fun `loadGallery renders every discovered resource`() {
        val resources = listOf(resource("ic_calendar"), resource("ic_close"))
        val result = runBlocking { loadGallery(FakeIconSource(resources), FakeIconRenderer()) }

        assertEquals(2, result.size)
        assertEquals(setOf("ic_calendar", "ic_close"), result.map { it.resource.name }.toSet())
    }
```

Leave the rest of the file (the `FakeIconSource`/`FakeIconRenderer` helpers and the `filterByName`/`rankRenderedIcons` tests) untouched — those helpers are still used by the remaining tests in this file.

- [ ] **Step 4: Build and run the full test suite**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. `IconIndexTest`'s "first refresh renders every discovered resource" now covers what the deleted `loadGallery` test covered; all other existing tests still pass.

- [ ] **Step 5: Manually verify via `runIde`**

Run: `./gradlew runIde`, open the IconLens tool window in a project with drawable resources, then:
1. Click Refresh. Confirm the gallery populates exactly as before (no visible regression).
2. In the Project view, rename or add a new drawable file under `res/drawable/`, then click Refresh in IconLens. Confirm the new/renamed resource appears.
3. Delete a drawable file, then click Refresh. Confirm it disappears from the gallery.
4. Edit and save an existing drawable's content (e.g. change a vector path in an XML drawable), then click Refresh. Confirm the updated artwork shows in its tile.
5. Click Refresh twice in quick succession. Confirm no crash, no duplicate tiles, no stuck/partial state.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/IconLensToolWindowFactory.kt src/main/kotlin/IconGalleryModel.kt src/test/kotlin/IconGalleryModelTest.kt
git commit -m "Wire IconIndex into gallery refresh, retire loadGallery"
```

---

### Task 3: Documentation and milestone close-out

**Files:**
- Modify: `ARCHITECTURE.md`
- Modify: `ROADMAP.md`

**Interfaces:** None — documentation only.

- [ ] **Step 1: Add an "Incremental Indexing (M9)" section to `ARCHITECTURE.md`**

Add at the end of the file (this also gives `IconIndex` the dedicated section the pipeline diagram's box never had):

```markdown
---

# Incremental Indexing (M9)

`IconIndex` (`IconIndex.kt`) is a project-level service —
`@Service(Service.Level.PROJECT)`, retrieved via `project.service<IconIndex>()`
— that caches the last-rendered `RenderedIcon` per resource so
`IconLensToolWindowFactory.kt`'s `refresh()` only re-renders what actually
changed:

```kotlin
@Service(Service.Level.PROJECT)
class IconIndex {
    suspend fun refresh(source: IconSource, renderer: IconRenderer): List<RenderedIcon>
}
```

Entries are keyed by `(moduleName, name)` rather than by `VirtualFile`,
because `DrawableRepresentativePicker` (M2) can pick a different file as the
representative for the same logical resource between two refreshes (e.g. a
higher-density variant is added) — keying by name keeps that an *update* to
one cache entry rather than an implicit delete-and-add. `discover()` still
runs in full on every refresh (it's a cheap VFS walk, and the only way to
notice additions and deletions); only rendering is skipped, and only when a
resource's `VirtualFile` and `modificationStamp` both match what's cached. A
`Mutex` around the whole discover-diff-update sequence keeps overlapping
`refresh()` calls sequential rather than racing on the shared cache.

`IconGalleryModel.kt`'s `loadGallery()` — the previous stateless
discover-and-render-everything function — is gone; `IconIndex.refresh` is
its replacement and sole caller's entry point now.

Explicitly out of scope for M9: automatic refresh on file change (no VFS/PSI
listeners — refresh is still a manual, button-triggered action), caching
`IconDescriptor`s for ranking (`SimilarityRanking.kt` is unchanged), and
persisting the index to disk (in-memory only, for the life of the project
session).
```

- [ ] **Step 2: Update `ROADMAP.md`'s M9 section**

Change:

```
## M9 — Incremental Indexing

Status: NOT STARTED

- [ ] Detect relevant resource changes
- [ ] Add new resources
- [ ] Update changed resources
- [ ] Remove deleted resources
- [ ] Avoid unnecessary full re-indexing
- [ ] Respect project lifecycle
```

to:

```
## M9 — Incremental Indexing

Status: DONE

- [x] Detect relevant resource changes
- [x] Add new resources
- [x] Update changed resources
- [x] Remove deleted resources
- [x] Avoid unnecessary full re-indexing
- [x] Respect project lifecycle

Verified: `./gradlew build` and `./gradlew test` green, including
`IconIndexTest`'s coverage of add/update/remove/reuse/representative-swap
cases. Manually verified via `runIde`: adding, renaming, editing, and
deleting drawable resources are all reflected correctly on the next
Refresh click. Scoped to discovery+rendering only — no automatic
(listener-driven) refresh and no descriptor caching in this milestone; see
`docs/superpowers/specs/2026-08-10-incremental-indexing-design.md` for the
full design and `ARCHITECTURE.md`'s "Incremental Indexing (M9)" section for
the implementation summary.
```

- [ ] **Step 3: Final full verification**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew test`
Expected: all tests pass, including the 6 new `IconIndexTest` cases.

Report any warnings surfaced by either command.

- [ ] **Step 4: Commit**

```bash
git add ARCHITECTURE.md ROADMAP.md
git commit -m "Update docs for M9 completion"
```
