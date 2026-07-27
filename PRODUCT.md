# Product

<!-- impeccable:product-schema 1 -->

## Platform

desktop-ide-plugin

IconLens ships as a JetBrains IntelliJ Platform plugin (Kotlin/JVM), surfaced as a Swing-based tool window inside Android Studio. It is not a web app, a native iOS/Android app, or an adaptive multi-OS product — none of the schema's default platform values apply. Visual/UX work on this project should follow JetBrains IntelliJ Platform UI Guidelines (https://jetbrains.github.io/ui) and Swing/`com.intellij.ui` component conventions, not browser or mobile-native conventions.

## Users

Android developers working on medium and large applications, particularly teams using Kotlin, Jetpack Compose, XML, VectorDrawable, design systems, and Figma-driven workflows.

Their job: while implementing a design, quickly answer "does this icon already exist in the project?" before adding a new drawable resource.

## Product Purpose

IconLens helps Android developers discover existing project icons that visually resemble a new icon they're about to add, so they reuse instead of duplicating.

Success (V0.1): a developer can install the plugin, open the IconLens tool window, browse discovered project drawables, provide a query icon, search locally, see ranked visually-similar matches, and open/copy an existing resource — without ever needing to hand-search `res/drawable` or ask a teammate.

## Positioning

Filename search misses icons that are visually identical but differently named (e.g. a designer's "location" vs. the project's `ic_map_marker`). IconLens's mechanism is visual/perceptual similarity search over the project's own resources — not text/semantic search, not an external asset library — so it finds matches competitors relying on filename search or manual browsing cannot.

## Operating Context

- Lives inside Android Studio as an IconLens tool window (not a standalone app).
- Core workflow: developer receives an icon from a design → copies/exports it → opens IconLens → pastes/drops/selects the query icon → IconLens searches project resources → ranked similar results appear → developer reuses an existing resource (open it, reveal in Project view, copy resource name, or copy a `R.drawable.x` reference).
- Also provides a standalone visual gallery of all discovered project icons with filename filtering, independent of a search query.
- Must operate across multi-module Android projects, not just single-module apps.

## Capabilities and Constraints

V0.1 scope (confirmed):
- Discovers icons from Android drawable resource directories: VectorDrawable XML, PNG, WebP, JPEG.
- Similarity search is local, deterministic, and lightweight (e.g. perceptual/difference hashing, edge/shape comparison) — no ML embeddings, no CLIP, no cloud AI/LLM calls, no networking, no accounts, no analytics/telemetry, no database. Must work fully offline; no project resources, code, images, or filenames are ever transmitted externally.
- Must not block the IDE's Event Dispatch Thread — scanning, rendering, normalization, hashing, and indexing run in the background.
- Architecture separates discovery, rendering, normalization, descriptor/fingerprinting, indexing, similarity, and search so the similarity algorithm and icon sources (e.g. future Compose ImageVectors) can evolve independently of the UI.
- The similarity percentage shown to users is a ranking aid, not a mathematical guarantee.

Explicitly out of scope until a later milestone (see ROADMAP.md when it exists): duplicate-resource detection/inspections, Compose ImageVector and Material Icon indexing, semantic/natural-language search, Figma API integration, automatic code replacement or resource deletion, cloud sync.

## Brand Commitments

Plugin name: **IconLens** (id `io.github.siddhardh7.iconlens.IconLens`) — considered fixed.

Vendor/author identity for the plugin listing is an **open decision** — `plugin.xml` currently carries a placeholder vendor ("YourCompany", yourcompany.com) that must not be treated as a real brand commitment or design input until replaced.

No logo, color, typography, or other visual identity has been established yet.

## Evidence on Hand

None. No real screenshots, testimonials, case studies, or usage data exist yet — the project is pre-implementation (only the JetBrains-generated plugin template exists in source). Future work must not fabricate example icon sets, user quotes, or benchmark similarity scores; the PRD's example ("calendar icon → 96% ic_calendar_outline") is illustrative, not real evidence.

## Product Principles

1. Local-first, always: no icon, filename, or project data leaves the machine, in V0.1 and beyond unless a future milestone explicitly changes that.
2. Reuse over recreation: every design decision should shorten the path from "I need an icon" to "here's the existing one," not add ceremony.
3. Pipeline stages stay decoupled: UI, similarity, normalization, and discovery must remain independently replaceable — similarity must never assume Android drawable specifics, and UI must never carry search/indexing logic.
4. Never block the IDE: any interaction that could stall the EDT belongs in the background, with results delivered asynchronously.
5. Small and honest: ship the smallest reliable milestone; similarity scores are presented as ranking aids, not guarantees.

## Accessibility & Inclusion

No project-specific accessibility requirement has been established beyond the baseline JetBrains IntelliJ Platform UI accessibility conventions (keyboard navigation, screen-reader-compatible Swing components) that any well-built plugin tool window should follow.
