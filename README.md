![ROADOCK](assets/roadock-banner.webp)

**Your agent's next task. Your next move.**

See what your agent has lined up and what needs your input first, even away from your desk.
Roadock brings GitHub Markdown checklists to your Android home screen, with Human and Agent tasks side by side.

## Features

- Human completion synced to GitHub; read-only Agent tasks
- Waiting labels name the Human tasks an Agent task needs first
- Task details show indented notes and links from the checklist
- Full-width project lists with All / Human / Agent filters in the app
- Single-column or split widgets with independent scrolling and offline access
- Separate personal tasks with drag sorting and swipe deletion
- Adjustable widget text size and background transparency

## Install

Download the APK from [Releases](https://github.com/utact/roadock/releases). Requires Android 8.0+.

## Setup

1. Ask your coding agent to apply the [template](templates/roadock-agent.md).
2. Have it prepare `TODO.md` with Human / Agent tasks.
3. Commit and push `TODO.md` to GitHub.
4. Register its repository, branch, and path in the app.
5. For Human completion sync, add a token with **Contents: read and write** under **Edit project**.

Merge the full marked block into `AGENTS.md`.
Keep existing project rules and tasks when applying or updating it.
Template revisions increment with each template change, independently of app releases.

Use `Requires: task-id` below an Agent task to reference a Human task's stable ID.
Roadock shows a waiting label until that Human task is complete. See the [template](templates/roadock-agent.md) for an example.

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
