# IconLens Release README Design

## Purpose

Replace the generated IntelliJ plugin template README with a release-facing page
that helps Android developers recognise IconLens as the practical solution to
duplicate drawable icons.

## Audience

- Individual Android developers who need to move quickly.
- Teams maintaining medium or large Android applications.

## Message

IconLens helps developers find an existing project icon before adding a duplicate.
It solves the gap between a design's description of an icon and the unrelated
resource filename already present in the project.

## Tone

Practical and engineering-focused: save implementation time, reduce drawable
asset bloat, and support consistent icon use.

## README Structure

1. Headline and short value proposition.
2. The duplicate-icon problem and why filename search fails.
3. The three-step workflow: provide an icon, view ranked visual matches, reuse
   the existing resource.
4. Concrete benefits for developers and teams.
5. Release feature list: project drawable gallery, filename filtering, clipboard,
   drag-and-drop and file query input, local visual similarity/ranking, and
   resource actions.
6. Screenshot or GIF placeholder for a future real product demo.
7. Local-first privacy statement: search runs locally and project icons/source
   code are not uploaded.
8. Transparent diagnostics note: optional crash reporting and anonymous product
   metrics, with user control.
9. Android Studio compatibility, installation, and concise development/build
   instructions.

## Constraints

- Do not claim a marketplace listing, installation URL, telemetry provider, or
  opt-in mechanism until those details are decided.
- Do not claim project data never leaves the machine without the qualifier that
  diagnostics are optional and exclude project icons/source code.
- Keep shipped-feature statements aligned with the release build.
- Remove generated template content and unrelated JetBrains promotional badges.

## Verification

Review the rendered Markdown for clear hierarchy, working local links, and
accurate product claims before release.
