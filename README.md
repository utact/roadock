# ROADOCK

An Android home screen widget for project checklists maintained by AI coding agents.

## MVP

- Configure a GitHub repository, branch, and Markdown file path
- Display Markdown checkboxes (`- [ ]`, `- [x]`) as read-only items
- Switch projects with left and right widget buttons; scroll long lists vertically
- Refresh remote checklists and cache the last fetched content
- Add, complete, reorder, and delete local personal tasks (up to 24 characters each)

## Sync and Authentication

1. An agent updates `TODO.md`, then commits and pushes it to GitHub.
2. The app fetches the configured repository, branch, and file when refreshed.
3. The widget displays the fetched checklist.

Private repository credentials are stored securely on the device and excluded from source code and the APK.

Append the [agent instruction template](templates/roadock-agent.md) to the target repository's `AGENTS.md`. Replace `TODO.md` with the configured path if needed.

## Development and Distribution

- Platform: Android / Kotlin
- Distribution: APK via GitHub Releases
- License: [MIT](LICENSE)

## Build

Requires JDK 17 and Android SDK 36. Use `.\gradlew.bat` on Windows.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

For signed release builds, provide `.local/signing.properties` with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`, then run `assembleRelease`. The keystore path is relative to the project root; signing files are ignored by Git.

[Checklist](TODO.md) · [Agent Instructions](AGENTS.md) · [Commit Convention](docs/commit-convention.md)
