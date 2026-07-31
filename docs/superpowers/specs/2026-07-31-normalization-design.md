# M5 — Normalization: Design

Status: approved, pending implementation plan.

## Context

`ARCHITECTURE.md`'s pipeline is `IconSource → IconResource → IconRenderer →
RenderedIcon → ImageNormalizer → NormalizedIcon → IconDescriptor → IconIndex →
SimilarityEngine → Search Results → UI`. M2-M4 built everything up to and including
`RenderedIcon` (gallery) and a separate query-input path (`QueryImage`, M4) that
produces a decoded `BufferedImage` from clipboard/drag-drop/file input, with no
canvas normalization at all.

Both `RenderedIcon.Rendered.image` and `QueryImage.Loaded.image` are already
`BufferedImage`, but they are not comparable to each other or to one another
consistently:

- Gallery-rendered icons (`DrawableIconRenderer`) are always exactly 48x48
  (`RENDER_SIZE`), fit-centered, thanks to the fit-not-stretch fix done during M4
  hardening.
- Query images loaded from a raster file/clipboard paste (`ImageIO`/`imageFlavor`)
  keep their native size and aspect ratio — a pasted screenshot could be
  4000x3000. Query images loaded from SVG/VectorDrawable XML get fit-centered into
  a 128x128 canvas (`QUERY_VECTOR_RENDER_SIZE`) — a different size from the gallery
  path, for a different reason (crisper preview, not comparison).

M5 introduces a single normalized shape both paths can be converted into, so that
M6's `SimilarityEngine` can compare a query against indexed candidates on equal
footing, regardless of source.

## Non-goals (explicitly deferred)

- `IconDescriptor`/`IconIndex`/`SimilarityEngine` (M6) — no hashing, no descriptor
  extraction, no comparison, no ranking. This milestone stops at "here is a
  canonically-shaped image."
- Wiring `NormalizedIcon` into the gallery UI or the query panel. `NormalizedIcon`
  is an internal comparison-pipeline artifact, not a display artifact — the
  gallery's current transparent-background tiles remain the correct *display*
  behavior. ROADMAP's M5 checklist has no UI item (unlike M3's "Display project
  icon gallery"), so nothing in this milestone touches
  `IconLensToolWindowFactory`/`IconGalleryModel`.
- Deciding M6's actual similarity algorithm (perceptual hash / difference hash /
  edge comparison — all still open per `PRD.md`). Normalization is designed to be
  a reasonable input to any of them, not tuned for one.
- The `<shape>`/`<selector>`/`<layer-list>` misclassification gap and
  radial/sweep gradient support — unrelated, already tracked separately in
  `ROADMAP.md`'s M4 entry.

## Components & data model

```kotlin
// NormalizedIcon.kt
data class NormalizedIcon(val image: BufferedImage)

interface ImageNormalizer {
    fun normalize(image: BufferedImage): NormalizedIcon
}
```

`normalize` takes a plain `BufferedImage`, not `RenderedIcon` or `QueryImage` —
callers extract `.image` from `RenderedIcon.Rendered`/`QueryImage.Loaded`
themselves. `Failed` variants have no image and are never passed in; there is
nothing to normalize for a resource/query that already failed to render/decode.
This keeps `ImageNormalizer` decoupled from where the image originated, matching
`AGENTS.md`'s "Similarity must not know where an icon originated" rule one stage
early.

`ImageNormalizer` is an interface with one concrete implementation,
`CenteredImageNormalizer`, matching `IconRenderer`/`DrawableIconRenderer`'s shape
for pipeline-diagram consistency (a deliberate choice discussed and confirmed
during brainstorming, even though — unlike `IconRenderer`, which has raster vs.
vector decoders — there is only one normalization algorithm anticipated today).

`NormalizedIcon.image` is always exactly `NORMALIZED_SIZE x NORMALIZED_SIZE`
(64x64 — a middle ground for perceptual-hash/edge-comparison algorithms: enough
detail for shape comparison without the 4x pixel cost of 128x128), fully opaque
(no alpha channel semantics to worry about downstream), with content fit-centered
and any transparent background flattened to white.

## Algorithm

`CenteredImageNormalizer.normalize(image)`:

1. **Crop to content.** Scan `image` for the bounding box of non-transparent
   pixels (alpha > 0). Crop to that box before scaling. This makes normalization
   idempotent regardless of how much padding the source already baked in — an
   icon whose vector content fills its full viewBox and one with a hand-authored
   margin around the glyph end up filling the 64x64 canvas the same amount,
   which matters for pixel/hash-based comparison later. If the image is entirely
   transparent (nothing to crop — a genuine edge case, not expected in practice
   but must not crash), skip cropping and use the full image bounds; the result
   is a plain white 64x64 image.
2. **Fit, don't stretch.** Compute one uniform scale factor (limited by the
   larger of the cropped content's width/height) so the content fits inside the
   64x64 canvas without exceeding it, then center it — the same
   scale-by-larger-dimension-and-center approach as the fit-not-stretch fix
   already applied to `DrawableIconRenderer.scaleToSquare` and
   `VectorDrawableRendering.renderVectorDrawable`, generalized to arbitrary
   source dimensions instead of a fixed render size.
3. **Composite onto white.** Draw the scaled, centered content onto an opaque
   white 64x64 `BufferedImage` (`TYPE_INT_RGB`, no alpha) using standard SRC_OVER
   alpha compositing, so translucent/anti-aliased edge pixels blend correctly
   into the white background instead of a hard transparent/opaque cutoff.

## Reuse: shared fit/scale/center helper

Once this lands, the "uniform scale + center offset, given a source
width/height and a target square size" formula exists three times:
`DrawableIconRenderer.scaleToSquare`, `VectorDrawableRendering.renderVectorDrawable`,
and this normalizer. Extract one small internal helper (e.g.
`fitScaleAndOffset(sourceWidth, sourceHeight, targetSize): FitTransform` returning
the scale factor and x/y offsets) that all three call, rather than a third copy of
the same arithmetic. This is a small, targeted refactor of code this work already
touches conceptually — not a speculative abstraction.

## Threading

`normalize()` is a plain synchronous function, not `suspend` — it does no I/O and
no VFS/PSI access (nothing here needs `readAction { }`), just deterministic pixel
manipulation on an already-decoded `BufferedImage` already living in memory. This
matches `DrawableIconRenderer`'s own private decode helpers (`decodeRaster`,
`decodeVector`, `scaleToSquare` are plain synchronous functions; only the outer
`IconRenderer.render` is `suspend`, to bracket `readAction`/cancellation). Whatever
eventually calls `normalize()` (M6) is already running off the EDT.

## Testing

- Square, already content-filling input → output unchanged in proportion, content
  still centered, background flattened to white.
- Non-square input (e.g. a tall/narrow icon like the real `map_pin.xml` case) →
  fit-centered without stretching, matching the same assertion style as the
  existing `VectorDrawableRenderingTest`/`DrawableIconRendererTest` non-square
  regression tests.
- Input with a large baked-in transparent margin around the content → cropped
  and re-centered so the content fills the canvas comparably to a
  tightly-cropped equivalent input (asserts the two normalize to a similar
  content-to-canvas ratio, not byte-identical images).
- Fully transparent input → does not throw; produces a plain opaque white 64x64
  image.
- Transparency flattening → a source with semi-transparent pixels produces
  fully-opaque output pixels at every point (no alpha channel meaning left),
  with a correctly-blended color at anti-aliased edges (not a hard on/off cutoff).
- `fitScaleAndOffset` helper, as its own directly-testable unit: a few direct
  cases (square-to-square, wide-to-square, tall-to-square) covering the
  arithmetic once, rather than only indirectly through the three callers.
