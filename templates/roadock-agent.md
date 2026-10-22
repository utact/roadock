## ROADOCK

- Maintain `TODO.md` under `## Human` and `## Agent`; subheadings inherit the owner.
- Human: user prerequisites, credentials, approvals, setup, and device tests. Never mark them complete without user confirmation.
- Agent: implementation and verification. Unscoped tasks are treated as Agent.
- Write one Korean action per task, at most 24 characters including spaces, excluding metadata.
- Give each Human task a unique, stable `<!-- roadock:task-id -->` suffix; preserve it when editing or moving the task.
- Read before work; update affected tasks and next steps with related changes.
- Refresh remote changes before editing or pushing; preserve mobile Human completions when resolving conflicts.
- Record dependencies in a short note below the task; never include secrets.
- Commit checklist updates with related changes; push only with explicit approval.

```markdown
## Human
- [ ] API 키 발급 <!-- roadock:api-key -->

## Agent
- [ ] 인증 연동 구현
  Requires: api-key
```
