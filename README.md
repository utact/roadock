![ROADOCK](assets/roadock-banner.webp)

An Android app and home screen widget for developers working with AI coding agents.

Follow project progress and tasks that need your input using Markdown checklists synced from GitHub.

## Features

- Human completion synced to GitHub; read-only Agent tasks
- Multiple projects with single-column or split views
- Independently scrolling lists with cached offline access
- Separate personal tasks with drag sorting and swipe deletion
- Adjustable widget text size and background transparency

## Install

Download the APK from [Releases](https://github.com/utact/roadock/releases). Requires Android 8.0+.

## Setup

1. Append the [agent template](templates/roadock-agent.md) to your project's `AGENTS.md`.
2. Maintain `TODO.md` under `## Human` and `## Agent`, with stable Human task IDs.
3. Commit and push the file to GitHub.
4. Register its repository, branch, and path in the app.
5. For Human completion sync, add a token with **Contents: read and write** under **Edit project**.

Write access is required even for public repositories. Tokens are encrypted on the device.

Human updates create GitHub commits. Pull before PC edits.

## Build

Requires JDK 17 and Android SDK 36. Use `.\gradlew.bat` on Windows.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Release: `assembleRelease` with `.local/signing.properties`:

```properties
storeFile=<keystore path from project root>
storePassword=...
keyAlias=...
keyPassword=...
```

[MIT License](LICENSE) · [Checklist](TODO.md) · [Agent Instructions](AGENTS.md) · [Commit Convention](docs/commit-convention.md)
