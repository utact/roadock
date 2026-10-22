# ROADOCK

An Android app and home screen widget for GitHub project checklists and personal tasks.

## Features

- Human completion sync; read-only Agent tasks
- Single-column or split project lists with independent scrolling
- Project switching and cached offline viewing
- Adjustable widget text size and background transparency
- Local personal tasks with drag sorting and swipe deletion

## Install

Download the APK from [Releases](https://github.com/utact/roadock/releases). Requires Android 8.0+.

## Setup

1. Append the [agent template](templates/roadock-agent.md) to your project's `AGENTS.md`.
2. Maintain `TODO.md` under `## Human` and `## Agent`, with stable Human task IDs.
3. Commit and push the file to GitHub.
4. Register its repository, branch, and path in the app.
5. For Human completion sync, add a token with **Contents: read and write** under **Edit project**.

Write access is required even for public repositories. Tokens are encrypted on the device.

Human completion changes require a connection and create commits on the configured branch. Pull them before PC edits.

## Build

Requires JDK 17 and Android SDK 36. Use `.\gradlew.bat` on Windows.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Signed releases use `.local/signing.properties`: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Run `assembleRelease`; `storeFile` is relative to the project root.

[MIT License](LICENSE) · [Checklist](TODO.md) · [Agent Instructions](AGENTS.md) · [Commit Convention](docs/commit-convention.md)
