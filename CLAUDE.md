# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Required reading before making changes

Read these in order — they are the source of truth for the project, not this file:

1. `PRD.md` — product scope, what V0.1 must/must not do
2. `ARCHITECTURE.md` — the pipeline and module boundaries
3. `ROADMAP.md` — current milestone (does not exist yet; if a milestone needs planning, check with the user before inventing one)
4. `AGENTS.md` — full agent operating rules (summarized below, but that file is authoritative)

## What this project is

IconLens is an **Android Studio / IntelliJ Platform plugin** (Kotlin/JVM), not an Android app. It helps Android developers find existing project drawable resources that visually resemble a new icon they're about to add, instead of creating duplicates.

The repo currently contains only the JetBrains-generated plugin template (`MyToolWindowFactory`, a shuffle-a-number demo tool window) — real IconLens functionality has not been implemented yet.

## Commands

```bash
./gradlew build          # full build
./gradlew test           # run tests (also: use "Run Tests" run config -> :check)
./gradlew runIde         # launch a sandbox IDE with the plugin installed (also: "Run IDE with Plugin" run config)
./gradlew verifyPlugin   # check plugin compatibility against target IDEs (also: "Run Verifications" run config)
./gradlew test --tests "some.ClassName"   # run a single test class
```

Predefined `.run/` configurations mirror the above (Run IDE with Plugin, Run Tests, Run Verifications).

Target platform: IntelliJ IDEA 2025.3.5 / IntelliJ Platform 261, Java/JBR 21 (`build.gradle.kts`, `AGENTS.md`).

## Architecture

Everything flows through one pipeline (`ARCHITECTURE.md`); each stage is a separate concern and must not reach into a non-adjacent stage:

```
IconSource → IconResource → IconRenderer → RenderedIcon → ImageNormalizer → NormalizedIcon
  → IconDescriptor → IconIndex → SimilarityEngine → Search Results → UI
```

Hard boundaries (from `AGENTS.md`):
- Discovery, rendering, normalization, and similarity are separate — none may assume Android `R.drawable` details leak past discovery.
- Similarity must not know where an icon originated (drawable vs. future Compose ImageVector, etc.).
- Search must not depend directly on Android drawable APIs.
- UI must contain no indexing/search business logic.
- V0.1 supports VectorDrawable XML, PNG, WebP, JPEG, discovered from drawable resource dirs across potentially multiple modules — don't assume a single-module project.

Threading: scanning, rendering, normalization, hashing, and indexing are expensive and must run off the EDT; only UI updates touch the EDT. Respect project disposal, dumb/indexing mode, and VFS lifecycle — don't retain references that block project disposal.

## Scope discipline

This project is built milestone-by-milestone against `ROADMAP.md`. Do not implement future milestones proactively, and do not build any of the following until their milestone is explicitly reached: cloud/LLM/AI APIs, semantic search, ML embeddings, CLIP, vector databases, databases, analytics, telemetry, accounts, networking, Figma API integration, Compose ImageVector/Material Icon indexing, IDE duplicate inspections, automatic resource replacement/deletion, cloud sync. V0.1 must work entirely offline/locally — no project data, images, or filenames ever leave the machine.

If `ARCHITECTURE.md` proves wrong during implementation: explain the problem, propose the smallest change, update the doc, then implement — don't preserve a bad abstraction just because it was documented first.

## Working conventions

- Don't guess IntelliJ Platform or Android Studio APIs — verify against current JetBrains docs before using an API you're not certain exists.
- Before adding any dependency, justify it: why it's needed, why the JDK/IntelliJ Platform can't do it, and its runtime cost.
- Prefer small classes with explicit responsibilities and immutable models; add an interface only where it represents a real architectural boundary (e.g. the pipeline stages above), not by default.
- A malformed/unsupported icon must fail in isolation, not break the whole index — don't silently swallow unexpected exceptions.
- Prioritize tests for non-UI logic: normalization, similarity, ranking, resource detection, cache/index behavior.
- Before calling a milestone done: build, run tests, run the relevant Gradle task, fix anything the change broke, report notable warnings, and update `ROADMAP.md`.
