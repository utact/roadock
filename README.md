# ROADOCK

An Android home screen widget for project checklists maintained by AI coding agents.

## MVP

- Configure a GitHub repository, branch, and Markdown file path
- Display Markdown checkboxes (`- [ ]`, `- [x]`) as read-only items
- Switch projects with left and right widget buttons
- Refresh remote checklists and cache the last fetched content
- Add, complete, and delete personal tasks stored locally and shown in the widget

## Sync and Authentication

1. An agent updates `TODO.md`, then commits and pushes it to GitHub.
2. The app fetches the configured repository, branch, and file when refreshed.
3. The widget displays the fetched checklist.

Private repository credentials are stored securely on the device and excluded from source code and the APK.

## Development and Distribution

- Platform: Android / Kotlin
- Distribution: APK via GitHub Releases
- License: [MIT](LICENSE)

[Checklist](TODO.md) · [Agent Instructions](AGENTS.md) · [Commit Convention](docs/commit-convention.md)
