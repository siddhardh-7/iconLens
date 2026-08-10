# IconLens — Architecture

## Goal

IconLens should remain small, fast, local-first and extensible.

The architecture separates:

- icon discovery
- rendering
- normalization
- description/fingerprinting
- indexing
- similarity
- search
- IDE UI

This prevents Android resource details from leaking throughout the application.

---

# High-Level Pipeline

IconSource
↓
IconResource
↓
IconRenderer
↓
RenderedIcon
↓
ImageNormalizer
↓
NormalizedIcon
↓
IconDescriptor
↓
IconIndex
↓
SimilarityEngine
↓
Search Results
↓
UI

---

# IconSource

`IconSource` discovers icons available in a project.

Conceptual API:

```kotlin
interface IconSource {
    suspend fun discover(): List<IconResource>
}
```

---

# IconResource

`IconResource` is what `IconSource` produces — one per distinct drawable resource name
per module, after density-variant dedup (see
`docs/superpowers/specs/2026-07-27-drawable-discovery-design.md` for full rationale).

```kotlin
enum class IconResourceType { VECTOR_DRAWABLE, PNG, WEBP, JPEG }

data class IconResource(
    val name: String,
    val type: IconResourceType,
    val file: VirtualFile,
    val moduleName: String,
)
```

It classifies by file extension only — it never parses file contents to validate a
drawable (e.g. confirming a `.xml` file really is a vector drawable, not a
selector/layer-list). That validation belongs to `IconRenderer`, so a malformed file
fails in isolation there, not during discovery.

---

# DrawableIconSource

`DrawableIconSource` is the first concrete `IconSource`. It walks every module's
content roots looking for `res/drawable` and `res/drawable-<qualifier>` directories
(not `mipmap-*`), classifying files by extension and deduping density-qualified
duplicates (e.g. `drawable-hdpi/ic_calendar.png` and `drawable-xhdpi/ic_calendar.png`)
down to one `IconResource` per resource name per module — preferring the
density-less `drawable/` variant when present.

Android Gradle sync represents one Gradle module as several IntelliJ modules (root
project, the app module, its main source set, and so on), and their content roots can
nest/overlap. Before the per-module density dedup above, `pickRepresentatives` first
collapses candidates that resolve to the exact same physical file (by `VirtualFile.path`)
regardless of which module's scan found it, keeping the most specific (longest) module
name — otherwise the same drawable appears once per module in that nesting chain.

It does not depend on the `org.jetbrains.android` plugin/facet APIs — a plain VFS
directory-name scan is sufficient for V0.1's bar and avoids an unjustified dependency;
this can be revisited if it proves too imprecise on real multi-flavor projects.

`discover()` performs its VFS/module-model reads inside `readAction { }`, so it never
blocks the EDT. A single unreadable/malformed candidate is caught, logged, and
skipped — it never aborts the whole scan.

---

# RenderedIcon

`RenderedIcon` is what `IconRenderer` produces — a sealed result, not an exception path,
so a single malformed/unsupported resource can never break the batch:

```kotlin
sealed interface RenderedIcon {
    val resource: IconResource

    data class Rendered(override val resource: IconResource, val image: BufferedImage) : RenderedIcon
    data class Failed(override val resource: IconResource, val reason: String) : RenderedIcon
}

interface IconRenderer {
    suspend fun render(resource: IconResource): RenderedIcon
}
```

---

# DrawableIconRenderer

`DrawableIconRenderer` is the first concrete `IconRenderer`. It decodes PNG/JPEG (and
WebP, where the platform's `ImageIO` can) via the JDK's `ImageIO`, and VectorDrawable
XML via a hand-written `PathDataInterpreter`/`VectorDrawableParser` pair supporting
`<vector>`/`<path>`/`<group>`/`<clip-path>`, solid fill/stroke colors (3/4/6/8-digit
hex), `android:fillType="evenOdd"` winding, linear-gradient fills via `<aapt:attr>`,
and group transforms. Animated-vector-drawable wrappers, radial/sweep gradients, and
non-vector drawable XML (`<shape>`, `<selector>`, `<layer-list>` — misclassified as
`VECTOR_DRAWABLE` today since `IconResource` classifies by extension only) remain
unsupported by design, not by oversight — see
`docs/superpowers/specs/2026-07-28-rendering-gallery-design.md`. Both raster and
vector rendering fit non-square sources into the render square (uniform scale,
centered, transparent margins) instead of stretching them to fill it.

All rendering runs off the EDT; `IconGalleryModel.loadGallery`/`filterByName` keep the
gallery's load-and-filter logic Swing-free, so `IconLensToolWindowFactory` only ever
displays a list it's handed.

---

# QueryImage / QueryImageLoading

M4 adds the other input the eventual `SimilarityEngine` will need: not a project
resource, but a query image the developer supplies from outside the project (a
screenshot, a file they're about to add, an icon copied from a design tool). This is
a separate acquisition path, not a variant of `IconSource`/`IconRenderer` — nothing
about it assumes the image originated from an Android project or ever will.

```kotlin
sealed interface QueryImage {
    data class Loaded(val image: BufferedImage, val sourceDescription: String) : QueryImage
    data class Failed(val reason: String) : QueryImage
}
```

`QueryImageLoading.kt` provides three loaders, all returning `QueryImage` (or `null`
only when there is genuinely nothing to load, e.g. an empty clipboard):

- `loadQueryImageFromFile(file)` — dispatches by extension: `ImageIO` for
  PNG/JPEG/WebP, the shared vector renderer (below) for `.xml` (VectorDrawable), and
  IntelliJ Platform's bundled `com.intellij.util.SVGLoader` for `.svg` — a core
  platform module, not a new dependency. SVGs with no explicit width/height are
  re-rendered at a higher scale instead of staying blurry.
- `loadQueryImageFromTransferable(transferable, sourceDescription)` — backs both
  paste and drag-and-drop. Tries `DataFlavor.imageFlavor` (raw image data), then
  `DataFlavor.javaFileListFlavor` (delegates to `loadQueryImageFromFile`), then
  `DataFlavor.stringFlavor` sniffed for a leading `<svg`/`<vector` tag — covering a
  "copy SVG code" clipboard action, which puts plain text on the clipboard, not an
  image or a file.
- `loadQueryImageFromClipboard()` — reads the system clipboard and delegates to
  `loadQueryImageFromTransferable`.

VectorDrawable XML rendering is shared, not duplicated: `VectorDrawableRendering.kt`'s
`renderVectorDrawable(xml, size)` is called by both `DrawableIconRenderer.decodeVector`
(gallery tiles, the fixed `RENDER_SIZE`) and `QueryImageLoading` (query preview, a
larger size for a crisper result) — one parser, one renderer, two callers.

`IconLensToolWindowFactory`'s query panel (Paste / Choose file... / Clear, plus
drag-and-drop) feeds the `SimilarityEngine`-based ranking described in
"Search Experience (M7)" below — loading a query image re-ranks the gallery
instead of only updating the preview.

---

# NormalizedIcon

`NormalizedIcon` is what `ImageNormalizer` produces from any `BufferedImage` —
a gallery `RenderedIcon.Rendered.image`, or (once query images need to be
compared, M6) a `QueryImage.Loaded.image`. It has no knowledge of where that
image came from:

```kotlin
data class NormalizedIcon(val image: BufferedImage)
```

`image` is always exactly 64x64 (`NORMALIZED_SIZE`), fully opaque, with content
cropped to its non-transparent bounds, fit-centered (not stretched), and
composited onto a white background — so two icons authored with different
amounts of baked-in padding, or decoded from sources of very different native
sizes, become directly comparable pixel grids for whichever M6 similarity
technique gets picked (perceptual hash, difference hash, or edge/shape
comparison — all still open per `PRD.md`).

```kotlin
interface ImageNormalizer {
    fun normalize(image: BufferedImage): NormalizedIcon
}
```

`CenteredImageNormalizer` is the only concrete implementation. Not `suspend` —
it is deterministic, synchronous pixel work on an already-decoded image already
in memory, the same pattern as `DrawableIconRenderer`'s private decode helpers.

The "uniform scale-to-fit, centered" arithmetic used by `CenteredImageNormalizer`,
`DrawableIconRenderer.scaleToSquare`, and
`VectorDrawableRendering.renderVectorDrawable` is one shared function,
`fitScaleAndOffset` in `IconFitScaling.kt`, not three copies of the same formula.

`NormalizedIcon`/`ImageNormalizer` are not wired into the gallery UI or the query
panel — they are an internal artifact for the future `SimilarityEngine` (M6+), not
a display concern. See
`docs/superpowers/specs/2026-07-31-normalization-design.md` for full rationale.

---

# IconDescriptor / SimilarityEngine

`IconDescriptor` is what `SimilarityEngine` produces from a `NormalizedIcon` — a
compact, comparable signature:

```kotlin
data class IconDescriptor(val hash: List<Long>)
```

```kotlin
interface SimilarityEngine {
    fun describe(icon: NormalizedIcon): IconDescriptor
    fun score(a: IconDescriptor, b: IconDescriptor): Double
}
```

`describe` and `score` live on one interface rather than being split apart: a
descriptor's bits are only meaningful to the matching comparison function (a
perceptual-hash descriptor and a difference-hash descriptor aren't
cross-comparable), so if the algorithm evolves later per `PRD.md`'s "must allow
the similarity algorithm to evolve later" requirement, it evolves as one unit,
not two pieces that could drift out of sync.

`DHashSimilarityEngine` is the only concrete implementation: a difference hash
(dHash). A single row-wise-only dHash over a small grid collapses most simple/
flat icon shapes (badges, rings, glyphs) into near-identical hashes — most of a
small bit budget ends up encoding "is there a roughly circular/rectangular
shape here," which is true of nearly every icon, leaving too few bits for the
actual distinguishing detail. To get more discriminative scores without any
ML/cloud dependency, `describe` computes two directional hashes from the 64x64
`NormalizedIcon.image` (bilinear resize, same technique `CenteredImageNormalizer`
uses): a horizontal component (resize to 17x16, pack 16 row-wise adjacent-pixel
comparisons per row × 16 rows = 256 bits) and a vertical component (resize to
16x17, pack 16 column-wise adjacent-pixel comparisons per column × 16 columns =
256 bits), concatenated into an 8-`Long`/512-bit `IconDescriptor.hash`. `score`
compares two hashes via total Hamming distance across all 8 longs into a `1.0`
(identical) to `0.0` (every bit differs) range — the same shape as the
percentage `PRD.md`/M7 displays.

```kotlin
data class ScoredMatch<T>(val candidate: T, val score: Double)

fun <T> rankBySimilarity(
    engine: SimilarityEngine,
    query: IconDescriptor,
    candidates: List<Pair<T, IconDescriptor>>,
): List<ScoredMatch<T>>
```

`rankBySimilarity` is generic over the caller's own candidate type, so neither
it nor `SimilarityEngine` needs to know about `IconResource`/`RenderedIcon`
directly, per the "similarity must not know where an icon originated" rule.

"Search Experience (M7)" below wires this into the gallery UI and query panel.
See `docs/superpowers/specs/2026-07-31-similarity-design.md` for full rationale
on the similarity design itself.

---

# Search Experience (M7)

`rankRenderedIcons` (`IconGalleryModel.kt`) is the pure, Swing-free bridge between
a query `BufferedImage` and the gallery's `RenderedIcon.Rendered` list:

```kotlin
fun rankRenderedIcons(
    icons: List<RenderedIcon.Rendered>,
    query: BufferedImage,
    normalizer: ImageNormalizer,
    engine: SimilarityEngine,
): List<ScoredMatch<RenderedIcon.Rendered>>
```

It normalizes and describes the query and every candidate, then delegates to
`rankBySimilarity`. Callers filter out `RenderedIcon.Failed` before calling it —
malformed/unsupported icons are excluded from ranked output entirely rather than
shown unscored, since there's nothing to compare them with.

`IconLensToolWindowFactory` has two mutually exclusive display modes, tracked by
a single `activeQueryImage: BufferedImage?`:

- **Browse mode** (`activeQueryImage == null`): the pre-existing filename-filtered
  gallery. The filter field is enabled.
- **Ranked mode** (`activeQueryImage != null`): every renderable icon, reordered
  best-match-first, filter field disabled and cleared. Loading a new query image
  always fully replaces the previous ranked results — there's no combining rank
  with a name filter, no top-N cap, and no score threshold.

Both modes render through one `DefaultListModel<GalleryTile>`, where
`GalleryTile(icon: RenderedIcon, score: Double?)` — `score` is `null` in browse
mode and a `0.0..1.0` similarity in ranked mode. `IconTileRenderer` paints a
percentage badge in the icon's corner only when `score != null`.

Ranking runs on the same `Dispatchers.IO` scope the gallery load already uses —
recomputed from scratch on every search and on every `Refresh` while a query is
active. There is no persistent index or descriptor cache; that's `ROADMAP.md`
M9 ("Incremental Indexing"), not this milestone. See
`docs/superpowers/specs/2026-08-01-search-experience-design.md` for full
rationale.

---

# Resource Actions (M8)

`GalleryResourceActions.kt` adds a right-click context menu and a
double-click-to-open shortcut on the gallery's `JBList<GalleryTile>`,
installed via `installGalleryResourceActions(project, list)`:

```kotlin
internal fun installGalleryResourceActions(project: Project, list: JBList<GalleryTile>)
```

Four `AnAction`s read the selected `GalleryTile.icon.resource`
(`IconResource`) and act on it — Open (`FileEditorManager.openFile`), Reveal
in Project View (`ProjectView.select`), Copy Name (`resource.name`), and
Copy Reference (`androidResourceReference`, a pure `"R.drawable.<name>"`
formatter, unit-tested independently of the `AnAction`/Swing plumbing). All
four work identically on `RenderedIcon.Rendered` and `RenderedIcon.Failed`
tiles, since both carry a real `IconResource`/`VirtualFile` — a rendering
failure never blocks acting on the underlying resource.

The popup uses the platform's `PopupHandler.installSelectionListPopup`,
which moves the list's selection to the row under the cursor before
showing the menu, so the menu always acts on what was actually clicked.
Open and Reveal check `resource.file.isValid` first and no-op on a stale
file (deleted/moved since the last gallery load) rather than throwing;
Copy Name/Reference only read the already-extracted `resource.name`
string, so they don't need that guard. No multi-select batching and no
clipboard-copy confirmation UI in V0.1.