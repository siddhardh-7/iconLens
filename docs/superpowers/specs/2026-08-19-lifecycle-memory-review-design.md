# M10 (sub-project 2) — Lifecycle & Memory Review — Design

## Goal

`ROADMAP.md`'s M10 bullets this sub-project covers: review memory usage,
review cancellation/disposal behavior.

Across M1-M9 the plugin accumulated a `CoroutineScope` per tool window
content (`IconLensToolWindowFactory.kt`), a project-scoped cache
(`IconIndex`, M9), and Swing listeners (`installGalleryResourceActions`,
M8). Each was built with lifecycle correctness in mind at the time (e.g.
M3's fix making render failures propagate `CancellationException` instead
of swallowing it), but nothing has reviewed the accumulated whole, and only
`IconIndex`'s steady-state behavior is unit-tested — its behavior under
*cancellation* specifically is not. This sub-project closes that gap: one
new regression test for the one thing that's cleanly unit-testable
headless, plus a written, evidence-based audit for the rest — and fixes
whatever either surfaces.

## Scope

**Testable in this sub-project:** `IconIndex.refresh()`'s behavior when
cancelled mid-flight — specifically, whether its internal `Mutex` is left
locked, which would deadlock every subsequent `refresh()` call for the
life of the project (a project-scoped service, so this would persist until
the user restarts the IDE).

**Audited, not tested, in this sub-project:** the `Disposer`/
`contentDisposed` wiring inside `IconLensToolWindowFactory.createToolWindowContent`,
memory-bound reasoning for `IconIndex`'s cache, and gallery listener
retention. All three live inside Swing-construction code or depend on
platform-managed lifecycle timing; testing them would require either a
heavy IntelliJ UI test fixture (the same fragile territory M10 sub-project
1's Task 4 hit and correctly avoided) or extracting the lifecycle logic
into a separately-testable unit first — both are larger changes than this
review's scope justifies given the code already reads as correct on
inspection (see Audit below).

Out of scope for this sub-project (covered by M10's third sub-project
instead): plugin verification, packaging the distributable plugin.

## Design

### 1. Cancellation-safety test — `IconIndexTest.kt` (extend)

```kotlin
@Test
fun `cancelling a refresh releases the mutex for the next refresh`() = runBlocking {
    val index = IconIndex()
    val hangingRenderer = object : IconRenderer {
        override suspend fun render(resource: IconResource): RenderedIcon = awaitCancellation()
    }

    val job = launch(start = CoroutineStart.UNDISPATCHED) {
        index.refresh(FakeIconSource(listOf(resource("ic_calendar"))), hangingRenderer)
    }
    job.cancelAndJoin()

    val result = withTimeout(2000) {
        index.refresh(FakeIconSource(listOf(resource("ic_calendar"))), CountingRenderer())
    }

    assertEquals(1, result.size)
}
```

`awaitCancellation()` (confirmed present in the project's actual bundled
`kotlinx-coroutines-core-jvm.jar` via `DelayKt$awaitCancellation$1.class`,
not guessed) suspends forever until the coroutine is cancelled, simulating
a render that's still in flight when cancellation happens — the realistic
case (a slow image decode) rather than an artificial one. `cancelAndJoin()`
guarantees the first call has fully unwound (including running past
whatever cleanup `Mutex.withLock` does on cancellation) before the second
call starts. `withTimeout(2000)` turns a real deadlock into an immediate,
clear test failure (`TimeoutCancellationException`) instead of a hung test
run — this is the actual risk being guarded against: if `IconIndex`'s
`mutex.withLock { ... }` were ever changed in a way that let a cancelled
coroutine leave the lock held, every subsequent `refresh()` for the life
of the project would hang forever, and a user would see the gallery never
update again until restarting the IDE.

This test is expected to pass without any production code change —
`kotlinx.coroutines.sync.Mutex` is documented as cancellation-safe, and
`IconIndex.refresh()` has no `try`/`catch` that could interfere. The test's
value is locking that guarantee in for IconLens's specific usage, the same
way M9's tests locked in cache-key/reuse behavior.

### 2. Audit — Disposal wiring in `IconLensToolWindowFactory.kt`

Trace every read/write of `contentDisposed`
(`IconLensToolWindowFactory.kt:91,125,181,296`) and confirm:

- It's declared once (`var contentDisposed = false`, line 91) and set exactly
  once, inside the `Disposable` registered via `Disposer.register(content, ...)`
  (line 296), alongside `scope.cancel()` — so disposal and cancellation happen
  atomically from the same callback.
- Both async call sites that resume on the EDT after suspending work —
  `refresh()`'s `invokeLater` (line 125) and `loadAndShow()`'s `invokeLater`
  (line 181) — check `contentDisposed` before touching any UI component
  (`listModel`, `allIcons`, `activeQueryImage`), so a coroutine that was
  already in flight when disposal happened cannot mutate UI state after the
  fact.
- `Disposer.register(content, ...)` (line 293) is the standard IntelliJ
  Platform pattern for tying cleanup to a `Content`'s lifecycle, which the
  platform disposes when the tool window content is removed (including on
  project close) — this is platform-guaranteed behavior, not something
  IconLens implements itself, so no test can meaningfully verify it beyond
  what the platform's own test suite already covers.

Document the outcome as a `ARCHITECTURE.md` note (a "Lifecycle & Disposal"
section, mirroring the existing "Incremental Indexing (M9)" section's
style) rather than a source comment, so it's discoverable independent of
which file a future reader happens to open.

### 3. Audit — Memory bound in `IconIndex`

`IconIndex`'s cache (`private var cache: Map<ResourceKey, CachedEntry>`)
is rebuilt from scratch on every `refresh()` from that call's `discovered`
list — any `(moduleName, name)` key not present in the current discovery
pass is simply not carried into the new map. This means the cache's size
is always bounded by the project's current real resource count, never by
historical resource count; a `RenderedIcon`/`BufferedImage` for a deleted
drawable becomes unreachable (and GC-eligible) the moment the next
`refresh()` runs. This is already proven by M9's existing
`IconIndexTest` case "a resource no longer discovered is dropped from the
result" — no new test needed; the audit's job is documenting that this
existing test *is* the memory-bound guarantee, closing the ROADMAP bullet
honestly rather than re-testing the same behavior under a different name.

### 4. Audit — Gallery listener retention in `GalleryResourceActions.kt`

`installGalleryResourceActions` (`GalleryResourceActions.kt:100`) attaches
two listeners (`PopupHandler`, `MouseAdapter`) directly to the `JBList`
instance passed in, via `list.addMouseListener(...)`. Neither listener is
held by anything outside the list itself (no static registry, no
project-level service holding a reference), so both become unreachable
together with the list when the containing `Content`/panel tree is
discarded — standard Swing listener lifecycle, nothing IconLens-specific
to leak.

## Error handling

Any bug the new test or the audit surfaces is fixed as part of this
sub-project, not deferred — same policy as M10 sub-project 1.

## Testing

`./gradlew build` and `./gradlew test` must stay green throughout. The one
new test above is this sub-project's only new automated coverage; the
three audit items are documentation, not tests, per the Scope section's
reasoning about heavy-fixture cost vs. actual risk.

## Non-goals (explicitly out of scope for this sub-project)

- Extracting `IconLensToolWindowFactory`'s disposal wiring into a
  separately-testable unit — a legitimate refactor, but a larger change
  than a review sub-project's scope; revisit only if a real bug is found
  here that a refactor would meaningfully prevent from recurring.
- A heavy IntelliJ UI test fixture to directly exercise `Disposer`/content
  lifecycle — same cost/fragility tradeoff that ruled out a live
  multi-module test in M10 sub-project 1.
- Plugin verification and packaging the distributable plugin — the third
  M10 sub-project ("Release Packaging").
