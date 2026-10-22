# ROADOCK

An Android home screen widget for project checklists maintained by AI coding agents.

## MVP

- Configure a GitHub repository, branch, and Markdown file path
- Group Markdown checkboxes (`- [ ]`, `- [x]`) by Human and Agent
- Check Human tasks in the app and save them to GitHub; keep Agent tasks read-only
- Switch projects with left and right widget buttons; scroll long lists vertically
- Adjust widget text size and background transparency with a live preview
- Refresh remote checklists and cache the last fetched content
- Add, complete, reorder, and delete local personal tasks (up to 24 characters each)

## Sync and Authentication

1. An agent updates `TODO.md`, then commits and pushes it to GitHub with approval.
2. The app fetches the configured repository, branch, and file when refreshed.
3. The widget displays the fetched checklist.

- Use `## Human` and `## Agent`; nested headings inherit the owner. Unscoped tasks remain read-only Agent tasks.
- Human tasks appear first. Widgets display both groups; editing is available in the app.
- Give Human tasks stable IDs as shown in the [agent instruction template](templates/roadock-agent.md). Unique text also works; ambiguous or changed targets require a refresh.
- Checking or unchecking a Human task requires a token with repository **Contents: read and write**, including public repositories. Enter it in project settings; credentials are encrypted on the device.
- Each save creates a commit on the configured branch: `chore(todo): 사용자 작업 완료 상태 갱신`. Branch rules still apply.
- Saves fetch the latest file and change only the target checkbox, with up to three attempts using the current file SHA. Renamed, deleted, reassigned, or duplicate targets stop the save.
- Completion is confirmed after a successful save. Failures preserve the cache; refresh after an uncertain network result. Offline edits are not queued.
- Pull mobile commits before PC edits or pushes; preserve Human completions during merges.

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
