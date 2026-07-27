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