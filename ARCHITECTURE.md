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
drag-and-drop) is the only consumer today, and it is not wired to anything else —
choosing or pasting a query image has no effect on the gallery below it. Comparing
the query against indexed resources is `SimilarityEngine` work, starting at M6.