<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# IconLens Changelog

## [Unreleased]

## [0.1.0] - 2026-08-24

### Added

- Visual gallery of project drawable resources (VectorDrawable XML, PNG, WebP, JPEG),
  discovered across every module in the project, with filename filtering.
- Query input from the clipboard, drag and drop, or an image file, including SVG.
- Local, offline visual similarity search with ranked, percentage-scored results.
- Resource actions: open a resource, reveal it in the Project view, copy its name,
  and copy its `R.drawable` reference.
- Incremental re-indexing on Refresh: added, changed, and removed resources are
  detected without a full re-render of unchanged icons.
