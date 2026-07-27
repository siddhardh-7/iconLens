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