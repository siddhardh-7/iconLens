# IconLens — Agent Instructions

IconLens is an Android Studio plugin for discovering existing project icons using visual search.

Before making changes, read:

1. `PRD.md`
2. `ARCHITECTURE.md`
3. `ROADMAP.md`
4. This file

These files are the source of truth for the project.

---

## Development Environment

Primary target:

- Android Studio Quail 1 | 2026.1.1 Patch 2
- Android Studio version: 2026.1.1.10
- Build: AI-261.23567.138.2611.15646644
- IntelliJ Platform: 261
- Java/JBR: 21
- Architecture: aarch64

The plugin is written in Kotlin/JVM using the IntelliJ Platform SDK.

This is NOT an Android application.

---

## Core Rules

### Work milestone-by-milestone

Only implement the milestone explicitly requested by the user.

Do not implement future ROADMAP features proactively.

A future requirement may influence an abstraction, but it must not cause the future feature itself to be implemented.

---

### Do not guess IntelliJ APIs

IntelliJ Platform and Android Studio APIs change frequently.

If uncertain about:

- API availability
- plugin dependencies
- extension points
- Android Studio APIs
- threading requirements
- deprecated APIs
- target platform configuration

verify against current official JetBrains documentation or existing platform APIs.

Do not invent APIs.

---

### Keep the plugin lightweight

Do not introduce dependencies unless they provide clear value.

Before adding a dependency, explain:

- why it is needed
- why the JDK/IntelliJ Platform cannot reasonably provide the functionality
- its runtime impact

Do not introduce during V0.1 unless explicitly required:

- cloud services
- LLM APIs
- AI APIs
- vector databases
- databases
- analytics
- telemetry
- accounts
- networking

V0.1 must work entirely locally.

---

## Architecture Rules

Follow `ARCHITECTURE.md`.

Important boundaries:

- Icon discovery must be separate from rendering.
- Rendering must be separate from normalization.
- Normalization must be separate from similarity.
- Similarity must not know where an icon originated.
- Search must not depend directly on Android drawable APIs.
- UI must not contain indexing/search business logic.

Do not tightly couple the system to `R.drawable`.

V0.1 supports drawables, but future icon sources may include Compose ImageVectors and other resources.

---

## IntelliJ Platform Rules

Prefer IntelliJ Platform APIs over raw filesystem assumptions when working with IDE/project resources.

Do not perform expensive work on the Event Dispatch Thread (EDT).

Scanning, rendering, normalization, hashing, and indexing must be suitable for background execution.

UI updates must follow IntelliJ threading requirements.

Respect:

- project lifecycle
- disposal
- dumb/indexing mode where applicable
- Virtual File System lifecycle

Do not retain references that unnecessarily prevent project disposal.

---

## Code Quality

Prefer:

- small classes
- explicit responsibilities
- immutable models
- dependency inversion at architectural boundaries
- testable pure logic
- descriptive naming

Avoid:

- giant manager classes
- global mutable state
- premature frameworks
- unnecessary abstractions
- speculative functionality

An interface should exist because it represents a meaningful architectural boundary, not simply because interfaces are considered "clean".

---

## Error Handling

A malformed or unsupported icon must not break the entire index.

Failures should be isolated to the affected resource whenever possible.

Do not silently swallow unexpected exceptions.

Use IDE-compatible logging where appropriate.

---

## Testing

Add tests for meaningful non-UI logic.

Especially prioritize tests for:

- normalization
- similarity
- ranking
- resource detection
- cache/index behavior

Do not write meaningless tests purely to increase test count.

---

## Before Completing Work

For implementation milestones:

1. Compile the project.
2. Run relevant tests.
3. Run the appropriate Gradle build.
4. Fix errors introduced by the change.
5. Report important warnings.
6. Explain significant architectural decisions.
7. Update `ROADMAP.md`.

Do not mark a milestone complete if the project does not build.

---

## Architecture Changes

If implementation reveals that `ARCHITECTURE.md` is incorrect or insufficient:

1. Explain the problem.
2. Propose the smallest architectural change.
3. Update the architecture documentation.
4. Then implement the change.

The architecture may evolve.

Do not preserve a bad abstraction merely because it was documented earlier.

---

## Scope Control

Do not implement these until their milestone/version is requested:

- Compose ImageVector indexing
- Material Icons indexing
- semantic text search
- ML embeddings
- CLIP
- Figma API integration
- IDE duplicate inspections
- automatic resource replacement
- automatic deletion
- cloud synchronization

---

## Guiding Principle

Build the smallest reliable implementation that moves the current milestone forward while preserving the important architectural boundaries needed for IconLens to evolve.