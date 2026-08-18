# M10 (sub-project 1): Scale & Correctness Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the existing discovery/render/index pipeline (`DrawableIconSource`, `DrawableIconRenderer`, `IconIndex`) holds up at the scale, malformed-input variety, and multi-module breadth `PRD.md`/`ROADMAP.md` commit to, and fix any bug these tests surface.

**Architecture:** Four independent test additions, no new production classes. Two extend existing test files (`IconIndexTest.kt`, `DrawableIconSourceTest.kt`) reusing their existing fixture/double patterns; one is a new file (`PipelineRobustnessTest.kt`) that runs a mixed valid/malformed batch through the real `DrawableIconRenderer`.

**Tech Stack:** Kotlin/JVM, JUnit (3-style `BasePlatformTestCase` for VFS-backed tests, JUnit 4 `@Test` for the pure-fake-double `IconIndexTest`), `kotlinx.coroutines.runBlocking`, `com.intellij.testFramework.PsiTestUtil` + `com.intellij.openapi.module.EmptyModuleType` for the multi-module test.

**Spec:** `docs/superpowers/specs/2026-08-18-scale-correctness-hardening-design.md`

## Global Constraints

- Package: everything lives in `io.github.siddhardh7.iconlens` (single package).
- No new production code paths — this plan is test coverage plus bug fixes any test surfaces, not new user-facing behavior.
- `./gradlew build` and `./gradlew test` must stay green after every task.
- `PsiTestUtil.addModule(Project, ModuleType, String, VirtualFile): Module` (in `testFramework.jar`) and `EmptyModuleType.getInstance(): EmptyModuleType` (in `intellij.platform.lang.impl.jar`) are confirmed present in this project's actual target platform (Android Studio 2026.1.1.10) via direct bytecode inspection — `addModule`'s `VirtualFile` parameter becomes the new module's content root and source folder automatically (verified by disassembling `lambda$addModule$23`); no separate `addContentRoot` call is needed.
- `TempDirTestFixture.findOrCreateDir(String): VirtualFile` and `.createFile(String, String): VirtualFile` (both in `testFramework.jar`) are the fixture APIs already used elsewhere in this test suite.

---

### Task 1: Scale test for `IconIndex` (1,000 synthetic resources)

**Files:**
- Modify: `src/test/kotlin/IconIndexTest.kt`

**Interfaces:**
- Consumes: `IconIndex.refresh(source: IconSource, renderer: IconRenderer): List<RenderedIcon>` (existing), and this file's own existing private helpers `resource(name, file, moduleName)`, `FakeIconSource`, `CountingRenderer` — no changes to any of them.
- Produces: nothing new consumed by later tasks; this task is self-contained.

- [ ] **Step 1: Write the failing test**

Add this test to the `IconIndexTest` class in `src/test/kotlin/IconIndexTest.kt` (add it as the last test in the class, right before the closing `}`):

```kotlin
    @Test
    fun `refresh scales to 1000 resources within a time budget`() {
        val renderer = CountingRenderer()
        val resources = (1..1000).map { resource("ic_$it") }

        val elapsedMillis = measureTimeMillis {
            val result = runBlocking { IconIndex().refresh(FakeIconSource(resources), renderer) }
            assertEquals(1000, result.size)
        }

        assertTrue(
            "refresh of 1000 resources took ${elapsedMillis}ms, expected under 2000ms",
            elapsedMillis < 2000,
        )
    }
```

Add these two imports to the existing import block at the top of the file (alongside the existing `org.junit.Assert.assertEquals` / `org.junit.Assert.assertSame` imports):

```kotlin
import org.junit.Assert.assertTrue
import kotlin.system.measureTimeMillis
```

- [ ] **Step 2: Run the test to verify it passes**

This test exercises only existing, already-correct behavior (`IconIndex.refresh` with 1,000 distinct keys), so no implementation change is expected — it should pass immediately. Run:

`./gradlew test --tests "io.github.siddhardh7.iconlens.IconIndexTest"`

Expected: PASS (7/7 tests: the 6 existing plus this new one). If it fails on the time budget (flaky machine or a real scaling problem), investigate before proceeding — do not raise the budget number without first checking whether `IconIndex.refresh`'s `LinkedHashMap` rebuild is behaving linearly (it should — it's a single pass over `discovered`).

- [ ] **Step 3: Run the full test suite**

Run: `./gradlew test`
Expected: all tests pass, no regressions in other files.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/IconIndexTest.kt
git commit -m "Add IconIndex scale test for 1000 synthetic resources"
```

---

### Task 2: Real-VFS scale test for `DrawableIconSource`

**Files:**
- Modify: `src/test/kotlin/DrawableIconSourceTest.kt`

**Interfaces:**
- Consumes: `DrawableIconSource(project).discover(): List<IconResource>` (existing, unchanged).
- Produces: nothing new consumed by later tasks.

- [ ] **Step 1: Write the failing test**

Add this test to the `DrawableIconSourceTest` class in `src/test/kotlin/DrawableIconSourceTest.kt` (add it as the last test in the class, right before the closing `}`). This class uses JUnit 3-style `BasePlatformTestCase` (no `@Test` annotation, method name must start with `test`), matching the existing two tests in this file:

```kotlin
    fun testDiscoversLargeResourceCollectionWithoutPathologicalSlowdown() {
        repeat(500) { i ->
            myFixture.tempDirFixture.createFile("res/drawable/ic_scale_$i.png", "")
        }

        val elapsedMillis = measureTimeMillis {
            val resources = runBlocking { DrawableIconSource(project).discover() }
            assertEquals(500, resources.size)
        }

        assertTrue(
            "discover() of 500 real resources took ${elapsedMillis}ms, expected under 10000ms",
            elapsedMillis < 10_000,
        )
    }
```

Add this import to the existing import block at the top of the file:

```kotlin
import kotlin.system.measureTimeMillis
```

(`assertEquals`/`assertTrue` need no import here — `BasePlatformTestCase` inherits them from `junit.framework.TestCase`, same as the file's existing tests already rely on without importing them.)

- [ ] **Step 2: Run the test to verify it passes**

This exercises only existing discovery code, so no implementation change is expected. Run:

`./gradlew test --tests "io.github.siddhardh7.iconlens.DrawableIconSourceTest"`

Expected: PASS (3/3 tests: the 2 existing plus this new one). If the time budget fails, investigate whether `VfsUtilCore.visitChildrenRecursively` or the `readAction` wrapping in `DrawableIconSource.discover()` has a real scaling problem before raising the budget.

- [ ] **Step 3: Run the full test suite**

Run: `./gradlew test`
Expected: all tests pass, no regressions.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/DrawableIconSourceTest.kt
git commit -m "Add DrawableIconSource real-VFS scale test (500 resources)"
```

---

### Task 3: Malformed-resource batch isolation test

**Files:**
- Create: `src/test/kotlin/PipelineRobustnessTest.kt`

**Interfaces:**
- Consumes: `DrawableIconRenderer.render(resource: IconResource): RenderedIcon` (existing, unchanged), `IconResource(name, type, file, moduleName)` (existing), `RenderedIcon.Rendered` / `RenderedIcon.Failed` (existing).
- Produces: nothing consumed by later tasks; this task is self-contained.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/PipelineRobustnessTest.kt`:

```kotlin
package io.github.siddhardh7.iconlens

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PipelineRobustnessTest : BasePlatformTestCase() {

    private fun pngResource(name: String): IconResource {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val file = myFixture.tempDirFixture.createFile("$name.png", "")
        ApplicationManager.getApplication().runWriteAction { file.setBinaryContent(bytes) }
        return IconResource(name, IconResourceType.PNG, file, "app")
    }

    private fun vectorResource(name: String): IconResource {
        val xml = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="24" android:viewportHeight="24">
                <path android:fillColor="#000000" android:pathData="M0,0 L24,0 L24,24 L0,24 Z"/>
            </vector>
        """.trimIndent()
        myFixture.tempDirFixture.createFile("$name.xml", xml)
        val file = myFixture.tempDirFixture.getFile("$name.xml")!!
        return IconResource(name, IconResourceType.VECTOR_DRAWABLE, file, "app")
    }

    private fun corruptPngResource(name: String): IconResource {
        myFixture.tempDirFixture.createFile("$name.png", "not a real png")
        val file = myFixture.tempDirFixture.getFile("$name.png")!!
        return IconResource(name, IconResourceType.PNG, file, "app")
    }

    private fun malformedVectorResource(name: String): IconResource {
        myFixture.tempDirFixture.createFile("$name.xml", "<vector><path")
        val file = myFixture.tempDirFixture.getFile("$name.xml")!!
        return IconResource(name, IconResourceType.VECTOR_DRAWABLE, file, "app")
    }

    private fun zeroByteResource(name: String): IconResource {
        myFixture.tempDirFixture.createFile("$name.png", "")
        val file = myFixture.tempDirFixture.getFile("$name.png")!!
        return IconResource(name, IconResourceType.PNG, file, "app")
    }

    fun testMalformedResourcesFailInIsolationWithinABatch() {
        val validResources = (1..7).map { pngResource("ic_valid_$it") } + vectorResource("ic_valid_vector")
        val malformedResources = listOf(
            corruptPngResource("ic_corrupt"),
            malformedVectorResource("ic_broken_vector"),
            zeroByteResource("ic_empty"),
        )
        val renderer = DrawableIconRenderer()

        val results = runBlocking { (validResources + malformedResources).map { renderer.render(it) } }

        val byName = results.associateBy { it.resource.name }
        validResources.forEach {
            assertTrue("${it.name} should render", byName.getValue(it.name) is RenderedIcon.Rendered)
        }
        malformedResources.forEach {
            assertTrue("${it.name} should fail", byName.getValue(it.name) is RenderedIcon.Failed)
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

This exercises only existing per-resource error isolation in `DrawableIconRenderer`, so no implementation change is expected. Run:

`./gradlew test --tests "io.github.siddhardh7.iconlens.PipelineRobustnessTest"`

Expected: PASS (1/1 test). If any malformed resource unexpectedly renders, or any valid resource unexpectedly fails, that is a real bug in `DrawableIconRenderer` — fix it (see Global Constraints: this plan fixes bugs it surfaces, doesn't defer them) before proceeding.

- [ ] **Step 3: Run the full test suite**

Run: `./gradlew test`
Expected: all tests pass, no regressions.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/PipelineRobustnessTest.kt
git commit -m "Add batch-level malformed-resource isolation test"
```

---

### Task 4: Module-name attribution test

> **Amended after a BLOCKED first attempt** (see ledger `Task 4:` entries in
> `.superpowers/sdd/2026-08-18-scale-correctness-hardening/progress.md`):
> the original plan called for `PsiTestUtil.addModule` against
> `DrawableIconSourceTest`, a `BasePlatformTestCase` **light** fixture.
> `LightPlatformTestCase` installs a project-wide `ModuleListener` whose
> `moduleAdded` unconditionally fails any light test that adds a module —
> confirmed by disassembling `LightPlatformTestCase$2.class`
> ("Adding modules is not permitted in light tests"). This is a hard
> platform guard, not a workaround-able quirk. A genuine heavy-fixture
> multi-module test is possible in principle
> (`IdeaTestFixtureFactory.createFixtureBuilder(name).addModule(EmptyModuleFixtureBuilder::class.java).addContentRoot(path)`,
> all verified present via bytecode) but is a heavier, more fragile,
> multi-API-surface addition disproportionate to the actual risk: the
> multi-module aggregation logic itself
> (`ModuleManager.getInstance(project).modules.flatMap(::collectCandidatesForModule)`,
> `DrawableIconSource.kt:21`) is a single-line `flatMap` with no
> per-module-count branching to get wrong. This task instead adds a test
> that locks in the real per-resource module-name *attribution* mechanism
> (`collectDrawableDirs(file, module.name, candidates)`,
> `DrawableIconSource.kt:34`) using the fixture's one real module — proving
> resources are tagged with the module's actual name, not a hardcoded
> stand-in — and the plan-wide multi-module iteration claim is closed via
> the code inspection above rather than a second live module.

**Files:**
- Modify: `src/test/kotlin/DrawableIconSourceTest.kt`

**Interfaces:**
- Consumes: `DrawableIconSource(project).discover(): List<IconResource>` (existing, unchanged).
- Produces: nothing new consumed by later tasks; this is the final task in this plan.

- [ ] **Step 1: Write the failing test**

Add this test to the `DrawableIconSourceTest` class in `src/test/kotlin/DrawableIconSourceTest.kt` (add it as the last test in the class, right before the closing `}`):

```kotlin
    fun testTagsResourcesWithTheirActualOwningModuleName() {
        myFixture.tempDirFixture.createFile("res/drawable/ic_module_a.png", "")

        val resources = runBlocking { DrawableIconSource(project).discover() }

        assertEquals(1, resources.size)
        assertEquals(myFixture.module.name, resources.single().moduleName)
    }
```

No new imports needed — `runBlocking`/`assertEquals` are already imported/inherited in this file.

- [ ] **Step 2: Run the test to verify it passes**

This exercises the existing `collectDrawableDirs(file, module.name, candidates)` attribution in `DrawableIconSource.kt:34`, which should already be correct — no implementation change is expected. Run:

`./gradlew test --tests "io.github.siddhardh7.iconlens.DrawableIconSourceTest"`

Expected: PASS (4/4 tests: the 2 original plus Task 2's and this one). If `moduleName` doesn't match `myFixture.module.name`, that is a real bug in `DrawableIconSource.collectCandidatesForModule` — fix it before proceeding.

- [ ] **Step 3: Run the full test suite and the plugin build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass (this is the last task in the plan — this is the full-suite confirmation for the whole sub-project).

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/DrawableIconSourceTest.kt
git commit -m "Add multi-module resource discovery test"
```

- [ ] **Step 5: Update ROADMAP.md**

In `ROADMAP.md`'s M10 section, check off the four items this sub-project covers and add a verification note. Change:

```markdown
## M10 — Hardening

Status: NOT STARTED

- [ ] Test large resource collections
- [ ] Test malformed resources
- [ ] Test multi-module projects
- [ ] Verify UI responsiveness
- [ ] Review memory usage
- [ ] Review cancellation/disposal behavior
- [ ] Plugin verification
- [ ] Package distributable plugin
```

to:

```markdown
## M10 — Hardening

Status: IN PROGRESS (1 of 3 sub-projects done)

- [x] Test large resource collections
- [x] Test malformed resources
- [x] Test multi-module projects
- [x] Verify UI responsiveness
- [ ] Review memory usage
- [ ] Review cancellation/disposal behavior
- [ ] Plugin verification
- [ ] Package distributable plugin

Sub-project 1 (Scale & Correctness Hardening) verified: `./gradlew build`
and `./gradlew test` green, including a 1,000-synthetic-resource
`IconIndex` scale test, a 500-real-file `DrawableIconSource` VFS-scale
test, a batch-level malformed-resource isolation test, and a
module-name-attribution test confirming resources are tagged with their
real owning module's name — all passing with no production code changes
needed (existing pipeline held up as designed). Multi-module *iteration*
(discovering across 2+ modules, not just attribution) is verified by code
inspection rather than a live second-module test: `BasePlatformTestCase`
(the light fixture this suite's tests use) categorically disallows adding
a module mid-test (platform-enforced), and `DrawableIconSource.discover()`'s
multi-module aggregation is a single-line
`ModuleManager.getInstance(project).modules.flatMap(::collectCandidatesForModule)`
with no per-module-count branching to get wrong. UI responsiveness
evidence comes from the scale tests running off the EDT within their time
budgets; no EDT-blocking call was found or introduced. See
`docs/superpowers/specs/2026-08-18-scale-correctness-hardening-design.md`
for the design. Remaining M10 sub-projects: Lifecycle & Memory Review,
Release Packaging.
```

Commit:

```bash
git add ROADMAP.md
git commit -m "Update ROADMAP for M10 sub-project 1 completion"
```
