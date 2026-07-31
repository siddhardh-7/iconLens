# M6 — Similarity: Design

Status: approved, pending implementation plan.

## Context

`ARCHITECTURE.md`'s pipeline is `IconSource → IconResource → IconRenderer →
RenderedIcon → ImageNormalizer → NormalizedIcon → IconDescriptor → IconIndex →
SimilarityEngine → Search Results → UI`. M5 built `NormalizedIcon` — a
canonical, comparable 64x64 image — but nothing consumes it yet. M6 is the
first milestone that actually compares icons: derive a compact descriptor
from a `NormalizedIcon`, compare two descriptors, and rank a query against a
set of candidates.

Per `PRD.md`'s Visual Similarity section: "V0.1 must work offline. The first
implementation should favour lightweight deterministic image comparison,"
listing perceptual hashing, difference hashing, and edge/shape comparison as
candidates, with the hard requirement that "the implementation must allow
the similarity algorithm to evolve later without redesigning resource
discovery or UI." Per `ROADMAP.md` M6, no ML/cloud services.

`IconIndex` appears in `ARCHITECTURE.md`'s pipeline diagram but not in M6's
own checklist — M6 says "Build descriptors for indexed resources," not
"build an index." A persistent/incremental index with change-tracking is
`ROADMAP.md` M9's job ("Incremental Indexing": detect changes, avoid
unnecessary full re-indexing). M6 works against whatever the gallery already
holds in memory, recomputed each time — no caching layer yet.

## Non-goals (explicitly deferred)

- Wiring this into the gallery UI or query panel. `IconLensToolWindowFactory`/
  `IconGalleryModel` are untouched this milestone — matches M4 (`QueryImage`)
  and M5 (`NormalizedIcon`), both of which introduced a type without wiring
  it into the UI. `ROADMAP.md` M7 ("Search Experience") is explicitly the
  "Display ranked matches" milestone.
- A persistent/cached `IconIndex` with incremental updates — `ROADMAP.md` M9.
- Any second similarity technique (perceptual hash, edge/shape comparison).
  `SimilarityEngine` is designed to be swappable later per PRD's requirement,
  but only one implementation (difference hash) ships now.
- Any ML/cloud/embedding-based approach — explicitly excluded by `PRD.md`
  and `ROADMAP.md` for V0.1.

## Components & data model

```kotlin
// IconDescriptor.kt
data class IconDescriptor(val hash: Long)

// SimilarityEngine.kt
interface SimilarityEngine {
    fun describe(icon: NormalizedIcon): IconDescriptor
    fun score(a: IconDescriptor, b: IconDescriptor): Double
}
```

`describe` and `score` live on one interface, not split apart: a
descriptor's bits only mean something to the matching comparison function
(a perceptual-hash descriptor and a difference-hash descriptor are not
cross-comparable), so if the algorithm evolves per PRD's requirement, it
evolves as one swappable unit, not two pieces that could drift out of sync.
`describe` takes a `NormalizedIcon`, not a raw `BufferedImage` — this is
exactly the type-level guarantee `NormalizedIcon` exists for: a descriptor
can only be computed from an already-normalized image, so two descriptors
are always comparable on equal footing regardless of where their source
image came from (gallery render or query paste).

`DHashSimilarityEngine` is the only concrete implementation, matching this
codebase's `CenteredImageNormalizer`/`DrawableIconRenderer` naming
convention (technique + role).

```kotlin
// SimilarityRanking.kt
data class ScoredMatch<T>(val candidate: T, val score: Double)

fun <T> rankBySimilarity(
    engine: SimilarityEngine,
    query: IconDescriptor,
    candidates: List<Pair<T, IconDescriptor>>,
): List<ScoredMatch<T>>
```

Generic over `T` — the caller's own candidate type (presumably
`RenderedIcon`, wired up in M7) — so neither `SimilarityEngine` nor this
function ever needs to know about `IconResource`/`RenderedIcon` directly,
per `AGENTS.md`'s "similarity must not know where an icon originated" rule.
Plain `map` + `sortedByDescending`, not part of the interface — ranking is
generic list logic, not part of the swappable algorithm.

## Algorithm: difference hash (dHash)

`DHashSimilarityEngine.describe(icon)`:

1. Resize `icon.image` (64x64) down to 9x8 using `Graphics2D` bilinear
   interpolation — the same scaling technique `CenteredImageNormalizer`
   already uses, so no new scaling approach to reason about, and
   deterministic given this project's fixed JDK/JBR 21 target.
2. Convert each of the 72 resulting pixels to grayscale using standard
   luminance weights: `gray = (r*299 + g*587 + b*114) / 1000`.
3. For each of the 8 rows, compare each of the 8 adjacent horizontal pixel
   pairs (`left > right` → bit `1`, else `0`) → 64 bits total, packed into
   a `Long` (bit order: row-major, left-to-right within each row).

`DHashSimilarityEngine.score(a, b)`:

```
hammingDistance = java.lang.Long.bitCount(a.hash xor b.hash)
score = 1.0 - hammingDistance / 64.0
```

`1.0` = identical descriptors, `0.0` = every bit differs. This 0.0–1.0 range
is deliberately the same shape as the 0–100%-style "similarity percentage"
`PRD.md`/M7 will eventually display (`score * 100`).

## Testing

- `describe()` on synthetic images with known geometric patterns (a
  left-half-black/right-half-white 64x64 image, a smooth horizontal
  gradient, a solid color) where the expected hash bits can be derived by
  hand from the algorithm and asserted exactly — not just "produces some
  hash," but the specific bit pattern the algorithm must produce.
- `describe()` on two `NormalizedIcon`s built from visually-similar but
  not-identical source images (e.g. the same shape shifted by a few pixels
  before normalization) — asserts a small but non-zero Hamming distance,
  not exact equality, demonstrating near-duplicates score high without
  requiring pixel-identical input.
- `score()`: identical descriptors → `1.0`; a descriptor XORed against
  itself with all 64 bits flipped → `0.0`; a descriptor differing in a
  known number of bits → the exact proportional score.
- `rankBySimilarity()`: a handful of candidates with hand-computed
  descriptors/Hamming distances → asserts correct descending order and
  correct scores, including an empty candidate list (returns an empty
  result, not an error) and a tie case (equal scores) — ties preserve the
  candidates' original relative order, since Kotlin's `sortedByDescending`
  is a stable sort; the test asserts this explicitly rather than leaving
  tie order unspecified.
