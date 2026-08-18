# M10 (sub-project 1) — Scale & Correctness Hardening — Design

## Goal

`ROADMAP.md`'s M10 bullets this sub-project covers: test large resource
collections, test malformed resources, test multi-module projects, verify
UI responsiveness.

The pipeline built across M2-M9 (`DrawableIconSource`, `DrawableIconRenderer`,
`IconIndex`) has never been exercised at the scale or breadth PRD.md commits
to: "the architecture should eventually support projects containing 1,000+
drawable resources," multi-module Android projects, and resources that fail
to decode. Individual pieces are unit-tested against small, hand-picked
fixtures, but nothing proves the pipeline holds up when those inputs show
up at realistic scale or in combination. This sub-project closes that gap
with tests, fixing whatever bugs they surface — it adds no new production
classes.

## Scope

Four test additions, one per M10 bullet above. Out of scope for this
sub-project (covered by M10's other two sub-projects instead): memory usage
review, cancellation/disposal review, plugin verification, packaging.

## Design

### 1. Scale test — `IconIndexTest.kt` (extend)

Reuses the existing `FakeIconSource`/`CountingRenderer` doubles already in
this file (see M9's `IconIndex` tests). Generates 1,000 synthetic
`IconResource`s (distinct names, `LightVirtualFile` per resource), calls
`IconIndex().refresh(...)`, and asserts:

- The result has exactly 1,000 entries.
- The call completes within a fixed wall-clock budget (2 seconds — generous
  enough to avoid flakiness on a loaded CI machine, tight enough to catch
  accidental quadratic behavior in the cache rebuild).

No real file I/O — this isolates whether `IconIndex`'s per-refresh
map-rebuild scales with resource count, independent of VFS cost.

### 2. Real-VFS scale test — `DrawableIconSourceTest.kt` (extend)

One test creating ~500 real temp files via `myFixture.tempDirFixture
.createFile(...)` (the same mechanism `DrawableIconSourceTest`'s existing
tests use), spread across a handful of `drawable`/`drawable-<qualifier>`
directories, then asserting `DrawableIconSource(project).discover()`
returns the expected count. Capped below 1,000 to keep the real-VFS-walk
test's setup/run time reasonable in CI — the goal is proving no pathological
behavior in `VfsUtilCore.visitChildrenRecursively` / `readAction` at
meaningful scale, not hitting the literal PRD number (test 1 already proves
the caching layer scales to 1,000+; this test's job is proving the VFS walk
underneath it doesn't have its own scaling problem).

### 3. Malformed-isolation test — new `PipelineRobustnessTest.kt`

A batch of ~10 resources run through the real `DrawableIconRenderer`
(not a fake): a mix of valid PNGs/vector drawables and known-malformed ones
— corrupt PNG bytes, malformed vector-drawable XML, a zero-byte file —
reusing the fixture patterns already established in
`DrawableIconRendererTest`/`VectorDrawableParserTest`. Asserts:

- Malformed resources come back as `RenderedIcon.Failed`.
- Valid resources come back as `RenderedIcon.Rendered`.
- The batch itself completes without throwing.

Per-resource isolation is already proven by existing unit tests
(`DrawableIconRenderer` catches per-resource and returns `Failed`); what's
missing and what this test adds is proof at the *batch* level — the actual
shape `IconIndex.refresh()` processes resources in — matching `CLAUDE.md`'s
"a malformed/unsupported icon must fail in isolation, not break the whole
index" rule end to end rather than at a single call site.

### 4. Multi-module test — `DrawableIconSourceTest.kt` (extend)

`DrawableIconSourceTest` today only exercises the single default module
`BasePlatformTestCase` provides. This test registers a second, real module
using APIs verified against this project's actual target platform jars
(Android Studio 2026.1.1.10):

```kotlin
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.testFramework.PsiTestUtil

val moduleBRoot = myFixture.tempDirFixture.findOrCreateDir("moduleB")
PsiTestUtil.addModule(project, EmptyModuleType.getInstance(), "moduleB", moduleBRoot)
```

(`PsiTestUtil.addModule(Project, ModuleType, String, VirtualFile): Module`
lives in `testFramework.jar`; `EmptyModuleType.getInstance(): EmptyModuleType`
— a `ModuleType` — lives in `intellij.platform.lang.impl.jar`. Both
confirmed present via `javap` against the jars this project's
`build.gradle.kts` already resolves.)

With drawable files created under both the default module's content root
and `moduleB`'s, the test asserts `discover()` returns resources from both
modules, each `IconResource.moduleName` matching its actual owning module —
proving the `ModuleManager.getInstance(project).modules.flatMap(...)` call
in `DrawableIconSource.collectCandidatesForModule` actually attributes
resources correctly across modules, which no existing test exercises.

### Responsiveness

No new test. Evidence comes from tests 1 and 2 above, both of which run
`discover()`/`refresh()` off the EDT exactly as production does (via
`runBlocking` in a JUnit test calling the same suspend functions production
calls from `Dispatchers.IO`), so their timing assertions double as
responsiveness evidence. This sub-project's completion write-up
(`ROADMAP.md`) will note explicitly that no EDT-blocking call was
introduced or found during this work, rather than adding a distinct
mechanism to measure "responsiveness" as its own thing.

## Error handling

Any bug these four tests surface is fixed as part of this sub-project, not
deferred — the point of a hardening pass is closing gaps found during it,
not just documenting them (same pattern M4 and M8 followed when their
hardening/manual-verification passes found real bugs).

## Testing

The four additions above **are** this sub-project's testing — there is no
separate testing phase beyond them. `./gradlew build` and `./gradlew test`
must both stay green throughout.

## Non-goals (explicitly out of scope for this sub-project)

- Memory usage review and cancellation/disposal review — the second M10
  sub-project ("Lifecycle & Memory Review").
- Plugin verification and packaging the distributable plugin — the third
  M10 sub-project ("Release Packaging").
- Any new production code path (e.g. a dedicated large-project warning UI,
  a configurable resource-count cap) — this sub-project is test coverage
  proving the existing pipeline holds up, plus bug fixes it surfaces; it is
  not scope for new user-facing behavior.
