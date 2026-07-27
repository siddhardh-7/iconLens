# IconLens — Product Requirements Document

## Product

IconLens is an Android Studio plugin that helps Android developers discover existing icons in their project before adding new assets.

The primary workflow is:

Design icon
→ Copy/Paste into IconLens
→ Search existing project icons
→ See visually similar resources
→ Reuse an existing icon

---

# Problem

Android applications frequently contain hundreds of drawable resources.

When implementing a design, developers repeatedly need to determine:

"Does this icon already exist?"

Current approaches include:

- searching filenames
- browsing `res/drawable`
- using Android Studio Resource Manager
- opening VectorDrawable XML files
- asking another developer
- simply adding another icon

Filename search is insufficient.

A designer may call something:

"location"

while the project resource is named:

`ic_map_marker`

The resources may be visually identical despite having unrelated names.

This creates:

- duplicate resources
- inconsistent icon usage
- larger resource collections
- developer friction
- wasted implementation time

---

# Vision

IconLens should become the fastest way to answer:

"Do we already have this visual asset?"

Long term, IconLens can become a visual asset search engine for Android projects.

V0.1 focuses exclusively on solving icon discovery well.

---

# Target Users

Primary users:

Android developers working on medium and large applications.

Particularly teams using:

- Kotlin
- Jetpack Compose
- XML
- VectorDrawable
- design systems
- Figma-driven workflows

---

# Core Experience

The desired interaction should take only a few seconds.

1. Developer receives an icon from a design.
2. Developer copies or exports the icon.
3. Developer opens IconLens.
4. Developer pastes/drops/selects the icon.
5. IconLens searches project resources.
6. Similar resources appear ranked.
7. Developer reuses an existing resource.

Example:

Query:
calendar icon

Results:

96%  ic_calendar_outline
91%  ic_calendar
78%  ic_event

The similarity percentage is a ranking aid, not a mathematical guarantee.

---

# V0.1 Scope

V0.1 solves one core problem:

Find existing project drawable resources that visually resemble a supplied icon.

---

## Supported Project Resources

Initial support:

- Android VectorDrawable XML
- PNG
- WebP
- JPEG where applicable

Resources should be discovered from Android drawable resource directories.

The implementation must not assume the application contains only one module.

---

## Project Icon Gallery

IconLens should provide a visual gallery of discovered project icons.

Each resource should expose useful information such as:

- preview
- resource name
- resource type
- module/source when relevant

Filename filtering should be supported.

Example:

Search:
`calendar`

Results:

`ic_calendar`
`ic_calendar_outline`
`ic_calendar_small`

This is ordinary filename filtering.

Semantic text search is not part of V0.1.

---

## Query Input

Users should eventually be able to provide a query icon through:

- clipboard paste
- drag and drop
- image file selection

The query should be previewed before/while searching.

---

## Visual Similarity

V0.1 must work offline.

The first implementation should favour lightweight deterministic image comparison.

Possible techniques include:

- perceptual hashing
- difference hashing
- edge/shape comparison

The implementation must allow the similarity algorithm to evolve later without redesigning resource discovery or UI.

---

## Results

Search results should be ranked from most to least visually similar.

Each result should provide:

- icon preview
- resource name
- similarity score/rank
- resource type
- module where useful

---

## Result Actions

Users should eventually be able to:

- open the resource
- reveal the resource in Project view
- copy the resource name
- copy a reference such as `R.drawable.ic_calendar`

---

# Privacy

V0.1 must operate locally.

Project resources, source code, pasted images and filenames must not be uploaded to external services.

No account should be required.

---

# Performance

IconLens must not noticeably freeze Android Studio.

Expensive operations such as:

- project scanning
- image rendering
- normalization
- hashing
- indexing

must not block the IDE UI thread.

The architecture should eventually support projects containing 1,000+ drawable resources.

---

# V0.1 Non-Goals

Do not build yet:

- cloud AI
- LLM integration
- semantic search
- CLIP/image embeddings
- vector databases
- Figma API integration
- Compose ImageVector indexing
- Material Icon indexing
- duplicate inspections
- automatic code replacement
- automatic drawable deletion
- analytics
- accounts
- backend infrastructure

---

# Future Direction

Potential later capabilities include:

## Duplicate Detection

Detect visually identical or near-identical project resources.

Example:

`ic_close`
`ic_cross`

Possible duplicate: 99%

---

## Compose Icons

Search resources such as:

`Icons.Outlined.Search`

`Icons.Rounded.Home`

and project-defined ImageVectors.

---

## Semantic Search

Allow searches such as:

"outlined calendar"

"back arrow"

"location marker"

without relying on filenames.

---

## IDE Inspection

When a developer adds a new resource, IconLens could detect an existing similar resource and suggest reuse.

It must never automatically delete or replace project resources without explicit developer action.

---

# Success Criteria for V0.1

V0.1 is successful when a developer can:

1. Install IconLens in Android Studio.
2. Open the IconLens Tool Window.
3. Discover project drawable resources.
4. Browse them visually.
5. Provide a query icon.
6. Search locally for visually similar project icons.
7. See ranked matches.
8. Open or copy an existing resource.

The most important product question is:

"Does IconLens help developers find resources they would otherwise recreate or spend time manually searching for?"