# IconLens — Roadmap

Current target:

Android Studio Quail 1 | 2026.1.1 Patch 2

Build:
AI-261.23567.138.2611.15646644

Platform:
261

---

# V0.1 — Visual Drawable Search

## M0 — Bootstrap

Status: IN PROGRESS

- [x] Create IntelliJ Platform plugin project
- [x] Configure JDK/JBR 21 development environment
- [x] Verify generated project builds
- [ ] Target Android Studio Quail instead of IntelliJ IDEA
- [ ] Verify plugin packaging
- [ ] Launch sandbox Android Studio using `runIde`
- [ ] Verify plugin loads

Definition of done:

The plugin builds and loads successfully in the target development IDE.

---

## M1 — IconLens Tool Window

Status: NOT STARTED

- [ ] Register IconLens Tool Window
- [ ] Display minimal empty state
- [ ] Follow native IntelliJ UI conventions
- [ ] Verify Tool Window opens correctly

No drawable scanning yet.

---

## M2 — Drawable Discovery

Status: NOT STARTED

- [ ] Define IconSource
- [ ] Define IconResource
- [ ] Implement DrawableIconSource
- [ ] Discover drawable resources
- [ ] Handle relevant drawable resource directories
- [ ] Avoid assuming only one Android module
- [ ] Run discovery outside expensive EDT work
- [ ] Add resource-discovery tests where practical

---

## M3 — Rendering & Gallery

Status: NOT STARTED

- [ ] Render supported raster resources
- [ ] Render supported VectorDrawables
- [ ] Gracefully handle unsupported/malformed resources
- [ ] Display project icon gallery
- [ ] Display resource names
- [ ] Add filename filtering

---

## M4 — Query Input

Status: NOT STARTED

- [ ] Query preview
- [ ] Clipboard image input
- [ ] Drag and drop
- [ ] Image file selection

At least one reliable query-input path is required before proceeding.

---

## M5 — Normalization

Status: NOT STARTED

- [ ] Introduce RenderedIcon
- [ ] Introduce NormalizedIcon
- [ ] Normalize canvas dimensions
- [ ] Preserve aspect ratio
- [ ] Center content
- [ ] Normalize padding/transparency as needed
- [ ] Add normalization tests

---

## M6 — Similarity

Status: NOT STARTED

- [ ] Introduce IconDescriptor
- [ ] Introduce SimilarityEngine
- [ ] Implement lightweight local similarity
- [ ] Build descriptors for indexed resources
- [ ] Compare query against candidates
- [ ] Rank results
- [ ] Add regression fixtures/tests

No ML/cloud services.

---

## M7 — Search Experience

Status: NOT STARTED

- [ ] Display ranked matches
- [ ] Display useful similarity indication
- [ ] Display icon preview
- [ ] Display resource name
- [ ] Handle empty results
- [ ] Handle search errors

---

## M8 — Resource Actions

Status: NOT STARTED

- [ ] Open resource
- [ ] Reveal resource in project
- [ ] Copy resource name
- [ ] Copy `R.drawable.resource_name` where appropriate

---

## M9 — Incremental Indexing

Status: NOT STARTED

- [ ] Detect relevant resource changes
- [ ] Add new resources
- [ ] Update changed resources
- [ ] Remove deleted resources
- [ ] Avoid unnecessary full re-indexing
- [ ] Respect project lifecycle

---

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

---

# V0.1 Release

Required:

- drawable discovery
- gallery
- query input
- visual similarity
- ranked results
- basic resource actions
- acceptable IDE performance

---

# Later — Not Part of V0.1

Potential future work:

## V0.2

Duplicate-resource detection.

## V0.3

Compose ImageVector support.

## V0.4

Material Icons discovery.

## V0.5

Semantic text search and stronger visual descriptors.

## V0.6

Improved Figma/clipboard workflow.

## V0.7

IDE inspection when adding duplicate resources.

## V1.0

Stable public JetBrains Marketplace release.

Versions and ordering may change based on real user feedback.