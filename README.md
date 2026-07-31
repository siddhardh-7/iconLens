# IconLens

**Find the icon your Android project already has—before adding another one.**

IconLens is an Android Studio plugin for visually searching a project's drawable
resources. Give it an icon from a design, and it surfaces the closest matches
already in the codebase so you can reuse the right resource with confidence.

## Why IconLens?

In a growing Android app, the answer to “do we already have this icon?” is
rarely obvious. A designer may call an asset *location* while the existing
resource is named `ic_map_marker`. Filename search cannot make that connection.

The usual result is another copy of the same icon: more drawable clutter,
inconsistent choices across screens, and time spent searching files or asking
teammates.

IconLens makes that answer visual instead of textual.

## How it works

1. Paste, drag in, or choose an icon from your design.
2. IconLens compares it with your project's drawable resources.
3. Review visually ranked matches and reuse the existing resource.

It is designed for the moment you are about to add an asset—not after the
project has already accumulated duplicates.

## What you get

- Visual discovery across Android drawable resources in every project module.
- A browsable icon gallery with filename filtering.
- Query input from the clipboard, drag and drop, or an image file.
- Local visual similarity matching with ranked results.
- Resource details and quick actions to open, reveal, or copy a resource name
  or `R.drawable` reference.

For individual developers, that means less time hunting through `res/drawable`.
For teams, it means fewer duplicate assets, a smaller resource collection, and
more consistent icon use over time.

## Privacy and diagnostics

Icon search runs locally in Android Studio. Your project icons and source code
are not uploaded to perform a search.

To improve reliability, IconLens may offer optional crash reports and anonymous
usage metrics. These diagnostics are separate from visual search and will be
documented clearly, including what is collected and how to control it, with the
release.

## Compatibility

IconLens targets **Android Studio Quail 1 (2026.1.1 Patch 2)** on the IntelliJ
Platform 261.

## Install

Once IconLens is published, install it from Android Studio:

1. Open **Settings / Preferences** → **Plugins** → **Marketplace**.
2. Search for **IconLens**.
3. Install the plugin and restart Android Studio when prompted.

## Build from source

```bash
./gradlew build
```

To launch a sandbox Android Studio with the plugin installed:

```bash
./gradlew runIde
```

## Status

IconLens is preparing for its first public release. Feedback from Android
developers and teams using large drawable collections will guide what comes
next.

## License

License information will be added before the public release.
