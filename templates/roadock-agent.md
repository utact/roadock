## ROADOCK

- Maintain `TODO.md` under `## Human` and `## Agent`; subheadings inherit the owner.
- Assign ownership by who must act, not importance.
- Human: only actions the agent cannot perform that require user intervention for current or next work.
  Examples: issuing credentials, account setup, granting access, physical-device tests, and decisions that block progress.
- Do not create Human tasks for abstract policy reviews, generic approvals, routine design choices, or speculative future decisions.
- Agent: implementation, investigation, design, technical decisions, and verification.
  Choose reasonable defaults within the user's instructions.
  Unscoped tasks are treated as Agent.
- Focus on actionable current and next steps, with immediately actionable pending tasks first.
  Avoid speculative or duplicate tasks; retain all necessary work even when the list is long.
- Write one self-contained Korean action per task, preferably within 24 characters including spaces, excluding metadata.
  Prioritize clarity over the length target.
- Give each Human task a unique, stable `<!-- roadock:task-id -->` suffix; preserve it when editing or moving the task.
- Add `Requires: task-id` below an Agent task only when an incomplete Human task blocks that action.
  Separate multiple Human IDs with commas.
  Continue independent Agent work.
- Read `TODO.md` before work; update affected tasks and next steps with related changes.
- Never mark Human tasks complete without user confirmation.
- Refresh remote changes before editing or pushing; preserve mobile Human completions when resolving conflicts.
- Record details and dependencies below the task; never include secrets.
- Roadock displays task text, Human/Agent labels, and waiting labels for `Requires`.
  Headings and other notes are hidden; dependencies are informational, not enforced.
- Commit checklist updates with related changes; push only with explicit approval.

```markdown
## Human
- [ ] API 키 발급 <!-- roadock:api-key -->

## Agent
- [ ] 인증 방식 비교·설계
- [ ] 발급 키로 API 호출 검증
  Requires: api-key
```
