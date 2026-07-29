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
XML via a hand-written `PathDataInterpreter`/`VectorDrawableParser` pair supporting a
deliberate subset: `<vector>`/`<path>`/`<group>`, solid fill/stroke colors, and basic
group transforms. Gradients, clip-paths, and animated-vector-drawable wrappers are
unsupported by design, not by oversight — see
`docs/superpowers/specs/2026-07-28-rendering-gallery-design.md`.

All rendering runs off the EDT; `IconGalleryModel.loadGallery`/`filterByName` keep the
gallery's load-and-filter logic Swing-free, so `IconLensToolWindowFactory` only ever
displays a list it's handed.