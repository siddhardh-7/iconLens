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

Status: DONE

- [x] Create IntelliJ Platform plugin project
- [x] Configure JDK/JBR 21 development environment
- [x] Verify generated project builds
- [x] Target Android Studio Quail instead of IntelliJ IDEA
- [x] Verify plugin packaging
- [x] Launch sandbox Android Studio using `runIde`
- [x] Verify plugin loads

Definition of done:

The plugin builds and loads successfully in the target development IDE.

Known follow-up (not part of M0's platform-targeting scope): `plugin.xml` still has
the JetBrains template's placeholder `<description>` and `<vendor>` values, which
`verifyPlugin` flags as invalid. `PRODUCT.md` already marks vendor/branding identity
as an open decision — fill these in once that's decided.

---

## M1 — IconLens Tool Window

Status: DONE

- [x] Register IconLens Tool Window
- [x] Display minimal empty state
- [x] Follow native IntelliJ UI conventions
- [x] Verify Tool Window opens correctly

No drawable scanning yet.

Verified: plugin loads and the `IconLens` tool window extension registers with no
errors in a sandbox run, both with no project open and with this project opened
(`ProjectUtil - Opening existing project` in idea.log, zero ERROR-level log lines).
Tool window content is created lazily on first click per platform convention, which
this headless verification can't drive — a manual `./gradlew runIde` + click on the
IconLens stripe icon is the last visual check before relying on this further.

---

## M2 — Drawable Discovery

Status: DONE

- [x] Define IconSource
- [x] Define IconResource
- [x] Implement DrawableIconSource
- [x] Discover drawable resources
- [x] Handle relevant drawable resource directories
- [x] Avoid assuming only one Android module
- [x] Run discovery outside expensive EDT work
- [x] Add resource-discovery tests where practical

Verified: 8 classifier tests + 5 representative-picker tests + 2 DrawableIconSource integration tests, all passing (`./gradlew test`); full `./gradlew build` green.

---

## M3 — Rendering & Gallery

Status: DONE

- [x] Render supported raster resources
- [x] Render supported VectorDrawables
- [x] Gracefully handle unsupported/malformed resources
- [x] Display project icon gallery
- [x] Display resource names
- [x] Add filename filtering

Verified: 13 PathDataInterpreter tests + 9 VectorDrawableParser tests + 4 DrawableIconRenderer tests + 3 IconGalleryModel tests, all passing (`./gradlew test`); full `./gradlew build` green.

---

## M4 — Query Input

Status: DONE

- [x] Query preview
- [x] Clipboard image input
- [x] Drag and drop
- [x] Image file selection

Query images support PNG/WebP/JPEG (`ImageIO`), VectorDrawable XML and SVG (shared
vector renderer / IntelliJ's bundled `SVGLoader`), and raw SVG/VectorDrawable markup
pasted as plain text (e.g. a "copy SVG code" action). A Clear button resets the query
and the name filter.

Verified: 14 QueryImageLoadingTest + 16 VectorDrawableParserTest +
1 VectorDrawableRenderingTest + 6 DrawableIconRendererTest tests (68 tests total across
the suite), all passing (`./gradlew test`); full `./gradlew build` green. Also verified
directly against real-world icons from an external Android project (KYN): fixed a
concurrency race between overlapping paste/choose/drop loads, a post-disposal UI
mutation, an uncaught clipboard exception, unscaled preview images ballooning the
layout, blocking I/O on the wrong dispatcher, incomplete drop-target coverage,
duplicated file-filter logic, 3/4-digit hex colors, `evenOdd` fill winding,
`<clip-path>`, linear gradients, and non-square icons being stretched instead of fit.

Known gap, deliberately out of scope: `.xml` files are classified as `VECTOR_DRAWABLE`
by extension alone, so non-vector drawable XML (`<shape>`, `<selector>`,
`<layer-list>`) still fails to render — fixing this needs content-based
classification, a bigger scope decision than a parser fix. Radial/sweep gradients
also remain unsupported pending real-world evidence they're needed.

---

## M5 — Normalization

Status: DONE

- [x] Introduce NormalizedIcon
- [x] Normalize canvas dimensions
- [x] Preserve aspect ratio
- [x] Center content
- [x] Normalize padding/transparency as needed
- [x] Add normalization tests

`RenderedIcon`/`QueryImage` images become a canonical, comparable `NormalizedIcon`:
content is cropped to its non-transparent bounds, fit-centered into a 64x64 canvas
(not stretched), and composited onto a white background. Not wired into the
gallery/query UI this milestone — see
`docs/superpowers/specs/2026-07-31-normalization-design.md`.

Verified: 4 IconFitScalingTest + 5 CenteredImageNormalizerTest new tests (77 tests
total across the suite), all passing (`./gradlew test`); full `./gradlew build`
green.

---

## M6 — Similarity

Status: DONE

- [x] Introduce IconDescriptor
- [x] Introduce SimilarityEngine
- [x] Implement lightweight local similarity
- [x] Build descriptors for indexed resources
- [x] Compare query against candidates
- [x] Rank results
- [x] Add regression fixtures/tests

No ML/cloud services. `SimilarityEngine` computes a difference hash (dHash) from
a `NormalizedIcon`'s 64x64 canvas (bilinear downsample to 9x8, grayscale, row-wise
adjacent-pixel comparison, 64-bit signature) and compares two descriptors via
Hamming distance into a 0.0-1.0 score. `rankBySimilarity` sorts arbitrary
candidates by score against a query descriptor. Not wired into the gallery/query
UI this milestone — see `docs/superpowers/specs/2026-07-31-similarity-design.md`.

Verified: 6 DHashSimilarityEngineTest + 2 SimilarityRankingTest new tests (86 tests
total across the suite), all passing (`./gradlew test`); full `./gradlew build`
green.

---

## M7 — Search Experience

Status: DONE

- [x] Display ranked matches
- [x] Display useful similarity indication
- [x] Display icon preview
- [x] Display resource name
- [x] Handle empty results
- [x] Handle search errors

Verified: `./gradlew build` and `./gradlew test` green after wiring
`rankRenderedIcons` into `IconLensToolWindowFactory`'s query panel. The
threading model (EDT-only mutation of `activeQueryImage`, values captured
into `val`s before crossing into `Dispatchers.IO` coroutines), the
guard/ordering sequencing (browse↔ranked mode switching), and the
`RenderedIcon.Failed`-exclusion from ranked output were verified by tracing
every read/write site in the diff. Manual `runIde` verification of the
visible behavior (pasting/choosing a query image re-ranks the gallery with
percentage badges and disables the filter field; Clear returns to plain
browse mode; Refresh mid-search stays in ranked mode; a failed query load
leaves the gallery untouched) is still pending — run this checklist before
relying on the UI behavior in production. See
`docs/superpowers/specs/2026-08-01-search-experience-design.md` for the
design and `ARCHITECTURE.md`'s "Search Experience (M7)" section for the
implementation summary.

---

## M8 — Resource Actions

Status: DONE

- [x] Open resource
- [x] Reveal resource in project
- [x] Copy resource name
- [x] Copy `R.drawable.resource_name` where appropriate

Verified: `./gradlew build` and `./gradlew test` green. Manual `runIde` UI
checklist has now been run by a human against a real Android project
(right-click menu, double-click-to-open, Reveal in Project View). Two real
bugs surfaced during that pass and were fixed:

- `PopupHandler.installSelectionListPopup` only shows the popup when the
  right-clicked row is already the selected one (`ListUtil.isPointOnSelection`
  gates it) — it never selects on right-click. A first right-click on any
  not-yet-selected tile silently did nothing. Fixed by installing a plain
  `PopupHandler` that selects the clicked row before showing the menu.
- `ProjectView.select()` silently no-ops if the Project tool window has never
  been shown in the session — "Reveal in Project View" only worked when the
  Project view was already open. Fixed by activating the tool window first
  (`ToolWindow.activate { ... }`) and calling `select()` in its callback.

Copy Name / Copy Reference confirmed working via clipboard checks. See
`docs/superpowers/specs/2026-08-07-resource-actions-design.md` for the
design and `ARCHITECTURE.md`'s "Resource Actions (M8)" section for the
implementation summary.

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