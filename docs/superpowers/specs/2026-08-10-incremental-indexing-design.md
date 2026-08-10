# M9 — Incremental Indexing — Design

## Goal

`ROADMAP.md`'s M9 bullets: detect relevant resource changes, add new
resources, update changed resources, remove deleted resources, avoid
unnecessary full re-indexing, respect project lifecycle.

Today, "indexing" is `IconGalleryModel.kt`'s `loadGallery()` — a fully
stateless function that re-discovers every drawable in the project and
re-renders every single one, on every call. It runs once at startup and
once per manual refresh-button click (`IconLensToolWindowFactory.kt`'s
`refresh()`). There is no caching between calls: an unchanged icon is
re-rendered from scratch exactly as often as a changed one.

M9 replaces that with an `IconIndex` that remembers what it already
rendered and only re-renders what actually changed since the last refresh.

## Scope

Two things were explicitly decided out of scope for this milestone, to keep
it focused:

- **No automatic change detection.** No VFS/file-change listeners. The
  refresh button (and the one startup call) remain the only triggers —
  M9 makes *that* work incremental, it doesn't make refreshing automatic.
  Auto-refresh-on-change can be a later milestone if it's ever needed.
- **Discovery + rendering only, not descriptor caching.** `ARCHITECTURE.md`'s
  pipeline diagram places `IconIndex` between `IconDescriptor` and
  `SimilarityEngine`, which would suggest caching descriptors too — but that
  diagram is the only place `IconIndex` is mentioned anywhere in the docs;
  there's no actual specification for it to defer to. Ranking already
  re-describes every gallery icon on every search (`rankRenderedIcons`), but
  that only runs when a user deliberately pastes/chooses/drops a query
  image — not a hot path — so the redundant work there isn't worth the
  larger change (touching `SimilarityRanking.kt` and its call site) right
  now. `IconIndex` caches `RenderedIcon`s only. Descriptor caching is a
  natural, small, additive follow-up once `IconIndex` exists — nothing here
  forecloses it.

## Design

### Identity: keyed by `(moduleName, name)`, not by `VirtualFile`

`DrawableRepresentativePicker` (M2) already collapses density variants of
the same logical resource into one representative `VirtualFile` per
`(moduleName, name)`. Which file is representative can change between two
refreshes — e.g. adding a higher-density PNG that outranks the current
representative — while the logical resource is unchanged from the user's
perspective. Keying the cache by `(moduleName, name)` means that case is
correctly treated as an *update* to the same entry. Keying by `VirtualFile`
directly would treat it as an implicit delete-of-the-old-file plus add-of-
the-new-file instead — not wrong, but it drifts from ROADMAP's "update
changed resources" framing and leaves an orphaned cache entry to clean up.

### Change detection: `VirtualFile.modificationStamp`

Discovery (`IconSource.discover()`) still runs in full on every refresh —
it's a cheap VFS tree walk, and it's the only way to notice additions and
deletions in the first place. What's skipped is rendering: for each
discovered resource, if the cache already has that `(moduleName, name)` key
**and** the cached file equals the resource's file **and** the cached
`modificationStamp` equals the file's current `modificationStamp`, the
cached `RenderedIcon` is reused as-is. Otherwise the resource is rendered
fresh and the cache entry is replaced. Any cache key that doesn't appear in
the current discovery pass is simply not carried into the new cache — that
is how deletions are handled, with no separate "is this still there" check
needed.

### `IconIndex`

```kotlin
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
                existing.modificationStamp == resource.file.modificationStamp) {
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

Retrieved via `project.service<IconIndex>()`. Project-scoped (per-project
lifetime, not per-tool-window-instance) so closing and reopening the
IconLens tool window doesn't throw the cache away and force a full
re-render — this satisfies ROADMAP's "respect project lifecycle" bullet.
IntelliJ Platform disposes project-level services automatically on project
close; `IconIndex` holds no disposable resources of its own (no listeners,
no threads), so no explicit `Disposable` handling is needed beyond that.

### Call site change

`IconLensToolWindowFactory.kt`'s `refresh()` changes one line:

```kotlin
// before
val rendered = loadGallery(DrawableIconSource(project), DrawableIconRenderer())
// after
val rendered = project.service<IconIndex>().refresh(DrawableIconSource(project), DrawableIconRenderer())
```

Everything downstream — the `invokeLater` EDT hop, re-ranking against an
active query image, `listModel` repopulation — is unchanged. `loadGallery()`
becomes unused once this lands and is deleted, along with its
`IconGalleryModelTest` case (`loadGallery renders every discovered
resource`) — that behavior is now covered by `IconIndex`'s own "first
refresh renders everything" test.

## Error handling & concurrency

Per-resource render failures already isolate correctly via
`RenderedIcon.Failed` (`DrawableIconRenderer`) — unchanged by this
milestone.

New concern specific to this milestone: `IconIndex.cache` is shared mutable
state written from a coroutine launched on `Dispatchers.IO`, and nothing
prevents two `refresh()` calls from overlapping (e.g. a fast double-click
on the refresh button, or a startup refresh still in flight when a user
clicks refresh again). Without a guard, two overlapping calls could
interleave their discovery/render/cache-write steps and leave `cache` in a
state that doesn't correspond to either call's view of the world. The
`Mutex` above makes `refresh()` calls strictly sequential — a second
concurrent call simply suspends until the first completes, rather than
racing.

## Testing

`IconIndex` depends only on the `IconSource`/`IconRenderer` interfaces, so
it's unit-testable with the same `FakeIconSource`/`FakeIconRenderer`/
`LightVirtualFile` doubles `IconGalleryModelTest.kt` already uses — no
IntelliJ platform test fixture required. Cases:

- First `refresh()` renders every discovered resource (replaces the old
  `loadGallery` test).
- A second `refresh()` with the same resources/files/stamps reuses cached
  `RenderedIcon`s without re-rendering — asserted via a `FakeIconRenderer`
  that counts invocations per resource, or fails if called more than once
  for the same unchanged file.
- A resource whose file's `modificationStamp` changed between refreshes is
  re-rendered.
- A resource no longer present in discovery is dropped from the result and
  the cache.
- A resource newly present in discovery (not in the previous cache) is
  rendered and added.
- A representative-variant swap — same `(moduleName, name)`, different
  `VirtualFile` — is treated as an update to the existing key, not a
  separate add+delete.

## Non-goals (explicitly out of scope for M9)

- Automatic re-indexing on file change (VFS/PSI listeners) — refresh stays
  a manual, button-triggered action for now.
- Caching `IconDescriptor`s / speeding up ranking — `IconIndex` caches
  `RenderedIcon`s only; ranking keeps re-describing on every search.
- Persisting the index to disk across IDE restarts — in-memory only, for
  the life of the project session, consistent with V0.1's no-database,
  fully-local constraint.
